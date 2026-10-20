package com.phuuun.legato.library

import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** A named spot inside one audio file. */
data class Chapter(val title: String, val startMs: Long)

/**
 * The chapters inside one file, best source first: the file's own chapter marks (MP4/M4B, MP3, or Vorbis-style
 * CHAPTERnnn tags in FLAC/Ogg/Opus), then a timestamp list in its comment (what the converter writes, and what
 * YouTube descriptions look like), and for a long file with neither, 30-minute parts so it can still be skipped through.
 */
suspend fun readChapters(path: String, durationMs: Long): List<Chapter> = withContext(Dispatchers.IO) {
    val file = File(path)
    val embedded = runCatching {
        RandomAccessFile(file, "r").use { f ->
            val head = ByteArray(8).also { f.readFully(it) }
            when {
                String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4Chapters(f)
                String(head, 0, 3, Charsets.ISO_8859_1) == "ID3" -> id3Chapters(f)
                else -> emptyList()
            }
        }
    }.getOrDefault(emptyList())
    if (embedded.size >= 2) return@withContext embedded
    val tags = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { TagLib.getMetadata(it.dup().detachFd(), false)?.propertyMap }
    }.getOrNull().orEmpty()
    chaptersFromTags(tags).takeIf { it.size >= 2 }
        ?: listOfNotNull(tags["COMMENT"], tags["DESCRIPTION"]).flatMap { it.asList() }.firstNotNullOfOrNull { text -> chaptersFromText(text).takeIf { it.size >= 2 } }
        ?: autoParts(durationMs)
}

/** Every 30 minutes, for a file over 45 minutes with no chapters of its own. */
fun autoParts(durationMs: Long, everyMs: Long = 30 * 60_000L): List<Chapter> =
    if (durationMs <= 45 * 60_000L) emptyList() else (0 until durationMs step everyMs).mapIndexed { i, ms -> Chapter("Part ${i + 1}", ms) }

/** Vorbis-style chapters, as FLAC, Ogg and Opus files carry them: CHAPTER001=00:01:02.500 and CHAPTER001NAME=Title. */
fun chaptersFromTags(tags: Map<String, Array<out String>>): List<Chapter> {
    val key = Regex("CHAPTER(\\d+)")
    return tags.mapNotNull { (k, v) ->
        val n = key.matchEntire(k.uppercase())?.groupValues?.get(1) ?: return@mapNotNull null
        val ms = parseTimestamp(v.firstOrNull() ?: return@mapNotNull null) ?: return@mapNotNull null
        Chapter(tags.entries.firstOrNull { it.key.uppercase() == "CHAPTER${n}NAME" }?.value?.firstOrNull().orEmpty(), ms)
    }.named()
}

private val timestampAt = Regex("(?<![\\d:])(\\d{1,2}:)?\\d{1,2}:\\d{2}(\\.\\d{1,3})?(?![\\d:])")

/**
 * A timestamp list, one per line, the way YouTube descriptions write chapters: "0:00 Intro", "1:02:03 - Part two",
 * "(12:30) The end". Lines out of order are dropped, so a stray time mentioned in passing can't scramble the list.
 */
fun chaptersFromText(text: String): List<Chapter> {
    val out = mutableListOf<Chapter>()
    for (line in text.lines()) {
        val m = timestampAt.find(line) ?: continue
        val ms = parseTimestamp(m.value) ?: continue
        if (out.isNotEmpty() && ms <= out.last().startMs) continue
        val title = line.removeRange(m.range).trim().trim('-', '–', '—', ':', '|', '.', '(', ')', '[', ']', ' ', '\t')
        out += Chapter(title, ms)
    }
    return out.named()
}

/** "1:02:03", "02:03", "00:01:02.5" → milliseconds. */
fun parseTimestamp(s: String): Long? {
    val parts = s.trim().split(':')
    if (parts.size !in 2..3) return null
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val whole = parts.dropLast(1).map { it.toLongOrNull() ?: return null }.fold(0L) { acc, n -> acc * 60 + n }
    return whole * 60_000 + (seconds * 1000).toLong()
}

