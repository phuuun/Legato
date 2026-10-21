package com.phuuun.legato.library

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.zip.ZipInputStream

private const val MODEL = "vosk-model-small-en-us-0.15"
private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL.zip"
// The model feeds native code, so only the exact file Legato was tried with is unpacked.
private const val MODEL_SHA256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
const val MODEL_DOWNLOAD_MB = 41

/** How long after a pause is listened to: "Chapter one hundred and twenty" fits. */
private const val LISTEN_MS = 4_000

/**
 * Chapterfy for the Chapters sheet (see SpokenChapters): one book at a time, in the app's own scope so closing the sheet
 * doesn't stop it. Also fetches and deletes the speech model it needs, Vosk's small English model.
 */
class Chapterfy(context: Context, private val edits: EditStore) {
    sealed interface State {
        data object Idle : State
        data class Downloading(val percent: Int) : State
        data class Listening(val path: String, val doneMs: Long, val totalMs: Long, val found: Int) : State
        data class Done(val path: String, val chapters: List<Chapter>) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    // ponytail: dies with the app; a foreground service if long books get killed mid-listen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val dir = File(context.filesDir, "speech")
    private val model = File(dir, MODEL)

    val hasModel: Boolean get() = File(model, "am/final.mdl").exists()

    val modelMb: Long get() = model.walkBottomUp().sumOf { it.length() } / 1_000_000

    fun download() = run("Couldn't download the speech model. Check the internet and try again.") { fetchModel() }

    fun start(path: String) = run("Couldn't listen to this file.") { listen(path) }

    fun deleteModel() { job?.cancel(); dir.deleteRecursively(); _state.value = State.Idle }

    fun cancel() { job?.cancel(); _state.value = State.Idle }

    fun dismiss() { _state.value = State.Idle }

    suspend fun use(done: State.Done) {
        edits.save(done.path, mapOf(CHAPTERS to done.chapters.asText()))
        dismiss()
    }

