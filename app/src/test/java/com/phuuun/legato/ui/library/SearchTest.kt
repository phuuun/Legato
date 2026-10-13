package com.phuuun.legato.ui.library

import com.phuuun.legato.library.trackFromRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchTest {
    private val tracks = listOf(
        trackFromRow(1, "Halo", "Beyoncé", "I Am... Sasha Fierce", 10, 1000, "a.flac"),
        trackFromRow(2, "Creep", "Radiohead", "Pablo Honey", 20, 1000, "b.flac"),
    )

    @Test
    fun blank_query_returns_nothing() {
        val r = search("  ", tracks)
        assertTrue(r.tracks.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty())
    }

    @Test
    fun matches_title_artist_and_album_case_insensitively() {
        assertEquals(listOf(1L), search("HALO", tracks).tracks.map { it.id })
        assertEquals(listOf("Radiohead"), search("radio", tracks).artists.map { it.name })
        assertEquals(listOf("Pablo Honey"), search("pablo", tracks).albums.map { it.title })
    }

    @Test
    fun non_ascii_titles_match() {
        assertEquals(listOf(1L), search("beyoncé", tracks).tracks.map { it.id })
        assertEquals(listOf(1L), search("BEYONCÉ", tracks).tracks.map { it.id })
    }
}
