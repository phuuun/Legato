package com.phuuun.legato

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.phuuun.legato.ui.LegatoNav
import com.phuuun.legato.ui.PermissionGate
import com.phuuun.legato.ui.theme.LegatoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LegatoApp
        setContent {
            LegatoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionGate {
                        LegatoNav(app.library, app.player, app.playlists, app.folders)
                    }
                }
            }
        }
    }
}
