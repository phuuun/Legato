package com.phuuun.legato.converter

import com.phuuun.legato.library.edit.Guess
import com.phuuun.legato.library.edit.clean
import com.phuuun.legato.library.edit.guessFromTitle

/**
 * Artist and title for a music video. YouTube Music's auto-made "Artist - Topic" channels name the artist
 * outright; otherwise the title's "Artist - Title", and failing that the channel (minus "VEVO").
 */
fun guessSong(title: String, uploader: String): Guess {
    if (uploader.endsWith(" - Topic")) return Guess(uploader.removeSuffix(" - Topic").trim(), clean(title))
    val g = guessFromTitle(title)
    return if (g.artist.isNotBlank()) g else Guess(uploader.trim().removeSuffix("VEVO").removeSuffix(" Official").trim(), g.title)
}

data class BookGuess(val title: String, val author: String)

private val bookJunk = Regex("""(?i)\b(full audiobook|audiobook|unabridged|complete)\b""")
private val bracketed = Regex("""\([^)]*\)|\[[^\]]*]""")
private val authorEnd = Regex("""\s*(\||_|\s-\s|\(|\[)""")

/** "The Inheritance Games by Jennifer Lynn Barnes _ Full Audiobook _ …" → The Inheritance Games / Jennifer Lynn Barnes. */
fun guessBook(raw: String): BookGuess {
    val by = Regex("""(?i)\s+by\s+""").find(raw)
    val author = by?.let { raw.substring(it.range.last + 1).split(authorEnd, limit = 2)[0].trim() }.orEmpty()
    val book = (if (by != null) raw.substring(0, by.range.first) else raw)
        .replace(bracketed) { m -> if (bookJunk.containsMatchIn(m.value)) "" else m.value }
        .replace(bookJunk, "")
        .trim().trimEnd('-', '_', '|', ' ')
    return BookGuess(book, author)
}
