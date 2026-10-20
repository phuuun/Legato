package com.phuuun.legato.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.phuuun.legato.library.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App-wide handle on the playback service; null until the controller connects. */
class PlayerConnection(context: Context) {
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller
    val sleep = SleepTimer(controller)

    init {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({ _controller.value = future.get() }, ContextCompat.getMainExecutor(context))
    }

    // ponytail: a tap in the first moments before the controller connects is dropped; queue it if that ever bites.
    /** [audiobook] marks the items so their spots are saved; [startMs] is where in the start item to begin. */
    fun play(tracks: List<Track>, startIndex: Int, startMs: Long = 0, audiobook: Boolean = false) {
        val c = controller.value ?: return
        c.setMediaItems(tracks.map { it.toMediaItem(audiobook) }, startIndex, startMs)
        c.prepare()
        c.play()
    }

    fun queue(tracks: List<Track>, next: Boolean) {
        val c = controller.value ?: return
        val items = tracks.map { it.toMediaItem() }
        if (next) c.playNext(items) else c.enqueue(items)
    }
}