    private fun run(failure: String, block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.value = State.Failed(failure) }
        }
    }

    private suspend fun fetchModel() = withContext(Dispatchers.IO) {
        _state.value = State.Downloading(0)
        dir.deleteRecursively()
        dir.mkdirs()
        try {
            val zip = File(dir, "model.zip")
            val sha = MessageDigest.getInstance("SHA-256")
            val c = URI(MODEL_URL).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                zip.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sha.update(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = State.Downloading((done * 100 / total).toInt())
                    }
                }
            }
            if (sha.digest().joinToString("") { "%02x".format(it) } != MODEL_SHA256) error("Checksum mismatch")
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                generateSequence { z.nextEntry }.forEach { e ->
                    val f = File(dir, e.name)
                    require(f.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { "Bad path in zip" }
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                }
            }
            zip.delete()
            _state.value = State.Idle
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    /**
     * Splits the file into [parts] stretches that are listened to at once, each overlapping the next by 10 s so a heading
     * at a seam is heard by one of them; [chapterList] drops the repeat.
     */
    private suspend fun listen(path: String) = withContext(Dispatchers.Default) {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val totalMs = MediaExtractor().run {
            try { setDataSource(path); (0 until trackCount).map { getTrackFormat(it) }.firstOrNull { it.isAudio }?.getLong(MediaFormat.KEY_DURATION)?.div(1000) ?: error("No audio") }
            finally { release() }
        }
        // Read once and shared: where every audio sample is, so the stretches skip MediaExtractor (null: they use it).
        val table = mp4AudioSamples(path)
        val parts = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        val done = AtomicLongArray(parts)
        val found = AtomicInteger()
        val recognizing = AtomicLong()
        val started = System.nanoTime()
        _state.value = State.Listening(path, 0, totalMs, 0)
        // The model closes only once every stretch has stopped, Stop included: they share it.
        val heard = Model(model.path).use { vosk ->
            coroutineScope {
                (0 until parts).map { i ->
                    val from = totalMs * i / parts
                    val to = if (i == parts - 1) totalMs else totalMs * (i + 1) / parts + 10_000
                    async {
                        listenPart(path, table, vosk, from, to, recognizing) { ms, heardOne ->
                            // After Stop, a last update mustn't bring the dialog back.
                            if (!isActive) return@listenPart
                            done.set(i, ms - from)
                            if (heardOne) found.incrementAndGet()
                            _state.value = State.Listening(path, (0 until parts).sumOf { done.get(it) }.coerceAtMost(totalMs), totalMs, found.get())
                        }
                    }
                }.awaitAll().flatten()
            }
        }
        Log.i("Chapterfy", "${if (table != null) "own reader" else "MediaExtractor"}: ${totalMs / 60_000} min of audio in ${(System.nanoTime() - started) / 1_000_000_000} s on $parts threads, ${recognizing.get() / 1_000_000_000} s of it recognizing")
        _state.value = State.Done(path, chapterList(heard))
    }

    /**
     * Listens to [fromMs, toMs) of the file and returns the headings heard. [progress] gets the position reached, and
     * whether a heading was just heard.
     *
     * With a [table], samples are read straight from the file and most of the book is only scanned: 2 of every [STEP]
     * samples are decoded to see where it goes quiet. Handing samples to the decoder is what costs (it lives in another
     * process; on a 10-hour book it kept six cores busy), so this is about three times faster. Wherever the scan sees
     * [SCAN_PAUSE_MS] of quiet, that stretch is decoded whole and goes through the same [Ear] as everything else, so what
     * counts as a chapter is decided exactly as without the scan. Without a table (MP3, fragmented MP4) all of it is
     * decoded, through MediaExtractor.
     */
    private suspend fun listenPart(path: String, table: SampleTable?, vosk: Model, fromMs: Long, toMs: Long, recognizing: AtomicLong, progress: (Long, Boolean) -> Unit): List<Chapter> = coroutineScope {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var channel: FileChannel? = null
        var ear: Ear? = null
        val began = System.nanoTime()
        var decoded = 0
        var reachedMs = fromMs
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).isAudio }
            extractor.selectTrack(track)
            extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val format = extractor.getTrackFormat(track)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also { codec = it }
            c.configure(format, null, null, 0)
            c.start()
            val e = Ear(vosk, format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), recognizing).also { ear = it }

            // Own reader: which sample to feed next, and what's decoded whole ([full], else scanning).
            val reader = table?.let { SampleReader(it, FileInputStream(path).channel.also { ch -> channel = ch }) }
            val first = table?.indexAt(fromMs * 1000) ?: 0
            var next = first
            var full: IntRange? = null
            var quietFrom = -1
            val fed = HashMap<Long, Int>() // sample time → index, for the scan to know what came out
            fun onGrid(i: Int) = (i - first) % STEP
            fun restart(at: Int) { c.flush(); fed.clear(); next = at; e.reset() }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var shown = 0L
            var pcm = ShortArray(0)

            while (true) {
                ensureActive()
                while (!inputDone) {
                    if (reader == null) {
                        val i = c.dequeueInputBuffer(0)
                        if (i < 0) break
                        val n = extractor.readSampleData(c.getInputBuffer(i)!!, 0)
                        if (n < 0 || extractor.sampleTime > toMs * 1000) { c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                        continue
                    }
                    // A stretch decoded whole keeps being fed past its end: the decoder holds a sample back until the
                    // next one arrives, so waiting for the last one to come out would wait forever.
                    val f = full
                    val end = next >= table.sizes.size || (f == null && table.startUs(next) > toMs * 1000)
                    val i = c.dequeueInputBuffer(0)
                    if (i < 0) break
                    if (end) { c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; continue }
                    val size = reader.read(next, c.getInputBuffer(i)!!)
                    fed[table.startUs(next)] = next
                    c.queueInputBuffer(i, 0, size, table.startUs(next), 0)
                    // Scanning: the pair at the start of each step, then on to the next step.
                    next = if (f == null && onGrid(next) == 1) next + STEP - 1 else next + 1
                }
                val o = c.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    e.format(c.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    continue
                }
                if (o < 0) continue
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val bytes = c.getOutputBuffer(o)!!.apply { position(info.offset); limit(info.offset + info.size) }
                val shorts = bytes.order(ByteOrder.nativeOrder()).asShortBuffer()
                val n = shorts.remaining()
                if (pcm.size < n) pcm = ShortArray(n)
                shorts.get(pcm, 0, n)
                c.releaseOutputBuffer(o, false)
                if (n > 0) decoded++
                reachedMs = info.presentationTimeUs / 1000
                val index = fed.remove(info.presentationTimeUs)

                var heardOne = false
                val f = full
                if (reader == null || (f != null && index != null && index in f)) heardOne = e.hear(pcm, n, info.presentationTimeUs)
                if (f != null && index != null) {
                    if (index >= f.last) {
                        // Never end in a pause or a heading being listened to: the scan would start counting that pause
                        // from nothing and miss a chapter after it. Another second, then look again.
                        if (e.listening || e.inPause) full = f.first..minOf(f.last + STEP * 6, table!!.sizes.size - 1)
                        else { full = null; quietFrom = -1; restart(first + ((f.last + 1 - first + STEP - 1) / STEP) * STEP) }
                    }
                } else if (f == null && index != null && n > 0 && onGrid(index) == 1) {
                    if (e.quiet(pcm, n)) { if (quietFrom < 0) quietFrom = index - 1 }
                    else {
                        // From the step before the quiet began, so the whole pause is decoded, to 5 s past it.
                        if (quietFrom >= 0 && table!!.startUs(index) - table.startUs(quietFrom) >= SCAN_PAUSE_MS * 1000) {
                            val start = maxOf(first, quietFrom - STEP)
                            full = start..minOf(table.indexAt(table.startUs(index) + 5_000_000), table.sizes.size - 1)
                            restart(start)
                        }
                        quietFrom = -1
                    }
                }

                val now = System.currentTimeMillis()
                if (heardOne || now - shown > 500) { shown = now; progress(reachedMs, heardOne) }
                if (eos) break
            }
            e.heard
        } finally {
            Log.i("Chapterfy", "stretch from ${fromMs / 60_000} min (${if (table != null) "scan" else "MediaExtractor"}): ${(System.nanoTime() - began) / 1_000_000} ms, $decoded samples decoded")
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
            channel?.close()
            ear?.close()
        }
    }
}

