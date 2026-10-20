package com.phuuun.legato.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.phuuun.legato.library.db.LegatoDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.cacheDir, "Legato backup.json").also { it.delete() }

    private fun backups(db: LegatoDb, paths: Map<Long, String>) =
        Backups(context, db, FolderStore(context), ProgressStore(context), BookmarkStore(context), file) { paths }

    private fun db() = Room.inMemoryDatabaseBuilder(context, LegatoDb::class.java).allowMainThreadQueries().build()

    @Test
    fun everything_survives_a_wipe_and_new_mediastore_ids() = runTest {
        val before = mapOf(1L to "/m/a.mp3", 2L to "/m/b.mp3", 3L to "/b/book.m4b")
        val db1 = db()
        val folders = FolderStore(context)
        val playlist = db1.playlists().insert(com.phuuun.legato.library.db.Playlist(name = "Road trip", createdAt = 5))
        db1.playlists().edit(playlist) { it += listOf(2L, 1L, 2L) }
        db1.stats().recordPlay(1, 77)
        BookmarkStore(context).save(Bookmark(9, 3, 61_000, 4, "the twist"))
        ProgressStore(context).save(3, 120_000, 88)
        folders.hide(listOf("/m/b.mp3"))
        folders.add(FolderKind.Audiobooks, "Books/")
        folders.setSort(Sort.Recent)

        val first = backups(db1, before)
        assertFalse(first.fromBefore)
        first.backUp()
        assertTrue(file.exists())

        // A reinstall: app data wiped, same files, new MediaStore ids.
        context.getSharedPreferences("bookmarks", 0).edit().clear().commit()
        context.getSharedPreferences("progress", 0).edit().clear().commit()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        context.getSharedPreferences("backup", 0).edit().clear().commit()
        val db2 = db()
        val after = backups(db2, mapOf(11L to "/m/a.mp3", 12L to "/m/b.mp3", 13L to "/b/book.m4b"))
        assertTrue(after.fromBefore)
        after.daily() // must not overwrite the backup from before
        assertTrue(file.readText().contains("Road trip"))

        after.restore(backupFromJson(file.readText()))
        val restored = db2.playlists().playlists().first().single()
        assertEquals("Road trip", restored.name)
        assertEquals(listOf(12L, 11L, 12L), db2.playlists().trackIds(restored.id).first())
        assertEquals(1, db2.stats().get(11)?.playCount)
        assertEquals(listOf(13L to 61_000L), BookmarkStore(context).bookmarks.first().map { it.trackId to it.positionMs })
        assertEquals("the twist", BookmarkStore(context).bookmarks.first().single().caption)
        assertEquals(Spot(120_000, 88), ProgressStore(context).spots().first()[13L])
        assertEquals(setOf("/m/b.mp3"), FolderStore(context).hidden.first())
        assertEquals(setOf("Books/"), FolderStore(context).folders(FolderKind.Audiobooks).first())
        assertEquals(Sort.Recent, FolderStore(context).sort.first())
        assertFalse(after.fromBefore)
        db1.close(); db2.close()
    }

    @Test
    fun a_file_that_isnt_a_backup_is_refused() {
        val refused = runCatching { backupFromJson("""{"hello": 1}""") }
        assertTrue(refused.isFailure)
    }
}
