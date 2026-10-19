package com.phuuun.legato.converter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.phuuun.legato.LegatoApp
import com.phuuun.legato.library.edit.CoverChange
import com.phuuun.legato.library.edit.coverFromUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

private const val CHUNK = 8L * 1024 * 1024
private const val CHANNEL = "downloads"
private const val NOTIFICATION = 2

// Work in progress is hidden (dot prefix, so MediaStore ignores it) but keeps ".m4a" so TagLib sees an MP4.
private const val PART = ".legato-part-"

/**
 * Works through [DownloadQueue] one job at a time, in the foreground so it survives leaving the app:
 * fresh stream link → 8 MB range chunks into a hidden file → tags and cover → rename into place → MediaStore scan.
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
            val name = uniqueName(job.fileName, dir.list()?.toSet().orEmpty())
            part = File(dir, PART + name)
            queue.set(job.id, JobState.Downloading(0))
            updateNotification("Downloading ${job.label}", 0)
            val url = YouTube.audioUrl(job.videoUrl)
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
            updateNotification("Tagging ${job.label}", null)
            val cover = job.thumbnail?.let { coverFromUrl(it) }?.let { CoverChange.Set(it) } ?: CoverChange.Keep
            if (!app.tagWriter.write(part.path, job.properties, cover, rescan = false)) error("Couldn't write the tags")
            val target = File(dir, name)
            if (!part.renameTo(target)) error("Couldn't save the file")
            MediaScannerConnection.scanFile(this, arrayOf(target.path), null, null)
            queue.set(job.id, JobState.Done)
        } catch (e: Exception) {
            part?.delete()
            queue.set(job.id, JobState.Failed(e.message ?: "Download failed"))
        }
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
