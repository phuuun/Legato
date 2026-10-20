package com.phuuun.legato.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.phuuun.legato.library.db.LegatoDb
import com.phuuun.legato.library.db.PlayStat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {
    private val onDisk = listOf(1L, 2L, 3L).map { trackFromRow(it, "T$it", "Artist", "Album", 10, 1000, "$it.flac") }
    private val library = object : LibraryRepository {
        override val tracks = flowOf(onDisk)
        override suspend fun track(id: Long) = onDisk.firstOrNull { it.id == id }
    }
    private lateinit var db: LegatoDb
    private lateinit var repo: PlaylistRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LegatoDb::class.java).allowMainThreadQueries().build()
        repo = PlaylistRepository(db, library)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ids(playlist: Long) = repo.tracksOf(playlist).first().map { it.id }

    /** Raw stored positions, read straight from the table so the test sees what a later load would. */
    private fun positions(playlist: Long): List<Int> =
        db.openHelper.readableDatabase.query("SELECT position FROM playlist_tracks WHERE playlistId = ? ORDER BY position", arrayOf<Any>(playlist))
            .use { c -> List(c.count) { c.moveToPosition(it); c.getInt(0) } }

    @Test
    fun add_then_read_returns_tracks_in_order() = runTest {
        val p = repo.create("Mix")
        repo.addTracks(p, listOf(3, 1))
        repo.addTracks(p, listOf(2))
        assertEquals(listOf(3L, 1L, 2L), ids(p))
    }

    @Test
    fun move_reorders_and_keeps_positions_contiguous() = runTest {
        val p = repo.create("Mix")
        repo.addTracks(p, listOf(1, 2, 3))
        repo.move(p, 0, 2)
        assertEquals(listOf(2L, 3L, 1L), ids(p))
        assertEquals(listOf(0, 1, 2), positions(p))
    }

    @Test
    fun removeAt_closes_the_gap() = runTest {
        val p = repo.create("Mix")
        repo.addTracks(p, listOf(1, 2, 3))
        repo.removeAt(p, 1)
        assertEquals(listOf(1L, 3L), ids(p))
        assertEquals(listOf(0, 1), positions(p))
    }

    @Test
    fun deleted_file_is_skipped_on_load() = runTest {
        val p = repo.create("Mix")
        repo.addTracks(p, listOf(1, 99, 2))
        assertEquals(listOf(1L, 2L), ids(p))
    }

    @Test
    fun write_after_deleted_file_uses_the_positions_on_screen() = runTest {
        val p = repo.create("Mix")
        repo.addTracks(p, listOf(1, 99, 2, 3))
        // On screen: [1, 2, 3]. Removing position 1 must remove track 2, not the missing 99.
        repo.removeAt(p, 1)
        assertEquals(listOf(1L, 3L), ids(p))
        assertEquals(listOf(0, 1), positions(p))
    }

    @Test
    fun delete_playlist_removes_its_tracks() = runTest {
        val p = repo.create("Mix")
        repo.addTracks(p, listOf(1, 2))
        repo.delete(p)
        assertTrue(repo.playlists.first().isEmpty())
        assertEquals(emptyList<Int>(), positions(p))
    }

    @Test
    fun recordPlay_increments_count_and_sets_lastPlayedAt() = runTest {
        repo.recordPlay(1, now = 100)
        repo.recordPlay(1, now = 200)
        assertEquals(PlayStat(1, 2, 200), db.stats().get(1))
    }

    @Test
    fun a_replaced_file_keeps_its_playlist_spots_and_plays() = runTest {
        val p = repo.create("Mix")
        repo.addTracks(p, listOf(1, 2, 1))
        db.stats().recordPlay(1, 100)
        repo.moveTrack(1, 3)
        assertEquals(listOf(3L, 2L, 3L), ids(p))
        assertEquals(PlayStat(3, 1, 100), db.stats().get(3))
        assertEquals(null, db.stats().get(1))
    }
}
