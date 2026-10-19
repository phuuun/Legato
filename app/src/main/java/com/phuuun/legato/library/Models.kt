package com.phuuun.legato.library

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val artUri: Uri?,
    val trackNumber: Int = 0,
    val path: String = "",
    val relativePath: String = "",
    val composer: String = "",
    val modified: Long = 0,
)

data class Album(val id: Long, val title: String, val artist: String, val artUri: Uri?, val tracks: List<Track>)

data class Artist(val name: String, val albums: List<Album>)

/** Blank tags get a label instead of an empty row; a blank title falls back to the file name. */
fun trackFromRow(
    id: Long,
    title: String?,
    artist: String?,
    album: String?,
    albumId: Long,
    durationMs: Long,
    displayName: String,
    trackNumber: Int = 0,
    path: String = "",
    relativePath: String = "",
    composer: String? = null,
    modified: Long = 0,
): Track = Track(
    id = id,
    title = title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
    artist = artist?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: "Unknown artist",
    album = album?.takeIf { it.isNotBlank() } ?: "Unknown album",
    albumId = albumId,
    durationMs = durationMs,
    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
    artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId),
    trackNumber = trackNumber,
    path = path,
    relativePath = relativePath,
    composer = composer?.takeIf { it != MediaStore.UNKNOWN_STRING }.orEmpty(),
    modified = modified,
)
