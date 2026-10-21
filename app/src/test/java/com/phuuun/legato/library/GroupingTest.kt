package com.phuuun.legato.library

import com.phuuun.legato.library.db.PlayStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun most_and_recently_played_rank_songs_and_albums_with_unplayed_last_a_to_z() {
        val songs = listOf(row(1, "X", 1, title = "b"), row(2, "X", 1, title = "a"), row(3, "Y", 2, title = "c"), row(4, "Z", 3, title = "d"))
        val stats = listOf(PlayStat(1, 5, 100), PlayStat(3, 2, 900), PlayStat(4, 6, 50)).associateBy { it.mediaStoreId }
        assertEquals(listOf(4L, 1L, 3L, 2L), songs.sortedFor(Sort.Plays, stats).map { it.id })
        assertEquals(listOf(3L, 1L, 4L, 2L), songs.sortedFor(Sort.Played, stats).map { it.id })
        // Album X: 5 plays in all, last at 100.
        val albums = groupAlbums(songs)
        assertEquals(listOf("Z", "X", "Y"), albums.sortedFor(Sort.Plays, stats).map { it.title })
        assertEquals(listOf("Y", "X", "Z"), albums.sortedFor(Sort.Played, stats).map { it.title })
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
    fun commas_split_artists_too() {
        val artists = groupArtists(groupAlbums(listOf(row(1, "LOVE.", 1, artist = "Kendrick Lamar, Zacari"), row(2, "DNA.", 1, artist = "Kendrick Lamar"))))
        assertEquals(listOf("Kendrick Lamar", "Zacari"), artists.map { it.name })
        assertEquals("Kendrick Lamar, Zacari", row(1, "LOVE.", 1, artist = "Kendrick Lamar,Zacari").artist)
    }

    @Test
    fun a_featured_artist_gets_the_album_under_appears_on() {
        val damn = listOf(row(1, "DAMN.", 1, artist = "Kendrick Lamar"), row(2, "DAMN.", 1, artist = "Kendrick Lamar; Rihanna"), row(3, "DAMN.", 1, artist = "Kendrick Lamar, Zacari"))
        val anti = listOf(row(4, "ANTI", 2, artist = "Rihanna"))
        val albums = groupAlbums(damn + anti)
        assertEquals("Kendrick Lamar", albums.single { it.title == "DAMN." }.artist)
        val rihanna = groupArtists(albums).single { it.name == "Rihanna" }
        assertEquals(listOf("ANTI"), rihanna.albums.map { it.title })
        assertEquals(listOf("DAMN."), rihanna.appearsOn.map { it.title })
        // Playing Rihanna plays her own album and only her song on DAMN.
        assertEquals(listOf(4L, 2L), rihanna.tracks.map { it.id })
        assertEquals(listOf("DAMN."), groupArtists(albums).single { it.name == "Kendrick Lamar" }.albums.map { it.title })
    }

    @Test
    fun a_single_belongs_to_its_first_artist() {
        val artists = groupArtists(groupAlbums(listOf(row(1, "Best Part", 1, artist = "Daniel Caesar; H.E.R."))))
        assertEquals(listOf("Best Part"), artists.single { it.name == "Daniel Caesar" }.albums.map { it.title })
        assertEquals(listOf("Best Part"), artists.single { it.name == "H.E.R." }.appearsOn.map { it.title })
    }

    @Test
    fun the_album_artist_tag_decides_and_can_name_two() {
        val tracks = listOf("JAY-Z", "Kanye West; JAY-Z").mapIndexed { i, a ->
            trackFromRow(i + 1L, "T$i", a, "Watch the Throne", 1, 1000, "$i.flac", albumArtist = "JAY-Z; Kanye West")
        }
        val artists = groupArtists(groupAlbums(tracks))
        assertEquals(listOf("Watch the Throne"), artists.single { it.name == "Kanye West" }.albums.map { it.title })
        assertEquals(listOf("Watch the Throne"), artists.single { it.name == "JAY-Z" }.albums.map { it.title })
        assertTrue(artists.all { it.appearsOn.isEmpty() })
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
