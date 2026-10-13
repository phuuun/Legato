package com.phuuun.legato.library

/** Grouped by MediaStore album id, not name: two "Greatest Hits" by different artists stay separate. */
fun groupAlbums(tracks: List<Track>): List<Album> =
    tracks.groupBy { it.albumId }.map { (id, group) ->
        val ordered = group.sortedBy { it.trackNumber } // stable: tracks with no number keep their input order
        Album(id, ordered.first().album, ordered.first().artist, ordered.first().artUri, ordered)
    }.sortedBy { it.title.lowercase() }

fun groupArtists(albums: List<Album>): List<Artist> =
    albums.groupBy { it.artist }.map { (name, group) -> Artist(name, group) }.sortedBy { it.name.lowercase() }
