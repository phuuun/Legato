package com.phuuun.legato.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Album
import com.phuuun.legato.library.Artist
import com.phuuun.legato.library.Track
import com.phuuun.legato.ui.components.Artwork

@Composable
private fun Back(onBack: () -> Unit) =
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }

@Composable
fun AlbumScreen(album: Album?, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit, onQueue: (List<Track>, next: Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Back(onBack)
        if (album == null) { Empty("Album not found", "It may have been deleted."); return }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Artwork(album.artUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                    Text(album.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                    Text(album.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(album.tracks, key = { it.id }) { t -> SongRow(t, onClick = { onPlay(album.tracks, album.tracks.indexOf(t)) }, onQueue = { onQueue(listOf(t), it) }) }
        }
    }
}

@Composable
fun ArtistScreen(artist: Artist?, onBack: () -> Unit, onAlbum: (Long) -> Unit, onQueue: (List<Track>, next: Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Back(onBack)
        if (artist == null) { Empty("Artist not found", "It may have been deleted."); return }
        Text(artist.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(artist.albums, key = { it.id }) { a -> AlbumCard(a, onClick = { onAlbum(a.id) }, onQueue = { onQueue(a.tracks, it) }) } }
    }
}
