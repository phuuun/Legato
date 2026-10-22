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
 * The chapters of one file, best source first: a list saved in Legato ([saved], see EditStore), then the file's own chapter marks (MP4/M4B, MP3, or Vorbis-style
 * CHAPTERnnn tags in FLAC/Ogg/Opus), then a timestamp list in its comment (what the converter writes, and what
 * YouTube descriptions look like), and for a long file with neither, 30-minute parts so it can still be skipped through.
 */
suspend fun readChapters(path: String, durationMs: Long, saved: String? = null): List<Chapter> = withContext(Dispatchers.IO) {
    if (saved != null) return@withContext chaptersFromText(saved)
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

/** Chapters as the timestamp lines Legato saves them in ("1:02:03 Title"), in order. */
fun List<Chapter>.asText(): String = sortedBy { it.startMs }.joinToString("\n") { "${formatTimestamp(it.startMs)} ${it.title}" }

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

/** The moov box, found among the top-level boxes without reading the audio; null when the file has none. */
private fun readMoov(f: RandomAccessFile): ByteBuffer? {
    var at = 0L
    while (at + 8 <= f.length()) {
        f.seek(at)
        var size = f.readInt().toLong() and 0xffffffffL
        val type = ByteArray(4).also { f.readFully(it) }.toString(Charsets.ISO_8859_1)
        if (size == 1L) size = f.readLong() else if (size == 0L) size = f.length() - at
        if (size < 8) break
        if (type == "moov") return ByteBuffer.wrap(ByteArray(size.toInt()).also { f.seek(at); f.readFully(it) })
        at += size
    }
    return null
}

internal fun mp4Chapters(f: RandomAccessFile): List<Chapter> {
    val b = readMoov(f) ?: return emptyList()
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

/** Where each sample of one track sits in the file, how big it is, and when it starts (in [timescale] units). */
class SampleTable(val offsets: LongArray, val sizes: IntArray, val starts: LongArray, val timescale: Long) {
    fun startUs(i: Int) = starts[i] * 1_000_000 / timescale

    /** The first sample starting at or after [us]; the count when there's none. */
    fun indexAt(us: Long): Int {
        val i = starts.binarySearch((us * timescale + 999_999) / 1_000_000) // rounded up: at or after
        return if (i >= 0) i else -i - 1
    }
}

/**
 * The audio track's samples of an MP4/M4A, for reading them without MediaExtractor (see Chapterfy): from moov's tables, or
 * from the fragments of a fragmented file (YouTube's). Null for anything that isn't an MP4.
 */
fun mp4AudioSamples(file: String): SampleTable? = runCatching {
    RandomAccessFile(File(file), "r").use { f ->
        val b = readMoov(f) ?: return@use null
        val root = boxes(b, 0, b.capacity()).find("moov") ?: return@use null
        val trak = boxes(b, root.body, root.end).filter { it.type == "trak" }.firstOrNull { t ->
            path(b, t, "mdia", "hdlr")?.let { h -> String(ByteArray(4) { b.get(h.body + 8 + it) }, Charsets.ISO_8859_1) } == "soun"
        } ?: return@use null
        sampleTable(b, trak)?.takeIf { it.sizes.isNotEmpty() } ?: fragmentSamples(f, b, root, trak)?.takeIf { it.sizes.isNotEmpty() }
    }
}.getOrNull()

/**
 * The samples of [trak] listed in a fragmented file's moof boxes: per traf, tfhd (track and defaults), tfdt (start time)
 * and trun (each sample's duration and size, with an offset from the moof). Defaults missing from tfhd come from moov's trex.
 */
private fun fragmentSamples(f: RandomAccessFile, b: ByteBuffer, moov: Box, trak: Box): SampleTable? {
    val tkhd = path(b, trak, "tkhd") ?: return null
    val id = b.getInt(tkhd.body + if (b.get(tkhd.body).toInt() == 1) 20 else 12)
    val mdhd = path(b, trak, "mdia", "mdhd") ?: return null
    val timescale = b.getInt(mdhd.body + if (b.get(mdhd.body).toInt() == 1) 20 else 12).toLong().takeIf { it > 0 } ?: return null
    val trex = path(b, moov, "mvex")?.let { m -> boxes(b, m.body, m.end).firstOrNull { it.type == "trex" && b.getInt(it.body + 4) == id } }
    val trexDuration = trex?.let { b.getInt(it.body + 12) } ?: 0
    val trexSize = trex?.let { b.getInt(it.body + 16) } ?: 0

    val offsets = LongArrayList()
    val sizes = IntArrayList()
    val starts = LongArrayList()
    var t = 0L
    var at = 0L
    while (at + 8 <= f.length()) {
        f.seek(at)
        var size = f.readInt().toLong() and 0xffffffffL
        val type = ByteArray(4).also { f.readFully(it) }.toString(Charsets.ISO_8859_1)
        if (size == 1L) size = f.readLong() else if (size == 0L) size = f.length() - at
        if (size < 8) break
        if (type == "moof") {
            val m = ByteBuffer.wrap(ByteArray(size.toInt()).also { f.seek(at); f.readFully(it) })
            val moof = boxes(m, 0, m.capacity()).find("moof") ?: break
            for (traf in boxes(m, moof.body, moof.end).filter { it.type == "traf" }) {
                val inside = boxes(m, traf.body, traf.end)
                val tfhd = inside.find("tfhd") ?: continue
                if (m.getInt(tfhd.body + 4) != id) continue
                val hf = m.getInt(tfhd.body) and 0xffffff
                var h = tfhd.body + 8
                val base = if (hf and 0x1 != 0) m.getLong(h).also { h += 8 } else at // else the moof's own start
                if (hf and 0x2 != 0) h += 4
                val defDuration = if (hf and 0x8 != 0) m.getInt(h).also { h += 4 } else trexDuration
                val defSize = if (hf and 0x10 != 0) m.getInt(h) else trexSize
                inside.find("tfdt")?.let { d -> t = if (m.get(d.body).toInt() == 1) m.getLong(d.body + 4) else m.getInt(d.body + 4).toLong() and 0xffffffffL }
                for (trun in inside.filter { it.type == "trun" }) {
                    val rf = m.getInt(trun.body) and 0xffffff
                    val count = m.getInt(trun.body + 4)
                    var r = trun.body + 8
                    var o = base + if (rf and 0x1 != 0) m.getInt(r).also { r += 4 } else 0
                    if (rf and 0x4 != 0) r += 4
                    repeat(count) {
                        val duration = if (rf and 0x100 != 0) m.getInt(r).also { r += 4 } else defDuration
                        val s = if (rf and 0x200 != 0) m.getInt(r).also { r += 4 } else defSize
                        if (rf and 0x400 != 0) r += 4
                        if (rf and 0x800 != 0) r += 4
                        offsets += o; sizes += s; starts += t
                        o += s; t += duration.toLong() and 0xffffffffL
                    }
                }
            }
        }
        at += size
    }
    return SampleTable(offsets.toArray(), sizes.toArray(), starts.toArray(), timescale)
}

// Growable primitive arrays: a 10 h book has ~1.7M samples, too many to box into a List<Long>.
private class LongArrayList {
    private var a = LongArray(1024); private var n = 0
    operator fun plusAssign(v: Long) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    fun toArray() = a.copyOf(n)
}

private class IntArrayList {
    private var a = IntArray(1024); private var n = 0
    operator fun plusAssign(v: Int) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    fun toArray() = a.copyOf(n)
}

private fun sampleTable(b: ByteBuffer, trak: Box): SampleTable? {
    val mdhd = path(b, trak, "mdia", "mdhd") ?: return null
    val timescale = b.getInt(mdhd.body + if (b.get(mdhd.body).toInt() == 1) 20 else 12).toLong().takeIf { it > 0 } ?: return null
    val stbl = path(b, trak, "mdia", "minf", "stbl") ?: return null
    val inside = boxes(b, stbl.body, stbl.end)

    // Sample sizes (stsz), then start times from the run-length durations in stts.
    val stsz = inside.find("stsz") ?: return null
    val fixed = b.getInt(stsz.body + 4)
    val sizes = IntArray(b.getInt(stsz.body + 8)) { if (fixed != 0) fixed else b.getInt(stsz.body + 12 + it * 4) }
    val stts = inside.find("stts") ?: return null
    val starts = LongArray(sizes.size)
    var t = 0L
    var s = 0
    for (e in 0 until b.getInt(stts.body + 4)) {
        val n = b.getInt(stts.body + 8 + e * 8)
        val delta = b.getInt(stts.body + 12 + e * 8).toLong() and 0xffffffffL
        repeat(n) { if (s < starts.size) starts[s++] = t; t += delta }
    }
    // File offset of each sample: chunk offsets (stco/co64), samples per chunk (stsc).
    val chunks = inside.find("stco")?.let { c -> LongArray(b.getInt(c.body + 4)) { (b.getInt(c.body + 8 + it * 4).toLong() and 0xffffffffL) } }
        ?: inside.find("co64")?.let { c -> LongArray(b.getInt(c.body + 4)) { b.getLong(c.body + 8 + it * 8) } } ?: return null
    val stsc = inside.find("stsc") ?: return null
    val runs = List(b.getInt(stsc.body + 4)) { b.getInt(stsc.body + 8 + it * 12) to b.getInt(stsc.body + 12 + it * 12) }
    val offsets = LongArray(sizes.size)
    var next = 0
    var run = 0
    for (i in chunks.indices) {
        while (run + 1 < runs.size && runs[run + 1].first <= i + 1) run++
        val perChunk = runs.getOrNull(run)?.takeIf { it.first <= i + 1 }?.second ?: continue
        var o = chunks[i]
        repeat(perChunk) { if (next < sizes.size) { offsets[next] = o; o += sizes[next]; next++ } }
    }
    return SampleTable(offsets, sizes, starts, timescale)
}

/** The text track the audio track points at through tref/chap: one sample per chapter, timed by the track. */
private fun appleChapters(f: RandomAccessFile, b: ByteBuffer, moov: Box): List<Chapter> {
    val traks = boxes(b, moov.body, moov.end).filter { it.type == "trak" }
    fun trackId(trak: Box): Int? = path(b, trak, "tkhd")?.let { t -> b.getInt(t.body + if (b.get(t.body).toInt() == 1) 20 else 12) }
    val chapterIds = traks.mapNotNull { path(b, it, "tref", "chap") }.flatMap { c -> (c.body until c.end step 4).map { b.getInt(it) } }.toSet()
    val trak = traks.firstOrNull { trackId(it) in chapterIds } ?: return emptyList()
    val table = sampleTable(b, trak) ?: return emptyList()
    // Each sample: a 16-bit length, then the title (UTF-8, or UTF-16 with a byte-order mark).
    return table.sizes.indices.map { i ->
        f.seek(table.offsets[i])
        val len = f.readUnsignedShort()
        val raw = ByteArray(len).also { f.readFully(it) }
        val title = if (len >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte()) String(raw, Charsets.UTF_16) else String(raw, Charsets.UTF_8)
        Chapter(title, table.starts[i] * 1000 / table.timescale)
    }.named()
}
