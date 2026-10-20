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

/** Title, author, or newest first (a book counts as added when its newest file was). */
@JvmName("booksSortedFor")
fun List<Book>.sortedFor(sort: Sort): List<Book> = when (sort) {
    Sort.Title -> sortedBy { it.title.lowercase() }
    Sort.Artist -> sortedWith(compareBy({ it.author.lowercase() }, { it.title.lowercase() }))
    Sort.Recent -> sortedByDescending { b -> b.chapters.maxOf { it.added } }
}

/** Books show with the album card and album page: cover, title, author, then the chapters. */
fun Book.asAlbum() = Album(id, title, author, artUri, chapters)
