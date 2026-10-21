package com.phuuun.legato.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenChaptersTest {
    @Test
    fun numbers() {
        assertEquals(7, spokenNumber(listOf("seven")))
        assertEquals(21, spokenNumber(listOf("twenty", "one")))
        assertEquals(105, spokenNumber(listOf("one", "hundred", "and", "five")))
        assertEquals(40, spokenNumber(listOf("forty", "[unk]", "eight"))) // stops at the first word that isn't a number
        assertNull(spokenNumber(listOf("[unk]")))
    }

    // What the recognizer really returned on The Inheritance Games.
    @Test
    fun headings() {
        assertEquals("Chapter 2", spokenChapter("chapter two [unk] [unk]"))
        assertEquals("Chapter 4", spokenChapter("chapter four [unk] eight"))
        assertEquals("Chapter 21", spokenChapter("chapter twenty one"))
        assertEquals("Chapter", spokenChapter("chapter [unk]"))
        assertEquals("Prologue", spokenChapter("prologue [unk]"))
        assertEquals("Part 3", spokenChapter("part three"))
        assertNull(spokenChapter("part [unk] [unk]"))
        assertNull(spokenChapter("[unk] chapter two"))
        assertNull(spokenChapter(""))
    }

    @Test
    fun list_adds_the_top_and_drops_repeats() {
        val heard = listOf(Chapter("Chapter 3", 684_000), Chapter("Chapter 2", 445_000), Chapter("Chapter 3", 690_000))
        assertEquals(listOf(Chapter("Chapter 1", 0), Chapter("Chapter 2", 445_000), Chapter("Chapter 3", 684_000)), chapterList(heard))
        assertEquals(listOf(Chapter("Opening", 0), Chapter("Prologue", 90_000)), chapterList(listOf(Chapter("Prologue", 90_000))))
        assertEquals(listOf(Chapter("Chapter 1", 2_000)), chapterList(listOf(Chapter("Chapter 1", 2_000))))
        assertEquals(emptyList<Chapter>(), chapterList(emptyList()))
    }

    @Test
    fun speech_after_a_long_pause_only() {
        val p = PauseFinder(pauseFrames = 3)
        assertFalse(p.speechStarts(-20f))
        repeat(2) { p.speechStarts(-60f) }
        assertFalse(p.speechStarts(-20f)) // two quiet frames isn't a pause
        repeat(3) { p.speechStarts(-60f) }
        assertTrue(p.speechStarts(-20f))
        assertFalse(p.speechStarts(-20f))
    }

    @Test
    fun loudness() {
        assertEquals(-120f, loudnessDb(0.0, 10))
        assertEquals(0f, loudnessDb(32768.0 * 32768.0 * 4, 4), 0.01f)
        assertEquals(-6.02f, loudnessDb(16384.0 * 16384.0, 1), 0.01f)
    }
}
