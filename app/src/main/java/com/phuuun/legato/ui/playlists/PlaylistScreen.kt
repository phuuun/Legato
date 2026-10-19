package com.phuuun.legato.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phuuun.legato.ui.components.screen
import com.phuuun.legato.library.Track
import com.phuuun.legato.library.db.Playlist
import com.phuuun.legato.ui.components.occurrenceKeys
import com.phuuun.legato.ui.components.rememberReorder
import com.phuuun.legato.ui.library.Back
import com.phuuun.legato.ui.library.Empty
import com.phuuun.legato.ui.library.SongRow
import com.phuuun.legato.ui.library.TrackActions

private val RowHeight = 64.dp

@Composable
fun PlaylistScreen(
    playlist: Playlist?,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    actions: TrackActions,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemove: (position: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val reorder = rememberReorder(RowHeight, onMove)

    Column(Modifier.screen()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Back(onBack)
            Spacer(Modifier.weight(1f))
            if (playlist != null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = true })
                }
            }
        }
        if (playlist == null) { Empty("Playlist not found", "It may have been deleted."); return }
        Text(playlist.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        if (tracks.isEmpty()) Empty("No songs yet", "Long-press a song or an album and pick Add to playlist.")
        else LazyColumn(Modifier.fillMaxSize()) {
            val keys = occurrenceKeys(tracks) { it.id }
            itemsIndexed(tracks, key = { pos, _ -> keys[pos] }) { pos, t ->
                Row(
                    Modifier.animateItem(placementSpec = null).fillMaxWidth().height(RowHeight).then(reorder.row(pos)).background(MaterialTheme.colorScheme.background),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongRow(t, onClick = { onPlay(tracks, pos) }, actions = actions, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(pos) }) { Icon(Icons.Rounded.Close, "Remove from playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Rounded.DragHandle, "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp).then(reorder.handle(pos)).padding(12.dp))
                }
            }
        }
    }

    if (renaming && playlist != null) NameDialog("Rename playlist", playlist.name, "Save", onDone = onRename, onDismiss = { renaming = false })
    if (deleting && playlist != null) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete “${playlist.name}”?") },
        text = { Text("The songs stay on your phone.") },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
}
