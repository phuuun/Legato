package com.phuuun.legato

import android.app.Application
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.MediaStoreLibrary
import com.phuuun.legato.playback.PlayerConnection

class LegatoApp : Application() {
    val library: LibraryRepository by lazy { MediaStoreLibrary(this) }
    val player by lazy { PlayerConnection(this) }
}
