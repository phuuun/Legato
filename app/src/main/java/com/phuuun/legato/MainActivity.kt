package com.phuuun.legato

import android.content.Intent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.phuuun.legato.ui.LegatoNav
import com.phuuun.legato.ui.PermissionGate
import com.phuuun.legato.ui.theme.LegatoTheme

class MainActivity : ComponentActivity() {
    /** Text shared to Legato (a YouTube link, usually), until the converter picks it up. */
    private var shared by mutableStateOf<String?>(null)

    /** Set when the playback notification was tapped, until the full player opens. */
    private var openPlayer by mutableStateOf(false)

    private fun Intent.sharedText() = if (action == Intent.ACTION_SEND) getStringExtra(Intent.EXTRA_TEXT) else null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.sharedText()?.let { shared = it }
        if (intent.action == OPEN_PLAYER) openPlayer = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            shared = intent.sharedText()
            openPlayer = intent.action == OPEN_PLAYER
        }
        // Legato is dark only: light status and navigation bar icons, whatever the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val app = application as LegatoApp
        setContent {
            LegatoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionGate {
                        LegatoNav(
                            app.library, app.audiobooks, app.player, app.playlists, app.folders, app.edits, app.chapterfy, app.progress, app.bookmarks, app.backups, app.eq, app.downloads,
                            sharedLink = shared, onSharedLinkUsed = { shared = null },
                            openPlayer = openPlayer, onPlayerOpened = { openPlayer = false },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val OPEN_PLAYER = "com.phuuun.legato.OPEN_PLAYER"
    }
}
