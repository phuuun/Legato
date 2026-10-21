package com.phuuun.legato.library

import com.phuuun.legato.library.db.PlayStat

/** Grouped by MediaStore album id, not name: two "Greatest Hits" by different artists stay separate. */
fun groupAlbums(tracks: List<Track>): List<Album> =
    tracks.groupBy { it.albumId }.map { (id, group) ->
        val ordered = group.sortedBy { it.trackNumber } // stable: tracks with no number keep their input order
        val by = mainArtists(ordered)
        Album(id, ordered.first().album, by.joinToString(", ").ifEmpty { "Unknown artist" }, ordered.first().artUri, ordered, by)
    }.sortedBy { it.title.lowercase() }

/**
 * Who an album is by: its album-artist tag, else the artist listed first on most of its tracks (a tie goes to the
 * earlier track). So DAMN. is Kendrick Lamar's, not Rihanna's for the one song she's on.
 */
fun mainArtists(tracks: List<Track>): List<String> =
    tracks.firstNotNullOfOrNull { it.albumArtists.ifEmpty { null } }
        ?: tracks.mapNotNull { it.artists.firstOrNull() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.let(::listOf).orEmpty()

/** The order picked beside search. Artists keep A–Z whatever is picked; [Artist] means author for books. */
enum class Sort { Title, Artist, Recent, Plays, Played }

/** How tracks rank under [Sort.Plays] (all their plays) or [Sort.Played] (their latest play); 0 when never played. */
fun Map<Long, PlayStat>.score(tracks: List<Track>, sort: Sort): Long =
    if (sort == Sort.Plays) tracks.sumOf { get(it.id)?.playCount?.toLong() ?: 0 } else tracks.maxOf { get(it.id)?.lastPlayedAt ?: 0 }

/** Most first; ties and the never played go A–Z. */
fun <T> List<T>.byPlays(title: (T) -> String, score: (T) -> Long): List<T> = sortedWith(compareByDescending(score).thenBy { title(it).lowercase() })

fun List<Track>.sortedFor(sort: Sort, stats: Map<Long, PlayStat> = emptyMap()): List<Track> = when (sort) {
    Sort.Title -> sortedBy { it.title.lowercase() }
    Sort.Artist -> sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
    Sort.Recent -> sortedByDescending { it.added }
    Sort.Plays, Sort.Played -> byPlays({ it.title }) { stats.score(listOf(it), sort) }
}

@JvmName("albumsSortedFor")
fun List<Album>.sortedFor(sort: Sort, stats: Map<Long, PlayStat> = emptyMap()): List<Album> = when (sort) {
    Sort.Title -> sortedBy { it.title.lowercase() }
    Sort.Artist -> sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    Sort.Recent -> sortedByDescending { a -> a.tracks.maxOf { it.added } }
    Sort.Plays, Sort.Played -> byPlays({ it.title }) { stats.score(it.tracks, sort) }
}

/** An album sits under its main artists' Albums, and under Appears on for everyone else on one of its songs. */
fun groupArtists(albums: List<Album>): List<Artist> {
    val own = albums.flatMap { a -> a.artists.ifEmpty { listOf("Unknown artist") }.map { it to a } }.groupBy({ it.first }, { it.second })
    val guest = albums.flatMap { a -> a.tracks.flatMap { it.artists }.distinct().filter { it !in a.artists }.map { it to a } }.groupBy({ it.first }, { it.second })
    return (own.keys + guest.keys).map { Artist(it, own[it].orEmpty(), guest[it].orEmpty()) }.sortedBy { it.name.lowercase() }
}
