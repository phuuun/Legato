package com.phuuun.legato.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.phuuun.legato.library.Track

/** The media id is the MediaStore id, so any queued item maps back to its track. */
fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(artUri).build())
    .build()

fun MediaItem.trackId(): Long = mediaId.toLong()
