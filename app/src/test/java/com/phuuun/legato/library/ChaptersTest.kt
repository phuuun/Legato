package com.phuuun.legato.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.RandomAccessFile

// Robolectric: Media3's ID3 decoder touches Android classes when it loads.
@RunWith(RobolectricTestRunner::class)
class ChaptersTest {
    // Made with ffmpeg: 6 s of silence, chapters at 0 s, 2 s and 4.5 s.
    private val expected = listOf(Chapter("Opening", 0), Chapter("Middle Part", 2_000), Chapter("The End", 4_500))

    private fun fixture(name: String) = RandomAccessFile(File(javaClass.classLoader!!.getResource("chapters/$name")!!.toURI()), "r")

    @Test
    fun m4b_chapter_track() = fixture("book.m4b").use { assertEquals(expected, mp4Chapters(it)) }

    // The same file with one of its two chapter sources renamed away, so each reader is tested on its own.
    @Test
    fun m4b_apple_chapter_track_alone() = fixture("apple-only.m4b").use { assertEquals(expected, mp4Chapters(it)) }

    @Test
    fun m4b_nero_chapters_alone() = fixture("nero-only.m4b").use { assertEquals(expected, mp4Chapters(it)) }

    @Test
    fun mp3_chap_frames() = fixture("book.mp3").use { assertEquals(expected, id3Chapters(it)) }

    @Test
    fun vorbis_chapter_tags() = assertEquals(
        expected,
        chaptersFromTags(mapOf(
            "CHAPTER001" to arrayOf("00:00:02.000"), "CHAPTER001NAME" to arrayOf("Middle Part"),
            "CHAPTER000" to arrayOf("00:00:00.000"), "CHAPTER000NAME" to arrayOf("Opening"),
            "CHAPTER002" to arrayOf("00:00:04.500"), "CHAPTER002NAME" to arrayOf("The End"),
            "TITLE" to arrayOf("Test Book"),
        )),
    )

    @Test
    fun description_timestamps() {
        val text = """
            The full audiobook. Narrated by someone, 2019.
            0:00 Intro
            12:30 - Chapter One: Arrival
            (1:02:03) Chapter Two
            Listen again at 5:00 for the best bit
            1:30:00 |
        """.trimIndent()
        assertEquals(
            listOf(Chapter("Intro", 0), Chapter("Chapter One: Arrival", 750_000), Chapter("Chapter Two", 3_723_000), Chapter("Chapter 4", 5_400_000)),
            chaptersFromText(text),
        )
    }

    @Test
    fun converter_comment_reads_back() {
        val written = expected.joinToString("\n") { "${formatTimestamp(it.startMs)} ${it.title}" }
        assertEquals(listOf(Chapter("Opening", 0), Chapter("Middle Part", 2_000), Chapter("The End", 4_000)), chaptersFromText(written))
    }

    @Test
    fun a_list_saved_in_legato_comes_first() = kotlinx.coroutines.test.runTest {
        assertEquals(listOf(Chapter("Prologue", 0), Chapter("Chapter 1", 65_000)), readChapters("/nonexistent.m4a", 0, "0:00:00 Prologue\n0:01:05 Chapter 1"))
        assertEquals(emptyList<Chapter>(), readChapters("/nonexistent.m4a", 99 * 60_000L, ""))
    }

    @Test
    fun long_files_without_chapters_get_parts() {
        assertEquals(emptyList<Chapter>(), autoParts(40 * 60_000L))
        assertEquals(listOf(Chapter("Part 1", 0), Chapter("Part 2", 1_800_000), Chapter("Part 3", 3_600_000)), autoParts(61 * 60_000L))
    }
}
