package com.phuuun.legato.ui.nowplaying

import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import com.phuuun.legato.playback.coverUri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

data class NowPlayingState(
    val title: String,
    val artist: String,
    val artUri: Uri?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val shuffle: Boolean,
    val repeatMode: Int,
    val trackId: Long?,
)

/** Null state means nothing is queued, so the mini player hides. */
@OptIn(UnstableApi::class)
class NowPlayingViewModel(val controller: StateFlow<MediaController?>) : ViewModel() {

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<NowPlayingState?> = controller.filterNotNull().flatMapLatest { c ->
        callbackFlow {
            val l = object : Player.Listener { override fun onEvents(player: Player, events: Player.Events) { trySend(Unit) } }
            c.addListener(l)
            trySend(Unit)
            awaitClose { c.removeListener(l) }
        }.transformLatest {
            // Every player event re-reads the state; while playing, the position ticks on top of that.
            emit(c.snapshot())
            while (c.isPlaying) { delay(500); emit(c.snapshot()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun MediaController.snapshot(): NowPlayingState? {
        val item = currentMediaItem ?: return null
        val m = item.mediaMetadata
        return NowPlayingState(
            m.title?.toString().orEmpty(), m.artist?.toString().orEmpty(), item.coverUri(),
            currentPosition, duration, !Util.shouldShowPlayButton(this), shuffleModeEnabled, repeatMode,
            currentMediaItem?.mediaId?.toLongOrNull(),
        )
    }

    private val c get() = controller.value

    // Handles the awkward states too: replays an ended queue, re-prepares after an error.
    fun togglePlay() { c?.let { Util.handlePlayPauseButtonAction(it) } }
    fun next() { c?.seekToNext() }
    fun previous() { c?.seekToPrevious() }
    fun seekTo(ms: Long) { c?.seekTo(ms) }
    fun toggleShuffle() { c?.run { shuffleModeEnabled = !shuffleModeEnabled } }
    fun cycleRepeat() {
        c?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
}
