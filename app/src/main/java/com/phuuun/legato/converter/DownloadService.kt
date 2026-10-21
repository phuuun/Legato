package com.phuuun.legato.converter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.phuuun.legato.LegatoApp
import com.phuuun.legato.library.CHAPTERS
import com.phuuun.legato.library.Chapter
import com.phuuun.legato.library.asText
import com.phuuun.legato.library.edit.CoverChange
import com.phuuun.legato.library.edit.coverFromUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

private const val CHUNK = 8L * 1024 * 1024
private const val CHANNEL = "downloads"
private const val NOTIFICATION = 2

// Work in progress is hidden: MediaStore ignores dot files.
private const val PART = ".legato-part-"

/**
 * Works through [DownloadQueue] one job at a time, in the foreground so it survives leaving the app:
 * fresh stream link → 8 MB range chunks into a hidden file → rename into place → tags, cover and chapters saved in
 * Legato (see EditStore) → MediaStore scan. The file is kept exactly as downloaded: tagging a big file in place once
 * garbled an audiobook.
 * A failure deletes the hidden file and leaves the job for Retry.
 */
class DownloadService : Service() {
    // Main dispatcher: the "anything left?" check and the end of the loop can't interleave with onStartCommand.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running = false
    private val app get() = application as LegatoApp

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION, notification("Getting ready…", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!running) {
            running = true
            scope.launch {
                clearStaleParts()
                while (true) process(app.downloads.next() ?: break)
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Leftovers from a run that died (app killed, phone off) in the folders about to be used. */
    private suspend fun clearStaleParts() = withContext(Dispatchers.IO) {
        app.downloads.jobs.value.filter { it.state == JobState.Queued }.map { it.folder }.distinct().forEach { folder ->
            File(Environment.getExternalStorageDirectory(), folder).listFiles { f -> f.name.startsWith(PART) }?.forEach { it.delete() }
        }
    }

    private suspend fun process(job: DownloadJob) {
        val queue = app.downloads
        val dir = File(Environment.getExternalStorageDirectory(), job.folder)
        var part: File? = null
        try {
            val old = job.replaces
            val name = uniqueName(job.fileName, dir.list()?.toSet().orEmpty())
            part = File(dir, PART + name)
            // Without all files access, Android only lets Legato make files in its standard folders (Music, Download, …).
            val made = withContext(Dispatchers.IO) { runCatching { dir.mkdirs(); part.createNewFile() }.getOrDefault(false) }
            if (!made) error("Android won't let Legato save into ${job.folder}. Pick a folder inside Music or Download.")
            queue.set(job.id, JobState.Downloading(0))
            updateNotification("Downloading ${job.label}", 0)
            val stream = YouTube.stream(job.videoUrl)
            val url = stream.audioUrl
            var shown = 0L
            withContext(Dispatchers.IO) {
                download(url, part) { percent ->
                    val now = System.currentTimeMillis()
                    if (now - shown > 1_000) {
                        shown = now
                        queue.set(job.id, JobState.Downloading(percent))
                        updateNotification("Downloading ${job.label}", percent)
                    }
                }
            }
            queue.set(job.id, JobState.Tagging)
            updateNotification("Saving ${job.label}", null)
            val cover = job.thumbnail?.let { coverFromUrl(it) }?.let { CoverChange.Set(it) } ?: CoverChange.Keep
            val chapters = stream.chapters.takeIf { it.size >= 2 }?.map { Chapter(it.title, it.startMs) }?.asText()
            val properties = if (chapters != null) job.properties + (CHAPTERS to chapters) else job.properties
            val target = File(dir, name)
            if (!part.renameTo(target)) error("Couldn't save the file")
            app.edits.save(target.path, properties, cover)
            val uri = scan(target.path)
            // A replaced file hands its playlist spots, plays, listening spot and bookmarks to the new one and is hidden
            // in Legato, never deleted: Legato doesn't touch files it didn't make. Settings → Hidden brings it back.
            if (old != null && uri != null) {
                val new = ContentUris.parseId(uri)
                app.playlists.moveTrack(old.id, new)
                app.progress.move(old.id, new)
                app.bookmarks.move(old.id, new)
                app.folders.hide(listOf(old.path))
            }
            queue.set(job.id, JobState.Done)
        } catch (e: Exception) {
            part?.delete()
            queue.set(job.id, JobState.Failed(if (e is CancellationException) "Stopped before it finished" else youTubeError(e, "Download failed")))
            // The service is going away: stop here instead of failing every job still waiting.
            if (e is CancellationException) throw e
        }
    }

    // Android 15 allows a data-sync service about 6 hours a day; a service that doesn't stop when told crashes the app.
    // What's still waiting fails with the reason, and Retry picks it up once Legato is back in front.
    override fun onTimeout(startId: Int, fgsType: Int) {
        app.downloads.jobs.value.filter { it.state == JobState.Queued }
            .forEach { app.downloads.set(it.id, JobState.Failed("Android's download time for today ran out")) }
        stopSelf()
    }

    /** Has MediaStore read the file; the link it returns carries the file's id. Null if the scanner doesn't answer in 10 s. */
    private suspend fun scan(path: String): Uri? = withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine { done -> MediaScannerConnection.scanFile(this@DownloadService, arrayOf(path), null) { _, uri -> done.resume(uri) } }
    }

    /** Range requests in 8 MB chunks: YouTube throttles or drops long single responses. */
    private fun download(url: String, file: File, onProgress: (Int) -> Unit) {
        var done = 0L
        var total = Long.MAX_VALUE
        file.outputStream().buffered().use { out ->
            while (done < total) {
                val c = URI(url).toURL().openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("Range", "bytes=$done-${done + CHUNK - 1}")
                when (c.responseCode) {
                    206 -> total = c.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: error("No file size from YouTube")
                    200 -> total = Long.MIN_VALUE // the whole file in one go; stop after this read
                    // A 403 on a fresh link usually means YouTube changed how links are signed.
                    403 -> error("YouTube refused the download (403). If it keeps happening: $EXTRACTOR_BROKE")
                    else -> error("YouTube refused the download (${c.responseCode})")
                }
                val read = c.inputStream.use { it.copyTo(out) }
                if (read == 0L) error("The download stalled")
                done += read
                if (total == Long.MIN_VALUE) break
                onProgress((done * 100 / total).toInt())
            }
        }
    }

    private fun notification(text: String, percent: Int?) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Legato")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()

    private fun updateNotification(text: String, percent: Int?) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(text, percent))

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }
}
