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
    val artists: List<String> = emptyList(),
)

data class Album(val id: Long, val title: String, val artist: String, val artUri: Uri?, val tracks: List<Track>)

data class Artist(val name: String, val albums: List<Album>)

/**
 * Blank tags get a label instead of an empty row; a blank title falls back to the file name.
 * Several artists are stored as "A; B" (what Edit writes) and shown as "A, B".
 * The cover is the file's own embedded picture, versioned by its modified time so an edit shows up at once.
 */
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
): Track {
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artists = artist.orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
    return Track(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
        artist = artists.joinToString(", ").ifEmpty { "Unknown artist" },
        album = album?.takeIf { it.isNotBlank() } ?: "Unknown album",
        albumId = albumId,
        durationMs = durationMs,
        uri = uri,
        artUri = uri.buildUpon().appendQueryParameter("v", modified.toString()).build(),
        trackNumber = trackNumber,
        path = path,
        relativePath = relativePath,
        composer = composer?.takeIf { it != MediaStore.UNKNOWN_STRING }.orEmpty(),
        modified = modified,
        artists = artists,
    )
}
