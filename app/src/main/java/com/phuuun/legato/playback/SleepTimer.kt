package com.phuuun.legato.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Pauses playback after a while, or when the chapter playing ends. App-wide, so it outlives the full player. */
class SleepTimer(private val controller: StateFlow<MediaController?>) {
    sealed interface Mode
    /** Pauses at [endsAt], on the [SystemClock.elapsedRealtime] clock. */
    data class After(val endsAt: Long) : Mode
    data object ChapterEnd : Mode

    private val _mode = MutableStateFlow<Mode?>(null)
    val mode: StateFlow<Mode?> = _mode
    private val scope = MainScope()
    private var job: Job? = null

    fun after(minutes: Int) {
        val ms = minutes * 60_000L
        start(After(SystemClock.elapsedRealtime() + ms)) { delay(ms) }
    }

    // ponytail: the pause lands just after the next chapter starts, so a split second of it plays; the saved spot
    // still resumes at its top. Use ExoPlayer's pauseAtEndOfMediaItems through a session command if that grates.
    fun atChapterEnd() = start(ChapterEnd) { controller.value?.chapterEnds()?.first() }

    fun cancel() {
        job?.cancel()
        _mode.value = null
    }

    private fun start(mode: Mode, wait: suspend () -> Unit) {
        job?.cancel()
        _mode.value = mode
        job = scope.launch {
            wait()
            controller.value?.pause()
            _mode.value = null
        }
    }

    /** A chapter that ends on its own either moves to the next item or, as the last one, ends the queue. */
    private fun MediaController.chapterEnds() = callbackFlow {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) trySend(Unit) }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) trySend(Unit) }
        }
        addListener(l)
        awaitClose { removeListener(l) }
    }
}
