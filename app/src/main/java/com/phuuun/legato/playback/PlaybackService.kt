package com.phuuun.legato.playback

import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.phuuun.legato.LegatoApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
            // An audiobook chapter that finishes on its own is saved as heard to the end, so the book resumes at the next one.
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    recordPlay(playing)
                    playing?.let { saveSpot(it, it.mediaMetadata.durationMs ?: Long.MAX_VALUE) }
                }
                playing = item
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) player.currentMediaItem?.let { saveSpot(it, player.currentPosition) }
            }

            // Every item reports its tracks, so an untagged one lands back on full volume.
            override fun onTracksChanged(tracks: Tracks) {
                val tags = buildMap {
                    for (g in tracks.groups) if (g.isSelected) for (i in 0 until g.length) g.getTrackFormat(i).metadata?.let { putAll(replayGainTags(it)) }
                }
                player.volume = gainToVolume(parseGainDb(tags), parsePeak(tags))
            }

            // The last track in the queue ends without a transition.
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    recordPlay(player.currentMediaItem)
                    player.currentMediaItem?.let { saveSpot(it, it.mediaMetadata.durationMs ?: Long.MAX_VALUE) }
                }
            }

            // A missing or unreadable file skips ahead; a lone bad file stops instead of looping.
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlaybackService, "Couldn't play ${player.currentMediaItem?.mediaMetadata?.title ?: "this track"}", Toast.LENGTH_SHORT).show()
                if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare() }
            }
        })
        session = MediaSession.Builder(this, player).build()
        // Audiobook spots also save every 10 s while playing, so a crash or a dead battery loses at most that.
        scope.launch {
            while (true) {
                delay(10_000)
                if (player.isPlaying) player.currentMediaItem?.let { saveSpot(it, player.currentPosition) }
            }
        }
    }

    private fun saveSpot(item: MediaItem, positionMs: Long) {
        if (item.isAudiobook) (application as LegatoApp).progress.save(item.trackId(), positionMs, System.currentTimeMillis())
    }

    private fun recordPlay(item: MediaItem?) {
        item ?: return
        scope.launch { (application as LegatoApp).playlists.recordPlay(item.trackId(), System.currentTimeMillis()) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.player?.let { p -> p.currentMediaItem?.let { saveSpot(it, p.currentPosition) } }
        session?.run { player.release(); release() }
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
