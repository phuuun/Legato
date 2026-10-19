package com.phuuun.legato

import android.app.Application
import com.phuuun.legato.library.FolderKind
import com.phuuun.legato.library.FolderStore
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.MediaStoreLibrary
import com.phuuun.legato.library.PlaylistRepository
import com.phuuun.legato.library.WhenEmpty
import com.phuuun.legato.library.db.LegatoDb
import com.phuuun.legato.playback.PlayerConnection
import kotlinx.coroutines.flow.flowOf

class LegatoApp : Application() {
    val folders by lazy { FolderStore(this) }
    val library: LibraryRepository by lazy { MediaStoreLibrary(this, folders.folders(FolderKind.Music), WhenEmpty.AllMusic) }
    val audiobooks: LibraryRepository by lazy { MediaStoreLibrary(this, folders.folders(FolderKind.Audiobooks), WhenEmpty.Nothing) }
    val player by lazy { PlayerConnection(this) }

    // Playlists resolve against all music, not the chosen folders: removing a folder must never prune a playlist.
    private val allMusic: LibraryRepository by lazy { MediaStoreLibrary(this, flowOf(emptySet()), WhenEmpty.AllMusic) }
    val playlists by lazy { PlaylistRepository(LegatoDb.open(this), allMusic) }
}
