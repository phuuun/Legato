package com.phuuun.legato.ui.library

import com.phuuun.legato.library.Album
import com.phuuun.legato.library.Artist
import com.phuuun.legato.library.Track
import com.phuuun.legato.library.albumTag
import com.phuuun.legato.library.groupAlbums
import com.phuuun.legato.library.groupArtists

data class SearchResults(val tracks: List<Track>, val albums: List<Album>, val artists: List<Artist>)

private val Empty = SearchResults(emptyList(), emptyList(), emptyList())

/** Case-insensitive substring match over title, artist and album. A blank query matches nothing. */
fun search(query: String, tracks: List<Track>): SearchResults {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return Empty
    fun String.hit() = lowercase().contains(q)
    val albums = groupAlbums(tracks).filter { it.title.hit() || it.artist.hit() }
    return SearchResults(
        tracks = tracks.filter { it.title.hit() || it.artist.hit() || it.album.hit() },
        albums = albums,
        artists = groupArtists(groupAlbums(tracks), tracks.filter { it.albumTag.isEmpty() }).filter { it.name.hit() },
    )
}
