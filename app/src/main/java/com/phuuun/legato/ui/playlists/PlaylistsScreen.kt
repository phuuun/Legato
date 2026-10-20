package com.phuuun.legato.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Track
import com.phuuun.legato.library.db.Playlist
import com.phuuun.legato.ui.components.screen
import com.phuuun.legato.ui.library.Back
import com.phuuun.legato.ui.library.Empty
import com.phuuun.legato.ui.library.PlayShuffle
import com.phuuun.legato.ui.library.SongRow
import com.phuuun.legato.ui.library.TrackActions

/** Playlists Legato keeps itself, from play counts and dates added. */
enum class SmartList(val label: String, val icon: ImageVector, val empty: String) {
    MostPlayed("Most played", Icons.AutoMirrored.Rounded.TrendingUp, "Songs you play to the end show up here."),
    RecentlyPlayed("Recently played", Icons.Rounded.History, "Songs you play to the end show up here."),
    RecentlyAdded("Recently added", Icons.Rounded.NewReleases, "No music yet."),
}

/** The Playlists tab in the library: the smart lists, a "New playlist" row, then every playlist, oldest first. */
@Composable
fun PlaylistsList(playlists: List<Playlist>, onOpen: (Long) -> Unit, onSmart: (SmartList) -> Unit, onCreate: (String) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(SmartList.entries, key = { it.name }) { s -> PlaylistRow(s.label, s.icon) { onSmart(s) } }
        item { PlaylistRow("New playlist", Icons.Rounded.Add) { naming = true } }
        items(playlists, key = { it.id }) { p -> PlaylistRow(p.name, Icons.AutoMirrored.Rounded.QueueMusic) { onOpen(p.id) } }
    }
    if (naming) NameDialog("New playlist", "", "Create", onDone = onCreate, onDismiss = { naming = false })
}

@Composable
fun PlaylistRow(name: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A smart list: read-only, at most 100 songs, with Play and Shuffle. */
@Composable
fun SmartPlaylistScreen(list: SmartList, tracks: List<Track>, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit, actions: TrackActions) {
    Column(Modifier.screen()) {
        Back(onBack)
        Text(list.label, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        PlayShuffle(tracks, onPlay, Modifier.padding(start = 20.dp, bottom = 8.dp))
        if (tracks.isEmpty()) Empty(list.label, list.empty)
        else LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t -> SongRow(t, onClick = { onPlay(tracks, i) }, actions = actions) }
        }
    }
}

/** Asks for a playlist name; used for create and rename. Blank names can't be confirmed. */
@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") }, modifier = Modifier.focusRequester(focus)) },
        confirmButton = { TextButton(onClick = { onDone(name.trim()); onDismiss() }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
