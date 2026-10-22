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

    // How Whisper writes them.
    @Test
    fun headings_as_whisper_writes_them() {
        assertEquals("Chapter 23", spokenChapter(" Chapter 23."))
        assertEquals("Chapter 23", spokenChapter(" Chapter Twenty-Three."))
        assertEquals("Chapter 1", spokenChapter("CHAPTER ONE"))
        assertEquals("Chapter 105", spokenChapter("Chapter one hundred and five. Avery"))
        assertEquals("Prologue", spokenChapter("Prologue."))
        assertEquals("Part 2", spokenChapter("Part 2:"))
        assertEquals("Chapter", spokenChapter("Chapter."))
        assertNull(spokenChapter("Chapters of my life were..."))
        assertNull(spokenChapter(" I said, chapter two."))
        // tiny.en on The Inheritance Games
        assertEquals("Chapter 2", spokenChapter(" Chapter II. Rough day Libby."))
        assertEquals("Chapter 14", spokenChapter("Chapter XIV"))
        assertEquals("Part 4", spokenChapter("Part IV: The Will"))
        assertNull(spokenChapter("Part I think it was"))
    }

    @Test
    fun quick_listen_lets_only_headings_through() {
        assertTrue(startsLikeHeading(" chapter two chapter two"))
        assertTrue(startsLikeHeading("Prologue."))
        assertTrue(startsLikeHeading("Part"))
        assertFalse(startsLikeHeading(" Rough day, Libby asked."))
        assertFalse(startsLikeHeading(""))
        assertEquals(49, romanNumber("XLIX"))
        assertNull(romanNumber("mix"))
    }

    @Test
    fun audio_is_averaged_down_to_16k() {
        val pcm = ShortArray(441) { if (it % 2 == 0) 16384 else -16384 } // 10 ms at 44.1 kHz, loudest possible hiss
        val out = to16k(pcm, pcm.size, 44_100)
        assertEquals(160, out.size)
        assertTrue(out.all { kotlin.math.abs(it) < 0.25f }) // averaged away, not aliased into the speech band at full volume
        assertEquals(0.5f, to16k(ShortArray(48) { 16384 }, 48, 48_000)[0], 1e-6f)
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
    fun a_missed_chapter_stays_a_gap_and_nothing_is_renumbered() {
        val heard = listOf(Chapter("Chapter 1", 2_000), Chapter("Chapter 3", 900_000), Chapter("Chapter 4", 1_500_000))
        assertEquals(listOf("Chapter 1", "Chapter 3", "Chapter 4"), chapterList(heard).map { it.title })
        // Saved and read back, too.
        assertEquals(listOf("Chapter 1", "Chapter 3", "Chapter 4"), chaptersFromText(chapterList(heard).asText()).map { it.title })
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
