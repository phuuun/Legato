package com.phuuun.legato.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Album
import com.phuuun.legato.library.Artist
import com.phuuun.legato.library.Track
import com.phuuun.legato.ui.components.Artwork

/** What a long-press can do with a song or a whole album. `next` = true plays right after the current track, false appends. */
class TrackActions(val queue: (List<Track>, next: Boolean) -> Unit, val addToPlaylist: (List<Track>) -> Unit, val edit: (Track) -> Unit, val hide: (List<Track>) -> Unit)

@Composable
private fun TrackMenu(open: Boolean, onDismiss: () -> Unit, tracks: List<Track>, actions: TrackActions) =
    DropdownMenu(open, onDismiss) {
        DropdownMenuItem(text = { Text("Play next") }, onClick = { actions.queue(tracks, true); onDismiss() })
        DropdownMenuItem(text = { Text("Add to queue") }, onClick = { actions.queue(tracks, false); onDismiss() })
        DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = { actions.addToPlaylist(tracks); onDismiss() })
        if (tracks.size == 1) DropdownMenuItem(text = { Text("Edit info…") }, onClick = { actions.edit(tracks.single()); onDismiss() })
        DropdownMenuItem(text = { Text("Hide from library") }, onClick = { actions.hide(tracks); onDismiss() })
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(track: Track, onClick: () -> Unit, actions: TrackActions, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Artwork(track.artUri, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TrackMenu(menu, { menu = false }, listOf(track), actions)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, actions: TrackActions, modifier: Modifier = Modifier, below: @Composable () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }).padding(bottom = 8.dp)) {
        Artwork(album.artUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
        Text(album.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(album.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        below()
        TrackMenu(menu, { menu = false }, album.tracks, actions)
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Artwork((artist.albums + artist.appearsOn).firstOrNull()?.artUri ?: artist.songs.firstOrNull()?.artUri, Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    artist.albums.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "album" else "albums"}" },
                    artist.appearsOn.size.takeIf { it > 0 }?.let { "appears on $it" },
                    artist.songs.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "song" else "songs"}" },
                ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
