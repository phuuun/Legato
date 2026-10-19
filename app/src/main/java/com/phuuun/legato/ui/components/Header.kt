package com.phuuun.legato.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Every full screen's frame: clear of the status bar AND the camera cutout (on some phones the punch-hole
 * reaches below the status bar), the keyboard when it's up, plus room to breathe under the top edge.
 */
fun Modifier.screen(): Modifier = fillMaxSize().safeDrawingPadding().padding(top = 12.dp)

/** Top of every main screen: the app's name on the left, settings on the right. The bottom bar says which section this is. */
@Composable
fun LegatoHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Legato", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
    }
}