/** Scan step in samples: of every 8 (about 190 ms), 2 are decoded; the first only primes the decoder. */
private const val STEP = 8

/**
 * Quiet the scan calls a possible pause: under the 1.5 s the [Ear] asks for by more than a step (190 ms), so stepping
 * never hides one.
 */
private const val SCAN_PAUSE_MS = 1_250

/**
 * How much louder than the [Ear]'s silence a scanned sample may be and still count as quiet. A 23 ms sample overlaps at
 * most two of the Ear's 50 ms frames; if both are silent, its mean power is at most 100/23 of theirs, 6.4 dB. So the scan
 * never calls loud what the Ear would call silent, and can't hide a pause; it only ever adds stretches to check.
 */
private const val SCAN_MARGIN_DB = 7f

/** Samples straight from the file, read in 1 MB blocks: one small read each is a trip through shared storage's file layer. */
private class SampleReader(private val table: SampleTable, private val channel: FileChannel) {
    private val block = ByteBuffer.allocate(1 shl 20).apply { limit(0) }
    private var blockAt = 0L

    /** Copies sample [i] into [into] and returns its size. */
    fun read(i: Int, into: ByteBuffer): Int {
        val at = table.offsets[i]
        val size = table.sizes[i]
        if (at < blockAt || at + size > blockAt + block.limit()) {
            block.clear()
            while (block.hasRemaining() && channel.read(block, at + block.position()) > 0) Unit
            block.flip()
            blockAt = at
            if (block.limit() < size) error("File ended early")
        }
        val from = (at - blockAt).toInt()
        into.clear()
        into.put(block.duplicate().apply { position(from); limit(from + size) })
        return size
    }
}

