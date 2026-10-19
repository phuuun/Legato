package com.phuuun.legato.playback

import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.phuuun.legato.library.Track

private const val COVER = "cover"

/**
 * The media id is the MediaStore id, so any queued item maps back to its track.
 * No artworkUri on purpose: Media3 "loads" it by reading the whole resource into memory, and the cover
 * link is the audio file itself (a 300 MB WAV, a 600 MB audiobook). Embedded art still reaches the
 * notification as artworkData pulled from the stream; Legato's own screens read [coverUri].
 */
/** [audiobook] marks the item so the playback service saves its spot (see ProgressStore). */
fun Track.toMediaItem(audiobook: Boolean = false): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
            .setMediaType(if (audiobook) MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER else null)
            .setDurationMs(durationMs)
            .setExtras(bundleOf(COVER to artUri?.toString())).build(),
    )
    .build()

fun MediaItem.trackId(): Long = mediaId.toLong()

val MediaItem.isAudiobook: Boolean get() = mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER

/** The track's cover link for Legato's own artwork (see AudioCover). */
fun MediaItem.coverUri(): Uri? = mediaMetadata.extras?.getString(COVER)?.let(Uri::parse)
