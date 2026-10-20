package com.phuuun.legato.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun songs_sort_by_title_artist_or_newest() {
        val songs = listOf(
            trackFromRow(1, "beta", "Zed", "X", 1, 1000, "1.mp3", added = 300),
            trackFromRow(2, "Alpha", "amy", "X", 1, 1000, "2.mp3", added = 100),
            trackFromRow(3, "Gamma", "Bob", "X", 1, 1000, "3.mp3", added = 200),
        )
        assertEquals(listOf(2L, 1L, 3L), songs.sortedFor(Sort.Title).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), songs.sortedFor(Sort.Artist).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), songs.sortedFor(Sort.Recent).map { it.id })
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

    @Test
    fun artist_tag_splits_on_semicolon() {
        val t = row(1, "X", 1, artist = "Daniel Caesar; H.E.R.")
        assertEquals(listOf("Daniel Caesar", "H.E.R."), t.artists)
        assertEquals("Daniel Caesar, H.E.R.", t.artist)
    }

    @Test
    fun album_with_two_artists_appears_under_both() {
        val artists = groupArtists(groupAlbums(listOf(row(1, "X", 1, artist = "Daniel Caesar; H.E.R."), row(2, "Y", 2, artist = "H.E.R."))))
        assertEquals(listOf("Daniel Caesar", "H.E.R."), artists.map { it.name })
        assertEquals(listOf("X", "Y"), artists.single { it.name == "H.E.R." }.albums.map { it.title })
    }

    @Test
    fun unknown_artist_keeps_its_own_entry() {
        val artists = groupArtists(groupAlbums(listOf(row(1, "X", 1, artist = null))))
        assertEquals(listOf("Unknown artist"), artists.map { it.name })
    }

    @Test
    fun cover_uri_is_the_track_itself_and_changes_with_the_file() {
        val a = trackFromRow(5, "T", "A", "X", 1, 1000, "a.mp3", modified = 100)
        val b = trackFromRow(5, "T", "A", "X", 1, 1000, "a.mp3", modified = 200)
        assertEquals(a.uri, a.artUri!!.buildUpon().clearQuery().build())
        assertNotEquals(a.artUri, b.artUri)
    }
}
