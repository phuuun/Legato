package com.phuuun.legato.library

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarksTest {
    @Test
    fun round_trips_captions_with_colons_and_blank_ones() = runTest {
        val store = BookmarkStore(ApplicationProvider.getApplicationContext())
        val a = Bookmark(1, trackId = 7, positionMs = 61_000, color = 2, caption = "Quote: \"10:30 sharp\"")
        val b = Bookmark(2, trackId = 7, positionMs = 5_000, color = 0, caption = "")
        store.save(a)
        store.save(b)
        store.save(a.copy(color = 4))
        store.delete(2)
        assertEquals(listOf(a.copy(color = 4)), store.bookmarks.first())
    }
}
