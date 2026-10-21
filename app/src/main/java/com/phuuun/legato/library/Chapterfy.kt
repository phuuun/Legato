package com.phuuun.legato.library

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
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
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteOrder
import java.security.MessageDigest
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

    /** Decodes the whole file, measuring loudness every 50 ms; after each long pause the next [LISTEN_MS] go to the recognizer. */
    private suspend fun listen(path: String) = withContext(Dispatchers.Default) {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var recognizer: Recognizer? = null
        val vosk = Model(model.path)
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("No audio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val totalMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also { codec = it }
            c.configure(format, null, null, 0)
            c.start()
            _state.value = State.Listening(path, 0, totalMs, 0)

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val heard = mutableListOf<Chapter>()
            val pauses = PauseFinder()
            var frame = ShortArray(rate / 20)
            var inFrame = 0
            var sum = 0.0
            var samples = 0L
            var window: ShortArray? = null
            var inWindow = 0
            var windowStartMs = 0L
            var pcm = ShortArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var shown = 0L

            while (true) {
                ensureActive()
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(c.getInputBuffer(i)!!, 0)
                        if (n < 0) { c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // HE-AAC decodes at twice the rate the container states.
                    rate = c.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    frame = ShortArray(rate / 20)
                    inFrame = 0
                    sum = 0.0
                    continue
                }
                if (o < 0) continue
                val rec = recognizer ?: Recognizer(vosk, rate.toFloat(), JSONArray(ChapterWords).toString()).also { recognizer = it }
                val bytes = c.getOutputBuffer(o)!!.apply { position(info.offset); limit(info.offset + info.size) }
                val shorts = bytes.order(ByteOrder.nativeOrder()).asShortBuffer()
                val n = shorts.remaining()
                if (pcm.size < n) pcm = ShortArray(n)
                shorts.get(pcm, 0, n)
                c.releaseOutputBuffer(o, false)

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
                            rec.acceptWaveForm(w, w.size)
                            spokenChapter(JSONObject(rec.finalResult).optString("text"))?.let { heard += Chapter(it, (windowStartMs - 300).coerceAtLeast(0)) }
                            rec.reset()
                            window = null
                        }
                    }
                    frame[inFrame++] = mono
                    sum += mono * mono.toDouble()
                    if (inFrame == frame.size) {
                        // The frame where speech starts opens the window, so the first syllable isn't lost.
                        if (pauses.speechStarts(loudnessDb(sum, inFrame)) && window == null) {
                            window = ShortArray(rate * LISTEN_MS / 1000).also { frame.copyInto(it) }
                            inWindow = frame.size
                            windowStartMs = (samples - frame.size) * 1000 / rate
                        }
                        inFrame = 0
                        sum = 0.0
                    }
                }

                val now = System.currentTimeMillis()
                if (now - shown > 500) { shown = now; _state.value = State.Listening(path, info.presentationTimeUs / 1000, totalMs, heard.size) }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
            _state.value = State.Done(path, chapterList(heard))
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
            recognizer?.close()
            vosk.close()
        }
    }
}
