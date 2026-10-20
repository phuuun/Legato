package com.phuuun.legato.library.edit

import androidx.test.core.app.ApplicationProvider
import com.phuuun.legato.library.trackFromRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagWriterTest {
    @Test
    fun write_to_missing_file_is_false() = runTest {
        val track = trackFromRow(1, "T", "A", "X", 1, 1000, "x.mp3", path = "/nonexistent/x.mp3")
        assertFalse(TagWriter(ApplicationProvider.getApplicationContext()).write(track, mapOf("TITLE" to "New"), CoverChange.Keep))
    }

    @Test
    fun new_album_order_renumbers_only_what_moved_and_drops_the_disc() {
        val a = trackFromRow(1, "A", "X", "Al", 1, 1000, "a.mp3", trackNumber = 1)
        val b = trackFromRow(2, "B", "X", "Al", 1, 1000, "b.mp3", trackNumber = 2)
        val c = trackFromRow(3, "C", "X", "Al", 1, 1000, "c.mp3", trackNumber = 1003) // disc 1, track 3
        assertEquals(
            listOf(2L to mapOf("TRACKNUMBER" to "1", "DISCNUMBER" to ""), 1L to mapOf("TRACKNUMBER" to "2", "DISCNUMBER" to ""), 3L to mapOf("TRACKNUMBER" to "3", "DISCNUMBER" to "")),
            renumber(listOf(b, a, c)).map { (t, p) -> t.id to p },
        )
        assertEquals(emptyList<Any>(), renumber(listOf(a, b)))
    }
}
