package com.phuuun.legato.library.edit

import com.phuuun.legato.library.trackFromRow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun square_crop_takes_the_centre() {
        assertEquals(Crop(280, 0, 720), squareCrop(1280, 720))
        assertEquals(Crop(0, 60, 480), squareCrop(480, 600))
        assertEquals(Crop(0, 0, 500), squareCrop(500, 500))
    }

    @Test
    fun new_album_order_renumbers_only_what_moved() {
        val a = trackFromRow(1, "A", "X", "Al", 1, 1000, "a.mp3", trackNumber = 1)
        val b = trackFromRow(2, "B", "X", "Al", 1, 1000, "b.mp3", trackNumber = 2)
        val c = trackFromRow(3, "C", "X", "Al", 1, 1000, "c.mp3", trackNumber = 1003) // disc 1, track 3
        assertEquals(
            listOf(2L to mapOf("TRACKNUMBER" to "1"), 1L to mapOf("TRACKNUMBER" to "2"), 3L to mapOf("TRACKNUMBER" to "3")),
            renumber(listOf(b, a, c)).map { (t, p) -> t.id to p },
        )
        assertEquals(emptyList<Any>(), renumber(listOf(a, b)))
    }
}
