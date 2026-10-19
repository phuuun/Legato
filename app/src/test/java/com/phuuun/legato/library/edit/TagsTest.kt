package com.phuuun.legato.library.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {
    @Test
    fun music_joins_artists_with_semicolons() = assertEquals(
        mapOf("TITLE" to "Rindu", "ARTIST" to "Agnes Monica; Ahmad Dhani", "ALBUM" to ""),
        MusicEdit("Rindu", listOf("Agnes Monica", " Ahmad Dhani ", " "), "").properties(),
    )

    @Test
    fun single_file_book_writes_title_to_title_and_album() = assertEquals(
        mapOf("TITLE" to "Dune", "ALBUM" to "Dune", "ARTIST" to "Frank Herbert", "ALBUMARTIST" to "Frank Herbert", "COMPOSER" to "Scott Brick"),
        BookEdit("Dune", null, "Frank Herbert", "Scott Brick").properties(),
    )

    @Test
    fun chapter_keeps_its_own_title() = assertEquals("Ch 1", BookEdit("Dune", "Ch 1", "", "").properties()["TITLE"])

    @Test
    fun cover_long_side_is_capped_at_1000() {
        assertEquals(1000 to 750, coverSize(8000, 6000))
        assertEquals(400 to 1000, coverSize(1200, 3000))
        assertEquals(900 to 600, coverSize(900, 600)) // small images are never upscaled
    }
}
