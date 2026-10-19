package com.phuuun.legato.library.edit

import com.phuuun.legato.library.Track

data class Guess(val artist: String, val title: String)

private val junk = setOf("official", "video", "music", "audio", "lyric", "lyrics", "lirik", "remaster", "remastered", "4k", "hd", "mv", "visualizer")
private val separators = listOf(" - ", " – ", " — ")
private val bracketed = Regex("""\([^)]*\)|\[[^\]]*]""")
private val word = Regex("""[\p{L}\p{N}']+""")

private fun isJunk(w: String) = w.lowercase() in junk

/** Drops bracketed parts that hold a junk word, then junk words off the end, never the last word left. */
internal fun clean(part: String): String {
    val words = bracketed.replace(part) { m -> if (word.findAll(m.value).any { isJunk(it.value) }) " " else m.value }
        .split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
    while (words.size > 1 && isJunk(words.last().trim { !it.isLetterOrDigit() })) words.removeAt(words.lastIndex)
    return words.joinToString(" ")
}

/**
 * "Agnes Monica - Rindu  Official 4K Remastered" → Agnes Monica / Rindu. Artist-first is a guess:
 * some rips are "Title - Artist", which the bulk fix's swap button is for. No dash → title only.
 */
fun guessFromTitle(raw: String): Guess {
    val (at, sep) = separators.mapNotNull { s -> raw.indexOf(s).takeIf { it >= 0 }?.let { it to s } }.minByOrNull { it.first }
        ?: return Guess("", clean(raw))
    return Guess(clean(raw.substring(0, at)), clean(raw.substring(at + sep.length)))
}

/** One song in the bulk fix: its guessed artist and title, ticked only when the guess found an artist. */
data class FixRow(val track: Track, val artist: String, val title: String, val include: Boolean)

fun fixRows(tracks: List<Track>): List<FixRow> =
    tracks.filter { it.artists.isEmpty() }.map { t -> guessFromTitle(t.title).let { g -> FixRow(t, g.artist, g.title, include = g.artist.isNotBlank()) } }
