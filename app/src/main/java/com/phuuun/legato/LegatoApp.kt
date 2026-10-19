package com.phuuun.legato

import android.app.Application
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.MediaStoreLibrary
import com.phuuun.legato.library.PlaylistRepository
import com.phuuun.legato.library.db.LegatoDb
import com.phuuun.legato.playback.PlayerConnection

class LegatoApp : Application() {
    val library: LibraryRepository by lazy { MediaStoreLibrary(this) }
    val player by lazy { PlayerConnection(this) }
    val playlists by lazy { PlaylistRepository(LegatoDb.open(this), library) }
}
