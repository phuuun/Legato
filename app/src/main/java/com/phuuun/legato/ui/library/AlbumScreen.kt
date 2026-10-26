package com.phuuun.legato.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phuuun.legato.ui.components.screen
import com.phuuun.legato.library.Album
import com.phuuun.legato.library.Artist
import com.phuuun.legato.library.Track
import com.phuuun.legato.ui.components.Artwork
import com.phuuun.legato.ui.components.rememberReorder

@Composable
fun Back(onBack: () -> Unit) =
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }

/** Play from the top, or the same tracks shuffled. */
@Composable
fun PlayShuffle(tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit, modifier: Modifier = Modifier) {
    if (tracks.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onPlay(tracks, 0) }) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Play")
        }
        OutlinedButton(onClick = { onPlay(tracks.shuffled(), 0) }) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Shuffle")
        }
    }
}

private val RowHeight = 64.dp

/** [onReorder] set: an Edit order button, drag the grips, Save hands back the new order. */
@Composable
fun AlbumScreen(album: Album?, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit, actions: TrackActions, onReorder: ((List<Track>) -> Unit)? = null, header: @Composable () -> Unit = {}) {
    var editing by remember { mutableStateOf(false) }
    val order = remember { mutableStateListOf<Track>() }
    val reorder = rememberReorder(RowHeight) { from, to -> order.add(to.coerceIn(0, order.lastIndex), order.removeAt(from)) }
    Column(Modifier.screen()) {
        Back(onBack)
        if (album == null) { Empty("Album not found", "It may have been deleted."); return }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Artwork(album.artUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                    Text(album.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                    Text(album.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    header()
                    if (onReorder != null) Row(Modifier.padding(top = 4.dp)) {
                        if (!editing) TextButton(onClick = { order.clear(); order += album.tracks; editing = true }) { Text("Edit order") }
                        else {
                            TextButton(onClick = { onReorder(order.toList()); editing = false }) { Text("Save") }
                            TextButton(onClick = { editing = false }) { Text("Cancel") }
                        }
                    }
                }
            }
            if (editing) itemsIndexed(order, key = { _, t -> t.id }) { pos, t ->
                Row(
                    Modifier.fillMaxWidth().height(RowHeight).then(reorder.row(pos)).background(MaterialTheme.colorScheme.background),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongRow(t, onClick = {}, actions = actions, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.DragHandle, "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp).then(reorder.handle(pos)).padding(12.dp))
                }
            }
            else items(album.tracks, key = { it.id }) { t -> SongRow(t, onClick = { onPlay(album.tracks, album.tracks.indexOf(t)) }, actions = actions) }
        }
    }
}

@Composable
fun ArtistScreen(artist: Artist?, onBack: () -> Unit, onAlbum: (Long) -> Unit, onPlay: (List<Track>, Int) -> Unit, actions: TrackActions) {
    Column(Modifier.screen()) {
        Back(onBack)
        if (artist == null) { Empty("Artist not found", "It may have been deleted."); return }
        Text(artist.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
        PlayShuffle(artist.tracks, onPlay, Modifier.padding(start = 20.dp, top = 12.dp))
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Section titles only when there's more than their own albums: an artist with just those keeps a plain grid.
            val titled = artist.appearsOn.isNotEmpty() || artist.songs.isNotEmpty()
            if (titled && artist.albums.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Section("ALBUMS") }
            items(artist.albums, key = { it.id }) { a -> AlbumCard(a, onClick = { onAlbum(a.id) }, actions = actions) }
            if (artist.appearsOn.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Section("APPEARS ON") }
            items(artist.appearsOn, key = { "on" + it.id }) { a -> AlbumCard(a, onClick = { onAlbum(a.id) }, actions = actions) }
            if (artist.songs.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Section("SONGS") }
            items(artist.songs, key = { "s" + it.id }, span = { GridItemSpan(maxLineSpan) }) { t ->
                SongRow(t, onClick = { onPlay(artist.songs, artist.songs.indexOf(t)) }, actions = actions, modifier = Modifier.bleed(20.dp))
            }
        }
    }
}

/** Out past the grid's padding on both sides, so a song row lines up as on every other screen. */
private fun Modifier.bleed(by: Dp) = layout { m, c ->
    val extra = (by * 2).roundToPx()
    val p = m.measure(c.copy(minWidth = c.minWidth + extra, maxWidth = c.maxWidth + extra))
    layout(p.width - extra, p.height) { p.place(-extra / 2, 0) }
}

@Composable
private fun Section(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
