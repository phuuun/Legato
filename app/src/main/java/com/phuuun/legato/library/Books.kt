package com.phuuun.legato.library

import android.net.Uri

/** An audiobook: one file, or chapter files that share a book title. */
data class Book(val title: String, val author: String, val artUri: Uri?, val chapters: List<Track>) {
    val id: Long get() = chapters.first().id
}

/**
 * Files group into a book by their album tag (the book title). An untagged file gets MediaStore's
 * fallback album, its folder's name, so that, like "Unknown album", means "no book title": the file is its own book.
 */
fun groupBooks(tracks: List<Track>): List<Book> =
    tracks.groupBy { t ->
        val folder = t.relativePath.trimEnd('/').substringAfterLast('/')
        if (t.album == "Unknown album" || t.album == folder) "#${t.id}" else t.album
    }.map { (key, group) ->
        val chapters = group.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
        Book(if (key.startsWith("#")) chapters.first().title else key, chapters.first().artist, chapters.first().artUri, chapters)
    }.sortedBy { it.title.lowercase() }

/** Books show with the album card and album page: cover, title, author, then the chapters. */
fun Book.asAlbum() = Album(id, title, author, artUri, chapters)
