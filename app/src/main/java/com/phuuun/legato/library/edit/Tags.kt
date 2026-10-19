package com.phuuun.legato.library.edit

import kotlin.math.roundToInt

data class MusicEdit(val title: String, val artists: List<String>, val album: String)

/** [chapterTitle] is null for a one-file book, whose title is the book's. */
data class BookEdit(val bookTitle: String, val chapterTitle: String?, val author: String, val narrator: String)

/** Tag keys as TagLib names them. A blank value means "remove this tag". */
fun MusicEdit.properties(): Map<String, String> = mapOf(
    "TITLE" to title.trim(),
    "ARTIST" to artists.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("; "),
    "ALBUM" to album.trim(),
)

/** Author goes in ARTIST and ALBUMARTIST, narrator in COMPOSER: what most audiobook players read. */
fun BookEdit.properties(): Map<String, String> = mapOf(
    "TITLE" to (chapterTitle ?: bookTitle).trim(),
    "ALBUM" to bookTitle.trim(),
    "ARTIST" to author.trim(),
    "ALBUMARTIST" to author.trim(),
    "COMPOSER" to narrator.trim(),
)

sealed interface CoverChange {
    data object Keep : CoverChange
    data object Remove : CoverChange
    class Set(val jpeg: ByteArray) : CoverChange
}

/** Stored covers are at most 1000px on the long side; smaller pictures keep their size. */
fun coverSize(width: Int, height: Int, max: Int = 1000): Pair<Int, Int> {
    val scale = minOf(1f, max.toFloat() / maxOf(width, height))
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

data class Crop(val left: Int, val top: Int, val size: Int)

/** The centre square of a picture: YouTube thumbnails are 16:9, covers are square. */
fun squareCrop(width: Int, height: Int): Crop {
    val s = minOf(width, height)
    return Crop((width - s) / 2, (height - s) / 2, s)
}
