package com.phuuun.legato

import android.app.Application
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.MediaStoreLibrary

class LegatoApp : Application() {
    val library: LibraryRepository by lazy { MediaStoreLibrary(this) }
}
