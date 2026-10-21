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
    private val dir = File(context.cacheDir, "downloads").also { it.deleteRecursively(); it.mkdirs() }

    private fun backups(db: LegatoDb, paths: Map<Long, String>) =
        Backups(context, db, FolderStore(context), ProgressStore(context), BookmarkStore(context), EditStore(context), dir) { paths }

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
        EditStore(context).save("/b/book.m4b", mapOf("COMPOSER" to "Christie Moreau"), com.phuuun.legato.library.edit.CoverChange.Set(byteArrayOf(1, 2, 3)))

        val first = backups(db1, before)
        assertFalse(first.fromBefore)
        first.backUp()
        val file = first.file!!
        assertTrue(file.name.matches(Regex("Legato( Debug)? backup \\d{4}-\\d{2}-\\d{2}\\.json")))

        // A reinstall: app data wiped, same files, new MediaStore ids.
        context.getSharedPreferences("bookmarks", 0).edit().clear().commit()
        context.getSharedPreferences("progress", 0).edit().clear().commit()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        context.getSharedPreferences("backup", 0).edit().clear().commit()
        context.getSharedPreferences("edits", 0).edit().clear().commit()
        val db2 = db()
        val after = backups(db2, mapOf(11L to "/m/a.mp3", 12L to "/m/b.mp3", 13L to "/b/book.m4b"))
        assertTrue(after.fromBefore)
        after.daily() // must not overwrite the backup from before
        assertTrue(file.readText().contains("Road trip"))

        assertEquals(0, after.restore(backupFromJson(file.readText())))
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
        val edit = EditStore(context).all().getValue("/b/book.m4b")
        assertEquals("Christie Moreau", edit["COMPOSER"])
        assertEquals(listOf<Byte>(1, 2, 3), File(edit.getValue(COVER)).readBytes().toList())
        assertFalse(after.fromBefore)
        db1.close(); db2.close()
    }

    @Test
    fun songs_not_on_the_phone_are_counted_and_the_full_backup_stays() = runTest {
        val db1 = db()
        val playlist = db1.playlists().insert(com.phuuun.legato.library.db.Playlist(name = "Road trip", createdAt = 5))
        db1.playlists().edit(playlist) { it += listOf(1L, 2L) }
        val full = backups(db1, mapOf(1L to "/m/a.mp3", 2L to "/m/b.mp3"))
        full.backUp()
        // Made on an earlier day.
        val old = File(dir, full.file!!.name.replace(Regex("\\d{4}-\\d{2}-\\d{2}"), "2026-01-01"))
        full.file!!.renameTo(old)
        old.setLastModified(1_000)
        val fullText = old.readText()

        // A new phone with only one of the two songs copied over yet.
        val db2 = db()
        val partial = backups(db2, mapOf(11L to "/m/a.mp3"))
        assertEquals(1, partial.restore(backupFromJson(fullText)))
        partial.backUp()
        assertFalse(partial.file!!.readText().contains("/m/b.mp3"))
        assertEquals(fullText, old.readText())
        db1.close(); db2.close()
    }

    @Test
    fun only_the_newest_seven_stay() = runTest {
        val db = db()
        val b = backups(db, emptyMap())
        val name = b.name
        val olds = (1..9).map { d -> File(dir, "$name 2026-01-%02d.json".format(d)).apply { writeText("{}"); setLastModified(d * 1_000L) } }
        File(dir, "$name.json").writeText("{}") // an old undated one isn't Legato's to prune
        b.backUp()
        assertEquals(KEEP, dir.listFiles { f -> f.name.startsWith("$name 2") }!!.size)
        assertFalse(olds[2].exists())
        assertTrue(olds[3].exists())
        assertTrue(File(dir, "$name.json").exists())
        db.close()
    }

    @Test
    fun a_file_that_isnt_a_backup_is_refused() {
        val refused = runCatching { backupFromJson("""{"hello": 1}""") }
        assertTrue(refused.isFailure)
    }
}
