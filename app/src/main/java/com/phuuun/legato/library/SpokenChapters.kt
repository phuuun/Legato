package com.phuuun.legato.library

import kotlin.math.log10

// Chapterfy: chapters from what the narrator says. Only the first seconds after each long pause are listened to (by
// Whisper, see Whisper.kt), so a 10-hour book takes minutes, and only what starts like a heading counts.

private val units = listOf(
    "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
    "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
)
private val tens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
private val headings = listOf("chapter", "prologue", "epilogue", "part")

private fun isNumberWord(w: String) = w in units || w in tens || w == "hundred" || w == "and"

/** "twenty one" → 21, "one hundred and five" → 105; null when there's no number. */
fun spokenNumber(words: List<String>): Int? {
    var n = 0
    var any = false
    for (w in words) {
        when (w) {
            in units -> n += units.indexOf(w) + 1
            in tens -> n += (tens.indexOf(w) + 2) * 10
            "hundred" -> n = maxOf(n, 1) * 100
            "and" -> continue
            else -> break
        }
        any = true
    }
    return n.takeIf { any && it > 0 }
}

private fun words(text: String) = text.lowercase().replace('-', ' ').split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

/** Whether what was heard starts with a heading word: worth listening to whole (see Whisper.kt). */
fun startsLikeHeading(text: String): Boolean = words(text).firstOrNull() in headings

// A Roman numeral right after the heading word that ends the sentence ("Chapter II."), so "Part I think…" isn't Part 1.
private val roman = Regex("^\\W*\\w+\\W+([ivxlc]+)\\s*(?:[.,:;!?]|$)", RegexOption.IGNORE_CASE)

/** "ii" → 2, "xiv" → 14; null when it isn't one. */
fun romanNumber(s: String): Int? {
    val values = s.lowercase().map { mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100)[it] ?: return null }
    return values.indices.sumOf { i -> if (i + 1 < values.size && values[i] < values[i + 1]) -values[i] else values[i] }.takeIf { it > 0 }
}

/**
 * What was heard after a pause as a heading: "Chapter 21.", "Chapter Twenty-One.", "Chapter XXI." and "chapter twenty
 * one" → "Chapter 21", "Prologue." → "Prologue"; else null.
 */
fun spokenChapter(text: String): String? {
    val words = words(text)
    val rest = words.drop(1)
    val number = rest.firstOrNull()?.toIntOrNull()?.takeIf { it > 0 }
        ?: roman.find(text.trim())?.groupValues?.get(1)?.let(::romanNumber)
        ?: spokenNumber(rest.takeWhile(::isNumberWord))
    return when (words.firstOrNull()) {
        "chapter" -> if (number != null) "Chapter $number" else "Chapter"
        "prologue" -> "Prologue"
        "epilogue" -> "Epilogue"
        // "Part" alone came up as noise in testing; only "part" with a number counts.
        "part" -> number?.let { "Part $it" }
        else -> null
    }
}

/**
 * The headings heard, as a chapter list: in order, a repeat within 30 s dropped, and the top of the book added when
 * nothing was heard there (named "Chapter 1" when "Chapter 2" comes first, which is how most books start).
 */
fun chapterList(heard: List<Chapter>): List<Chapter> {
    val out = mutableListOf<Chapter>()
    for (c in heard.sortedBy { it.startMs }) if (out.isEmpty() || c.startMs - out.last().startMs >= 30_000) out += c
    val first = out.firstOrNull() ?: return out
    if (first.startMs > 30_000) out.add(0, Chapter(if (first.title == "Chapter 2") "Chapter 1" else "Opening", 0))
    return out
}

/** Loudness of one stretch of 16-bit samples, in dB below full scale. */
fun loudnessDb(sumOfSquares: Double, count: Int): Float =
    if (count == 0 || sumOfSquares <= 0) -120f else (10 * log10(sumOfSquares / count / (32768.0 * 32768.0))).toFloat()

// ponytail: a fixed threshold; a book with music or hiss under the reading never goes quiet enough. Learn the noise
// floor from the first minutes if one turns up.
/** Fed one 50 ms frame's loudness at a time; true on the first loud frame after a pause of at least [pauseFrames]. */
class PauseFinder(private val pauseFrames: Int = 30, private val silentDb: Float = SILENT_DB) {
    companion object {
        /** Quieter than this is silence. */
        const val SILENT_DB = -40f
    }

    private var quiet = 0

    fun speechStarts(db: Float): Boolean {
        if (db < silentDb) { quiet++; return false }
        val starts = quiet >= pauseFrames
        quiet = 0
        return starts
    }
}