/** "1:02:03" for a chapter list the converter writes and [chaptersFromText] reads back. */
fun formatTimestamp(ms: Long): String = "%d:%02d:%02d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60)

private fun List<Chapter>.named() = sortedBy { it.startMs }.mapIndexed { i, c -> if (c.title.isBlank()) c.copy(title = "Chapter ${i + 1}") else c }

// ---- MP3: ID3v2 CHAP frames, decoded by Media3 ----

@OptIn(UnstableApi::class)
internal fun id3Chapters(f: RandomAccessFile): List<Chapter> {
    val header = ByteArray(10).also { f.seek(0); f.readFully(it) }
    // The tag size is "synchsafe": 7 bits per byte.
    val size = (0 until 4).fold(0) { acc, i -> (acc shl 7) or (header[6 + i].toInt() and 0x7f) }
    val tag = ByteArray(10 + size).also { f.seek(0); f.readFully(it) }
    val metadata = Id3Decoder().decode(tag, tag.size) ?: return emptyList()
    return (0 until metadata.length()).mapNotNull { metadata[it] as? ChapterFrame }.map { c ->
        val title = (0 until c.subFrameCount).map { c.getSubFrame(it) }.filterIsInstance<TextInformationFrame>().firstOrNull { it.id == "TIT2" }?.values?.firstOrNull()
        Chapter(title.orEmpty(), c.startTimeMs.toLong())
    }.named()
}

// ---- MP4 / M4B: Apple's chapter track, or Nero's chpl list ----

private class Box(val type: String, val body: Int, val end: Int)

/** The boxes directly inside [from, to) of [b]. */
private fun boxes(b: ByteBuffer, from: Int, to: Int): List<Box> {
    val out = mutableListOf<Box>()
    var at = from
    while (at + 8 <= to) {
        var size = b.getInt(at).toLong() and 0xffffffffL
        val type = String(ByteArray(4) { b.get(at + 4 + it) }, Charsets.ISO_8859_1)
        var body = at + 8
        if (size == 1L) { size = b.getLong(at + 8); body += 8 } else if (size == 0L) size = (to - at).toLong()
        if (size < 8 || at + size > to) break
        out += Box(type, body, (at + size).toInt())
        at += size.toInt()
    }
    return out
}

private fun List<Box>.find(type: String) = firstOrNull { it.type == type }

private fun path(b: ByteBuffer, root: Box, vararg types: String): Box? =
    types.fold(root as Box?) { box, t -> box?.let { boxes(b, it.body, it.end).find(t) } }

internal fun mp4Chapters(f: RandomAccessFile): List<Chapter> {
    // Find moov among the top-level boxes without reading the audio, then load just moov.
    var at = 0L
    var moov: ByteBuffer? = null
    while (at + 8 <= f.length()) {
        f.seek(at)
        var size = f.readInt().toLong() and 0xffffffffL
        val type = ByteArray(4).also { f.readFully(it) }.toString(Charsets.ISO_8859_1)
        if (size == 1L) size = f.readLong() else if (size == 0L) size = f.length() - at
        if (size < 8) break
        if (type == "moov") { moov = ByteBuffer.wrap(ByteArray(size.toInt()).also { f.seek(at); f.readFully(it) }); break }
        at += size
    }
    val b = moov ?: return emptyList()
    val root = boxes(b, 0, b.capacity()).find("moov") ?: return emptyList()
    return appleChapters(f, b, root).takeIf { it.size >= 2 } ?: neroChapters(b, root)
}

