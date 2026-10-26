package com.phuuun.legato.library

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import kotlin.math.absoluteValue

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
    val added: Long = 0,
    /** The album-artist tag, split like [artists]; empty when the file has none. */
    val albumArtists: List<String> = emptyList(),
)

/** The album tag as the file has it: blank when MediaStore made one up ("Unknown album", or the folder's name for an untagged file). */
val Track.albumTag: String
    get() = album.takeUnless { it == "Unknown album" || it == relativePath.trimEnd('/').substringAfterLast('/') }.orEmpty()

/** [artists]: who the album is by (see mainArtists); [artist] shows them, "Unknown artist" when there are none. */
data class Album(val id: Long, val title: String, val artist: String, val artUri: Uri?, val tracks: List<Track>, val artists: List<String> = emptyList())

/** Like Spotify: [albums] are the artist's own, [appearsOn] are other artists' albums with them on a song, [songs] are on no album. */
data class Artist(val name: String, val albums: List<Album>, val appearsOn: List<Album> = emptyList(), val songs: List<Track> = emptyList()) {
    /** Everything they're on: their albums whole, their songs on the rest, and their songs on no album. */
    val tracks: List<Track> get() = albums.flatMap { it.tracks } + appearsOn.flatMap { a -> a.tracks.filter { name in it.artists } } + songs
}

// ponytail: a comma always splits, so "Tyler, The Creator" becomes two artists; keep a list of comma names if one turns up.
/** One artist tag as its artists: "A; B" (what Edit writes) and "A, B" (typed by hand) are both two. */
fun splitArtists(tag: String): List<String> = tag.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Blank tags get a label instead of an empty row; a blank title falls back to the file name.
 * Several artists, written "A; B" or "A, B", are shown as "A, B".
 * The cover is the file's own embedded picture, versioned by its modified time so a retagged file shows up at once.
 * [edit] (see EditStore) goes over what the file says; a renamed album gets an id of its own, since MediaStore's
 * belongs to the old name.
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
    added: Long = 0,
    albumArtist: String? = null,
    edit: Map<String, String> = emptyMap(),
): Track {
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artists = splitArtists(edit["ARTIST"] ?: artist.orEmpty()).filter { it != MediaStore.UNKNOWN_STRING }
    // Edit offers the album tag, blank for an untagged file (whose MediaStore album is its folder): saving it unchanged isn't a rename.
    val albumTag = album.takeUnless { it == MediaStore.UNKNOWN_STRING || it == relativePath.trimEnd('/').substringAfterLast('/') }.orEmpty()
    val editedAlbum = edit["ALBUM"]?.takeIf { it != albumTag }
    // ponytail: hash ids could collide (1 in ~4 billion); store a counter if two albums ever merge.
    val editedAlbumId = editedAlbum?.let { -1L - "${it.lowercase()}\u0000$relativePath".hashCode().toLong().absoluteValue }
    return Track(
        id = id,
        title = (edit["TITLE"] ?: title)?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
        artist = artists.joinToString(", ").ifEmpty { "Unknown artist" },
        album = (editedAlbum ?: albumTag).ifBlank { "Unknown album" },
        albumId = editedAlbumId ?: albumId,
        durationMs = durationMs,
        uri = uri,
        artUri = when (val cover = edit[COVER]) {
            null -> uri.buildUpon().appendQueryParameter("v", modified.toString()).build()
            "" -> null
            else -> Uri.fromFile(File(cover))
        },
        trackNumber = edit["TRACKNUMBER"]?.toIntOrNull() ?: trackNumber,
        path = path,
        relativePath = relativePath,
        composer = (edit["COMPOSER"] ?: composer)?.takeIf { it != MediaStore.UNKNOWN_STRING }.orEmpty(),
        modified = modified,
        artists = artists,
        added = added,
        albumArtists = splitArtists(edit["ALBUMARTIST"] ?: albumArtist.orEmpty()).filter { it != MediaStore.UNKNOWN_STRING },
    )
}
