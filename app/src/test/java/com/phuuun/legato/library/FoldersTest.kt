package com.phuuun.legato.library

import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoldersTest {
    @Test
    fun primary_tree_maps_to_relative_path() = assertEquals("Download/MUSIC/", relativePathFromTreeDocId("primary:Download/MUSIC"))

    @Test
    fun trailing_slash_is_not_doubled() = assertEquals("Music/", relativePathFromTreeDocId("primary:Music/"))

    @Test
    fun storage_root_and_sd_card_are_rejected() {
        assertNull(relativePathFromTreeDocId("primary:"))
        assertNull(relativePathFromTreeDocId("1A2B-3C4D:Music"))
    }

    @Test
    fun folders_select_exact_relative_paths() {
        val (sel, args) = audioSelection(setOf("Music/", "Download/MUSIC/"), WhenEmpty.AllMusic)!!
        assertEquals("${MediaStore.Audio.Media.RELATIVE_PATH} IN (?,?) AND ${MediaStore.Audio.Media.DURATION} > 0", sel)
        assertArrayEquals(arrayOf("Download/MUSIC/", "Music/"), args)
    }

    @Test
    fun no_folders_means_all_music_or_nothing() {
        assertEquals("${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0", audioSelection(emptySet(), WhenEmpty.AllMusic)!!.first)
        assertNull(audioSelection(emptySet(), WhenEmpty.Nothing))
    }

    @Test
    fun store_round_trips_per_kind() = runTest {
        val store = FolderStore(ApplicationProvider.getApplicationContext())
        store.add(FolderKind.Music, "Download/MUSIC/")
        store.add(FolderKind.Audiobooks, "Audiobooks/")
        store.remove(FolderKind.Music, "Download/MUSIC/")
        assertEquals(emptySet<String>(), store.folders(FolderKind.Music).first())
        assertEquals(setOf("Audiobooks/"), store.folders(FolderKind.Audiobooks).first())
    }

    @Test
    fun hidden_files_round_trip() = runTest {
        val store = FolderStore(ApplicationProvider.getApplicationContext())
        store.hide(listOf("/storage/emulated/0/Music/a.mp3", "/storage/emulated/0/Music/b.mp3"))
        store.unhide("/storage/emulated/0/Music/a.mp3")
        assertEquals(setOf("/storage/emulated/0/Music/b.mp3"), store.hidden.first())
    }

    @Test
    fun in_folders_matches_the_direct_parent_only() {
        val books = setOf("Audiobooks/")
        assertEquals(true, inFolders("/storage/emulated/0/Audiobooks/a.m4a", books))
        assertEquals(false, inFolders("/storage/emulated/0/Audiobooks/Dune/a.m4a", books))
        assertEquals(false, inFolders("/storage/emulated/0/MyAudiobooks/a.m4a", books))
    }
}
