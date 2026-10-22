package com.phuuun.legato.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.core.os.bundleOf
import com.phuuun.legato.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

private const val CHANNEL = "chapterfy"
private const val WORKING = 3
private const val FINISHED = 4

/**
 * Chapterfy's own process (":chapterfy" in the manifest). FFmpeg and Whisper are native code fed whatever is in the file,
 * and damaged audio has crashed FFmpeg; here a crash ends only this process, and the app, watching it (see Chapterfy),
 * listens again without FFmpeg. It runs in the foreground with its progress in a notification, so the phone can be put
 * away meanwhile, and leaves what it found in [resultFile] for an app that was closed in the meantime.
 */
class ChapterfyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    // Binding is only for the app to notice this process dying; the work comes in through onStartCommand.
    override fun onBind(intent: Intent): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val path = intent?.getStringExtra(PATH)
        val model = intent?.getStringExtra(MODEL_PATH)
        if (path == null || model == null) { stopSelf(); return START_NOT_STICKY }
        val reply = IntentCompat.getParcelableExtra(intent, REPLY, Messenger::class.java)
        val inProcess = intent.getBooleanExtra(IN_PROCESS, true)
        val title = File(path).nameWithoutExtension
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Chapterfy", NotificationManager.IMPORTANCE_LOW))
        startForeground(WORKING, working(title, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        job?.cancel()
        job = scope.launch {
            try {
                var shown = 0L
                val chapters = listenBook(path, model, inProcess) { done, total, found ->
                    send(reply, PROGRESS, bundleOf(DONE_MS to done, TOTAL_MS to total, FOUND to found))
                    val now = System.currentTimeMillis()
                    if (now - shown > 1_000) { shown = now; notifications.notify(WORKING, working(title, if (total > 0) (done * 100 / total).toInt() else 0)) }
                }
                resultFile(this@ChapterfyService).writeText(path + "\n" + chapters.asText())
                send(reply, DONE, null)
                notifications.notify(FINISHED, finished(title, "Found ${chapters.size} chapters. Tap to look them over."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(reply, FAILED, null)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun send(to: Messenger?, what: Int, data: android.os.Bundle?) {
        runCatching { to?.send(Message.obtain(null, what).apply { if (data != null) this.data = data }) }
    }

    private fun working(title: String, percent: Int) =
        NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("Chapterfy").setContentText("Listening to $title").setProgress(100, percent, false)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openPlayer()).build()

    private fun finished(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("Chapterfy · $title").setContentText(text).setAutoCancel(true).setContentIntent(openPlayer()).build()

    private fun openPlayer() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).setAction(MainActivity.OPEN_PLAYER), PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val PATH = "path"
        const val MODEL_PATH = "model"
        const val REPLY = "reply"
        const val IN_PROCESS = "inProcess"
        const val PROGRESS = 1
        const val DONE = 2
        const val FAILED = 3
        const val DONE_MS = "done"
        const val TOTAL_MS = "total"
        const val FOUND = "found"

        /** What the last run found: the file's path on the first line, then its chapters as timestamp lines. */
        fun resultFile(context: android.content.Context) = File(context.filesDir, "chapterfy-result.txt")
    }
}
