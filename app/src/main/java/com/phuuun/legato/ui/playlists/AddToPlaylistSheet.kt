package com.phuuun.legato.ui.playlists

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.db.Playlist

/** Picks the playlist a long-pressed song or album goes into, or names a new one for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(playlists: List<Playlist>, onPick: (Playlist) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var naming by remember { mutableStateOf(false) }
    if (naming) { NameDialog("New playlist", "", "Create", onDone = onCreate, onDismiss = onDismiss); return }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text("ADD TO PLAYLIST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            item { PlaylistRow("New playlist", Icons.Rounded.Add) { naming = true } }
            items(playlists, key = { it.id }) { p -> PlaylistRow(p.name, Icons.AutoMirrored.Rounded.QueueMusic) { onPick(p); onDismiss() } }
        }
    }
}
