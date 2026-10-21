package com.phuuun.legato.library

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressTest {
    private val min = 60_000L
    private val ch0 = trackFromRow(1, "Ch 1", "Author", "Book", 1, 10 * min, "1.mp3")
    private val ch1 = trackFromRow(2, "Ch 2", "Author", "Book", 1, 20 * min, "2.mp3")
    private val book = listOf(ch0, ch1)

    @Test
    fun no_spot_starts_at_the_beginning() = assertEquals(0 to 0L, resumePoint(book, emptyMap()))

    @Test
    fun resumes_latest_chapter_five_seconds_early() =
        assertEquals(1 to 15 * min - 5_000, resumePoint(book, mapOf(1L to Spot(50_000, 100), 2L to Spot(15 * min, 200))))

    @Test
    fun rewind_never_goes_below_zero() = assertEquals(0 to 0L, resumePoint(book, mapOf(1L to Spot(3_000, 100))))

    @Test
    fun finished_chapter_moves_to_the_next() = assertEquals(1 to 0L, resumePoint(book, mapOf(1L to Spot(10 * min, 100))))

    @Test
    fun continue_listening_picks_the_latest_unfinished_book() {
        val other = Book("Other", "A", null, listOf(trackFromRow(3, "Other", "A", "Other", 2, 10 * min, "3.mp3")))
        val main = Book("Book", "Author", null, book)
        // "Other" was heard more recently but is finished; nothing heard means nothing to offer.
        val spots = mapOf(1L to Spot(min, 100), 3L to Spot(10 * min, 300))
        assertEquals(main, lastBook(listOf(main, other), spots))
        assertEquals(null, lastBook(listOf(main, other), emptyMap()))
    }

    @Test
    fun finished_book_starts_over() = assertEquals(0 to 0L, resumePoint(book, mapOf(2L to Spot(20 * min, 100))))

    @Test
    fun progress_counts_earlier_chapters() =
        assertEquals(15 * min to 30 * min, bookProgress(book, mapOf(1L to Spot(2 * min, 100), 2L to Spot(5 * min, 200))))

    @Test
    fun time_left_reads_hours_and_minutes() {
        assertEquals("7h 42m left", formatLeft(7 * 60 * min + 42 * min))
        assertEquals("43m left", formatLeft(42 * min + 30_000)) // a started minute counts
        assertEquals("1m left", formatLeft(20_000))
    }

    @Test
    fun store_round_trips() = runTest {
        val store = ProgressStore(ApplicationProvider.getApplicationContext())
        store.save(7, positionMs = 1_234, now = 99)
        assertEquals(mapOf(7L to Spot(1_234, 99)), store.spots().first())
        store.move(7, 8)
        assertEquals(mapOf(8L to Spot(1_234, 99)), store.spots().first())
    }
}
