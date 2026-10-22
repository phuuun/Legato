package com.phuuun.legato.library

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import androidx.core.content.ContextCompat
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

private const val MODEL = "ggml-tiny.en-q5_1.bin"
private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$MODEL"
// The model feeds native code, so only the exact file Legato was tried with is kept.
private const val MODEL_SHA256 = "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b"
const val MODEL_DOWNLOAD_MB = 32

/**
 * Chapterfy for the Chapters sheet (see SpokenChapters), one book at a time. The listening itself runs in its own
 * process (see ChapterfyService), watched from here: if native code crashes on damaged audio there, the book is listened
 * to again, and the app never goes down with it. Also fetches and deletes the speech model it needs, Whisper's tiny
 * English model (as good as base at headings on a real book, and 2.5 times faster).
 */
class Chapterfy(private val context: Context, private val edits: EditStore) {
    sealed interface State {
        data object Idle : State
        data class Downloading(val percent: Int) : State
        data class Listening(val path: String, val doneMs: Long, val totalMs: Long, val found: Int) : State
        data class Done(val path: String, val chapters: List<Chapter>) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val dir = File(context.filesDir, "speech")
    private val model = File(dir, MODEL)

    val hasModel: Boolean get() = model.exists()

    val modelMb: Long get() = model.length() / 1_000_000

    fun download() = run("Couldn't download the speech model. Check the internet and try again.") { fetchModel() }

    fun start(path: String) { job?.cancel(); attempt(path, inProcess = true) }

    fun deleteModel() { job?.cancel(); stopWorker(); dir.deleteRecursively(); _state.value = State.Idle }

    fun cancel() { job?.cancel(); stopWorker(); _state.value = State.Idle }

    fun dismiss() { ChapterfyService.resultFile(context).delete(); _state.value = State.Idle }

    suspend fun use(done: State.Done) {
        edits.save(done.path, mapOf(CHAPTERS to done.chapters.asText()))
        dismiss()
    }

    /** What a run found while the app was closed: the worker leaves it in a file. */
    fun pickUpResult() {
        if (_state.value != State.Idle) return
        val lines = runCatching { ChapterfyService.resultFile(context).readLines() }.getOrNull() ?: return
        val path = lines.firstOrNull() ?: return
        _state.value = State.Done(path, chaptersFromText(lines.drop(1).joinToString("\n")))
    }

    private val main = Handler(Looper.getMainLooper())
    private var worker: IBinder? = null
    private var died: IBinder.DeathRecipient? = null
    private var connection: ServiceConnection? = null

    /** Starts the worker on [path] and follows it; [inProcess] false keeps FFmpeg out (see listenBook). */
    private fun attempt(path: String, inProcess: Boolean) {
        stopWorker()
        _state.value = State.Listening(path, 0, 0, 0)
        val reply = Messenger(Handler(Looper.getMainLooper()) { m ->
            if (_state.value !is State.Listening) return@Handler true
            when (m.what) {
                ChapterfyService.PROGRESS -> _state.value = State.Listening(
                    path, m.data.getLong(ChapterfyService.DONE_MS), m.data.getLong(ChapterfyService.TOTAL_MS), m.data.getInt(ChapterfyService.FOUND),
                )
                ChapterfyService.DONE -> { unwatch(); pickUpResultAfterRun() }
                ChapterfyService.FAILED -> { unwatch(); _state.value = State.Failed("Couldn't listen to this file.") }
            }
            true
        })
        val intent = Intent(context, ChapterfyService::class.java)
            .putExtra(ChapterfyService.PATH, path).putExtra(ChapterfyService.MODEL_PATH, model.path)
            .putExtra(ChapterfyService.REPLY, reply).putExtra(ChapterfyService.IN_PROCESS, inProcess)
        ContextCompat.startForegroundService(context, intent)
        // Bound only to hear of the worker's process dying.
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                val recipient = IBinder.DeathRecipient { main.post { crashed(path, inProcess) } }
                runCatching { binder.linkToDeath(recipient, 0) }.onFailure { main.post { crashed(path, inProcess) } }
                worker = binder
                died = recipient
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        connection = c
        context.bindService(Intent(context, ChapterfyService::class.java), c, Context.BIND_AUTO_CREATE)
    }

    private fun pickUpResultAfterRun() { _state.value = State.Idle; pickUpResult() }

    /** The worker's process went down mid-listen: once more without FFmpeg, else give up on this file. */
    private fun crashed(path: String, inProcess: Boolean) {
        if (_state.value !is State.Listening) return
        unwatch()
        if (inProcess) attempt(path, inProcess = false)
        else _state.value = State.Failed("Chapterfy kept crashing on this file. Its audio may be damaged.")
    }

    private fun unwatch() {
        died?.let { d -> runCatching { worker?.unlinkToDeath(d, 0) } }
        died = null
        worker = null
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
    }

    private fun stopWorker() {
        unwatch()
        context.stopService(Intent(context, ChapterfyService::class.java))
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
            val part = File(dir, "$MODEL.part")
            val sha = MessageDigest.getInstance("SHA-256")
            val c = URI(MODEL_URL).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                part.outputStream().use { out ->
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
            if (!part.renameTo(model)) error("Couldn't keep the model")
            _state.value = State.Idle
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }
}
