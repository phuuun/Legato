package com.phuuun.legato.library

/** Grouped by MediaStore album id, not name: two "Greatest Hits" by different artists stay separate. */
fun groupAlbums(tracks: List<Track>): List<Album> =
    tracks.groupBy { it.albumId }.map { (id, group) ->
        val ordered = group.sortedBy { it.trackNumber } // stable: tracks with no number keep their input order
        Album(id, ordered.first().album, ordered.first().artist, ordered.first().artUri, ordered)
    }.sortedBy { it.title.lowercase() }

/** The order picked beside search. Artists keep A–Z whatever is picked; [Artist] means author for books. */
enum class Sort { Title, Artist, Recent }

fun List<Track>.sortedFor(sort: Sort): List<Track> = when (sort) {
    Sort.Title -> sortedBy { it.title.lowercase() }
    Sort.Artist -> sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
    Sort.Recent -> sortedByDescending { it.added }
}

@JvmName("albumsSortedFor")
fun List<Album>.sortedFor(sort: Sort): List<Album> = when (sort) {
    Sort.Title -> sortedBy { it.title.lowercase() }
    Sort.Artist -> sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    Sort.Recent -> sortedByDescending { a -> a.tracks.maxOf { it.added } }
}

/** An album sits under every artist on it, so a duet shows up under both singers. */
fun groupArtists(albums: List<Album>): List<Artist> =
    albums.flatMap { a -> a.tracks.flatMap { it.artists }.ifEmpty { listOf("Unknown artist") }.distinct().map { it to a } }
        .groupBy({ it.first }, { it.second })
        .map { (name, group) -> Artist(name, group) }
        .sortedBy { it.name.lowercase() }
