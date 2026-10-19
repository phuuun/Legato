package com.phuuun.legato.library

import android.net.Uri

/** An audiobook: one file, or chapter files that share a book title. */
data class Book(val title: String, val author: String, val artUri: Uri?, val chapters: List<Track>) {
    val id: Long get() = chapters.first().id
}

/** Files group into a book by their album tag (the book title); a file without one is its own book. */
fun groupBooks(tracks: List<Track>): List<Book> =
    tracks.groupBy { t -> t.albumTag.ifEmpty { "#${t.id}" } }.map { (key, group) ->
        val chapters = group.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
        Book(if (key.startsWith("#")) chapters.first().title else key, chapters.first().artist, chapters.first().artUri, chapters)
    }.sortedBy { it.title.lowercase() }

/** Books show with the album card and album page: cover, title, author, then the chapters. */
fun Book.asAlbum() = Album(id, title, author, artUri, chapters)