/** udta/chpl: a count, then per chapter a start in 100 ns units and a length-prefixed UTF-8 title. */
private fun neroChapters(b: ByteBuffer, moov: Box): List<Chapter> {
    val chpl = path(b, moov, "udta", "chpl") ?: return emptyList()
    var at = chpl.body + 4 + (if (b.get(chpl.body).toInt() != 0) 4 else 0)
    val count = b.get(at++).toInt() and 0xff
    return List(count) {
        val start = b.getLong(at)
        val len = b.get(at + 8).toInt() and 0xff
        val title = String(ByteArray(len) { i -> b.get(at + 9 + i) }, Charsets.UTF_8)
        at += 9 + len
        Chapter(title, start / 10_000)
    }.named()
}

/** The text track the audio track points at through tref/chap: one sample per chapter, timed by the track. */
private fun appleChapters(f: RandomAccessFile, b: ByteBuffer, moov: Box): List<Chapter> {
    val traks = boxes(b, moov.body, moov.end).filter { it.type == "trak" }
    fun trackId(trak: Box): Int? = path(b, trak, "tkhd")?.let { t -> b.getInt(t.body + if (b.get(t.body).toInt() == 1) 20 else 12) }
    val chapterIds = traks.mapNotNull { path(b, it, "tref", "chap") }.flatMap { c -> (c.body until c.end step 4).map { b.getInt(it) } }.toSet()
    val trak = traks.firstOrNull { trackId(it) in chapterIds } ?: return emptyList()
    val mdhd = path(b, trak, "mdia", "mdhd") ?: return emptyList()
    val timescale = b.getInt(mdhd.body + if (b.get(mdhd.body).toInt() == 1) 20 else 12).toLong().takeIf { it > 0 } ?: return emptyList()
    val stbl = path(b, trak, "mdia", "minf", "stbl") ?: return emptyList()
    val inside = boxes(b, stbl.body, stbl.end)

    // Start time of each sample, from the run-length durations in stts.
    val stts = inside.find("stts") ?: return emptyList()
    val starts = mutableListOf<Long>()
    var t = 0L
    for (e in 0 until b.getInt(stts.body + 4)) {
        val n = b.getInt(stts.body + 8 + e * 8)
        val delta = b.getInt(stts.body + 12 + e * 8).toLong() and 0xffffffffL
        repeat(n) { starts += t; t += delta }
    }
    // File offset of each sample: chunk offsets (stco/co64), samples per chunk (stsc), sample sizes (stsz).
    val stsz = inside.find("stsz") ?: return emptyList()
    val fixed = b.getInt(stsz.body + 4)
    val sizes = List(b.getInt(stsz.body + 8)) { if (fixed != 0) fixed else b.getInt(stsz.body + 12 + it * 4) }
    val chunks = inside.find("stco")?.let { c -> List(b.getInt(c.body + 4)) { (b.getInt(c.body + 8 + it * 4).toLong() and 0xffffffffL) } }
        ?: inside.find("co64")?.let { c -> List(b.getInt(c.body + 4)) { b.getLong(c.body + 8 + it * 8) } } ?: return emptyList()
    val stsc = inside.find("stsc") ?: return emptyList()
    val runs = List(b.getInt(stsc.body + 4)) { b.getInt(stsc.body + 8 + it * 12) to b.getInt(stsc.body + 12 + it * 12) }
    val offsets = mutableListOf<Long>()
    for ((i, chunkStart) in chunks.withIndex()) {
        val perChunk = runs.lastOrNull { it.first <= i + 1 }?.second ?: continue
        var o = chunkStart
        repeat(perChunk) { if (offsets.size < sizes.size) { offsets += o; o += sizes[offsets.size - 1] } }
    }
    // Each sample: a 16-bit length, then the title (UTF-8, or UTF-16 with a byte-order mark).
    return offsets.indices.mapNotNull { i ->
        val start = starts.getOrNull(i) ?: return@mapNotNull null
        f.seek(offsets[i])
        val len = f.readUnsignedShort()
        val raw = ByteArray(len).also { f.readFully(it) }
        val title = if (len >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte()) String(raw, Charsets.UTF_16) else String(raw, Charsets.UTF_8)
        Chapter(title, start * 1000 / timescale)
    }.named()
}
