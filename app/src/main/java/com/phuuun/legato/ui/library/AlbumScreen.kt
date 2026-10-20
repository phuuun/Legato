package com.phuuun.legato.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.phuuun.legato.ui.components.screen
import com.phuuun.legato.library.Album
import com.phuuun.legato.library.Artist
import com.phuuun.legato.library.Track
import com.phuuun.legato.ui.components.Artwork

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

@Composable
fun AlbumScreen(album: Album?, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit, actions: TrackActions, header: @Composable () -> Unit = {}) {
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
                }
            }
            items(album.tracks, key = { it.id }) { t -> SongRow(t, onClick = { onPlay(album.tracks, album.tracks.indexOf(t)) }, actions = actions) }
        }
    }
}

@Composable
fun ArtistScreen(artist: Artist?, onBack: () -> Unit, onAlbum: (Long) -> Unit, onPlay: (List<Track>, Int) -> Unit, actions: TrackActions) {
    Column(Modifier.screen()) {
        Back(onBack)
        if (artist == null) { Empty("Artist not found", "It may have been deleted."); return }
        Text(artist.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
        PlayShuffle(artist.albums.flatMap { it.tracks }, onPlay, Modifier.padding(start = 20.dp, top = 12.dp))
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(artist.albums, key = { it.id }) { a -> AlbumCard(a, onClick = { onAlbum(a.id) }, actions = actions) } }
    }
}
