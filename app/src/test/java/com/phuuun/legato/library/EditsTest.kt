package com.phuuun.legato.library

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.phuuun.legato.library.edit.CoverChange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EditsTest {
    private fun row(edit: Map<String, String>, album: String? = "Al") =
        trackFromRow(1, "T", "A", album, 7, 1000, "t.mp3", trackNumber = 1003, relativePath = "Music/Folder/", composer = "N", edit = edit)

    @Test
    fun an_edit_goes_over_the_file_tags() {
        val t = row(mapOf("TITLE" to "New", "ARTIST" to "X; Y", "COMPOSER" to "Narrator", "TRACKNUMBER" to "2"))
        assertEquals("New", t.title)
        assertEquals(listOf("X", "Y"), t.artists)
        assertEquals("Narrator", t.composer)
        assertEquals(2, t.trackNumber)
        assertEquals(7, t.albumId)
    }

    @Test
    fun a_renamed_album_leaves_its_old_group_and_an_unchanged_one_stays() {
        assertEquals(7, row(mapOf("ALBUM" to "Al")).albumId)
        assertEquals(7, row(mapOf("ALBUM" to ""), album = "Folder").albumId) // untagged: MediaStore's album is the folder
        val renamed = row(mapOf("ALBUM" to "Other"))
        assertEquals("Other", renamed.album)
        assertNotEquals(7, renamed.albumId)
        assertEquals(renamed.albumId, row(mapOf("ALBUM" to "other")).albumId)
    }

    @Test
    fun covers() {
        assertEquals("content", row(emptyMap()).artUri?.scheme)
        assertNull(row(mapOf(COVER to "")).artUri)
        assertEquals(Uri.fromFile(File("/c/x.jpg")), row(mapOf(COVER to "/c/x.jpg")).artUri)
    }

    @Test
    fun saves_merge_and_a_replaced_cover_file_goes() = runTest {
        val store = EditStore(ApplicationProvider.getApplicationContext<Context>())
        store.save("/m/a.mp3", mapOf("TITLE" to "One"), CoverChange.Set(byteArrayOf(1)))
        val first = store.all().getValue("/m/a.mp3").getValue(COVER)
        store.save("/m/a.mp3", mapOf("ARTIST" to "Two"), CoverChange.Set(byteArrayOf(2)))
        val edit = store.all().getValue("/m/a.mp3")
        assertEquals("One", edit["TITLE"])
        assertEquals("Two", edit["ARTIST"])
        assertFalse(File(first).exists())
        store.save("/m/a.mp3", emptyMap(), CoverChange.Remove)
        assertFalse(File(edit.getValue(COVER)).exists())
        assertEquals("", store.all().getValue("/m/a.mp3")[COVER])
    }
}
