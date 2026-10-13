package com.phuuun.legato.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroupingTest {
    private fun row(id: Long, album: String?, albumId: Long, artist: String? = "A", title: String? = "T$id") =
        trackFromRow(id, title, artist, album, albumId, 1000, "file$id.flac")

    @Test
    fun same_album_name_different_ids_stay_separate() {
        val albums = groupAlbums(listOf(row(1, "Greatest Hits", 1), row(2, "Greatest Hits", 2)))
        assertEquals(2, albums.size)
    }

    @Test
    fun albums_sorted_by_title_case_insensitive() {
        val albums = groupAlbums(listOf(row(1, "banana", 1), row(2, "Apple", 2), row(3, "cherry", 3)))
        assertEquals(listOf("Apple", "banana", "cherry"), albums.map { it.title })
    }

    @Test
    fun blank_tags_get_unknown_labels() {
        val t = trackFromRow(7, "  ", null, "", 9, 1000, "My Song.flac")
        assertEquals("My Song", t.title)
        assertEquals("Unknown artist", t.artist)
        assertEquals("Unknown album", t.album)
    }

    @Test
    fun album_keeps_track_order_and_artists_group_albums() {
        val tracks = listOf(row(1, "X", 1, "Bee"), row(2, "X", 1, "Bee"), row(3, "Y", 2, "Ant"))
        val albums = groupAlbums(tracks)
        assertEquals(listOf(1L, 2L), albums.first { it.title == "X" }.tracks.map { it.id })
        assertEquals(listOf("Ant", "Bee"), groupArtists(albums).map { it.name })
    }
}