/**
 * Decoded audio in, headings out: loudness every 50 ms, and after each pause of 1.5 s or more (see [PauseFinder]) the next
 * [LISTEN_MS] go to the recognizer. [reset] after a jump: it counts time from the first buffer it gets.
 */
private class Ear(private val vosk: Model, private var rate: Int, private var channels: Int, private val recognizing: AtomicLong) {
    val heard = mutableListOf<Chapter>()
    private var recognizer: Recognizer? = null
    private var pauses = PauseFinder()
    private var frame = ShortArray(rate / 20)
    private var inFrame = 0
    private var sum = 0.0
    private var samples = -1L
    private var window: ShortArray? = null
    private var inWindow = 0
    private var windowStartMs = 0L

    /** Whether a heading is being listened to: the stretch decoded whole mustn't end in the middle of it. */
    val listening: Boolean get() = window != null

    /** Whether the last 50 ms were silent: nor in the middle of a pause. */
    var inPause = false
        private set

    fun format(rate: Int, channels: Int) {
        // HE-AAC decodes at twice the rate the container states.
        if (rate != this.rate) { recognizer?.close(); recognizer = null }
        this.rate = rate
        this.channels = channels
        reset()
    }

    fun reset() {
        pauses = PauseFinder()
        frame = ShortArray(rate / 20)
        inFrame = 0
        sum = 0.0
        samples = -1
        window = null
        inPause = false
    }

    /** Whether a scanned buffer could be silence: mixed to mono like [hear], against [SCAN_MARGIN_DB] over the silence level. */
    fun quiet(pcm: ShortArray, n: Int): Boolean {
        var sum = 0.0
        var count = 0
        var k = 0
        while (k + channels <= n) {
            var s = 0
            for (ch in 0 until channels) s += pcm[k + ch]
            k += channels
            val mono = s / channels
            sum += mono * mono.toDouble()
            count++
        }
        return loudnessDb(sum, count) < PauseFinder.SILENT_DB + SCAN_MARGIN_DB
    }

    /** Takes one decoded buffer starting at [ptsUs]; true when it completed a heading. */
    fun hear(pcm: ShortArray, n: Int, ptsUs: Long): Boolean {
        val rec = recognizer ?: Recognizer(vosk, rate.toFloat(), JSONArray(ChapterWords).toString()).also { recognizer = it }
        if (samples < 0) samples = ptsUs * rate / 1_000_000
        var heardOne = false
        var k = 0
        while (k + channels <= n) {
            var s = 0
            for (ch in 0 until channels) s += pcm[k + ch]
            k += channels
            val mono = (s / channels).toShort()
            samples++
            window?.let { w ->
                w[inWindow++] = mono
                if (inWindow == w.size) {
                    val t = System.nanoTime()
                    rec.acceptWaveForm(w, w.size)
                    val title = spokenChapter(JSONObject(rec.finalResult).optString("text"))
                    rec.reset()
                    recognizing.addAndGet(System.nanoTime() - t)
                    if (title != null) { heard += Chapter(title, (windowStartMs - 300).coerceAtLeast(0)); heardOne = true }
                    window = null
                }
            }
            frame[inFrame++] = mono
            sum += mono * mono.toDouble()
            if (inFrame == frame.size) {
                // The frame where speech starts opens the window, so the first syllable isn't lost.
                val db = loudnessDb(sum, inFrame)
                inPause = db < PauseFinder.SILENT_DB
                if (pauses.speechStarts(db) && window == null) {
                    window = ShortArray(rate * LISTEN_MS / 1000).also { frame.copyInto(it) }
                    inWindow = frame.size
                    windowStartMs = (samples - frame.size) * 1000 / rate
                }
                inFrame = 0
                sum = 0.0
            }
        }
        return heardOne
    }

    fun close() { recognizer?.close() }
}

private val MediaFormat.isAudio get() = getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
