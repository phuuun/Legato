package com.phuuun.legato.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BooksTest {
    private fun t(id: Long, title: String, album: String?, relativePath: String, trackNumber: Int = 0) =
        trackFromRow(id, title, "Frank Herbert", album, 1, 1000, "$id.mp3", trackNumber = trackNumber, relativePath = relativePath)

    @Test
    fun untagged_files_are_their_own_books() {
        // MediaStore names the album after the folder when a file has no album tag.
        val books = groupBooks(listOf(t(1, "Dune", "Audiobooks", "Audiobooks/"), t(2, "Emma", "Audiobooks", "Audiobooks/")))
        assertEquals(listOf("Dune", "Emma"), books.map { it.title })
    }

    @Test
    fun blank_album_is_its_own_book() = assertEquals("Dune", groupBooks(listOf(t(1, "Dune", null, "Audiobooks/"))).single().title)

    @Test
    fun files_sharing_a_book_title_form_one_book() {
        val books = groupBooks(listOf(t(1, "Ch 1", "Dune", "Audiobooks/"), t(2, "Ch 2", "Dune", "Audiobooks/"), t(3, "Emma", "Audiobooks", "Audiobooks/")))
        assertEquals(listOf("Dune", "Emma"), books.map { it.title })
        assertEquals(2, books.first().chapters.size)
        assertEquals("Frank Herbert", books.first().author)
    }

    @Test
    fun chapters_order_by_track_number_then_title() {
        val book = groupBooks(listOf(t(1, "B", "Dune", "A/", 2), t(2, "A", "Dune", "A/", 2), t(3, "Z", "Dune", "A/", 1))).single()
        assertEquals(listOf(3L, 2L, 1L), book.chapters.map { it.id })
    }
}
