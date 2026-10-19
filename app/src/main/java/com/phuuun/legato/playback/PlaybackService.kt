package com.phuuun.legato.playback

import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.phuuun.legato.LegatoApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** The only owner of the player. The UI reaches it through a MediaController, so the queue lives here and nowhere else. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            private var playing: MediaItem? = null

            // A track counts as played when it finishes on its own (a repeat-one loop included), never when skipped.
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) recordPlay(playing)
                playing = item
            }

            // The last track in the queue ends without a transition.
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) recordPlay(player.currentMediaItem)
            }

            // A missing or unreadable file skips ahead; a lone bad file stops instead of looping.
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlaybackService, "Couldn't play ${player.currentMediaItem?.mediaMetadata?.title ?: "this track"}", Toast.LENGTH_SHORT).show()
                if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare() }
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    private fun recordPlay(item: MediaItem?) {
        item ?: return
        scope.launch { (application as LegatoApp).playlists.recordPlay(item.trackId(), System.currentTimeMillis()) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
