package com.phuuun.legato

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.phuuun.legato.converter.DownloadQueue
import com.phuuun.legato.library.Backups
import com.phuuun.legato.library.EditStore
import com.phuuun.legato.library.BookmarkStore
import com.phuuun.legato.library.FolderKind
import com.phuuun.legato.library.FolderStore
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.MediaStoreLibrary
import com.phuuun.legato.library.PlaylistRepository
import com.phuuun.legato.library.ProgressStore
import com.phuuun.legato.library.WhenEmpty
import com.phuuun.legato.library.db.LegatoDb
import com.phuuun.legato.library.edit.TagWriter
import com.phuuun.legato.playback.EqStore
import com.phuuun.legato.playback.PlayerConnection
import com.phuuun.legato.ui.components.AudioCoverFetcher
import com.phuuun.legato.ui.components.AudioCoverKeyer
import kotlinx.coroutines.flow.flowOf

class LegatoApp : Application(), SingletonImageLoader.Factory {
    val folders by lazy { FolderStore(this) }
    val edits by lazy { EditStore(this) }
    val library: LibraryRepository by lazy { MediaStoreLibrary(this, folders.folders(FolderKind.Music), WhenEmpty.AllMusic, folders.hidden, edits.edits) }
    val audiobooks: LibraryRepository by lazy { MediaStoreLibrary(this, folders.folders(FolderKind.Audiobooks), WhenEmpty.Nothing, folders.hidden, edits.edits) }
    val player by lazy { PlayerConnection(this) }
    val tagWriter by lazy { TagWriter(this) }
    val progress by lazy { ProgressStore(this) }
    val bookmarks by lazy { BookmarkStore(this) }
    val eq by lazy { EqStore(this) }
    val downloads = DownloadQueue()

    // Playlists resolve against all music, not the chosen folders or hidden files: neither may prune a playlist.
    private val allMusic: LibraryRepository by lazy { MediaStoreLibrary(this, flowOf(emptySet()), WhenEmpty.AllMusic, edits = edits.edits) }
    private val db by lazy { LegatoDb.open(this) }
    val playlists by lazy { PlaylistRepository(db, allMusic) }
    val backups by lazy { Backups(this, db, folders, progress, bookmarks, edits) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(AudioCoverFetcher.Factory()); add(AudioCoverKeyer()) }.build()
}
