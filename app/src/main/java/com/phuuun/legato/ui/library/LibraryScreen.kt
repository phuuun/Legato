package com.phuuun.legato.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Track

private enum class Tab(val label: String) { Songs("SONGS"), Albums("ALBUMS"), Artists("ARTISTS") }

@Composable
fun LibraryScreen(
    state: LibraryState,
    onQuery: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onQueue: (List<Track>, next: Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Songs) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("LEGATO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        Text("Library", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
        TextField(
            value = state.query, onValueChange = onQuery, singleLine = true,
            placeholder = { Text("Search") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
        if (state.query.isBlank()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Tab.entries.forEach { t ->
                    Text(
                        t.label, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                        color = if (t == tab) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { tab = t }.padding(vertical = 8.dp),
                    )
                }
            }
        }
        when {
            !state.loaded -> Unit
            state.tracks.isEmpty() -> Empty("No music found", "Add some files to this phone and they'll show up here.")
            state.query.isNotBlank() -> SearchBody(state.results, onPlay, onAlbum, onArtist, onQueue)
            tab == Tab.Songs -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.tracks, key = { it.id }) { t -> SongRow(t, onClick = { onPlay(state.tracks, state.tracks.indexOf(t)) }, onQueue = { onQueue(listOf(t), it) }) }
            }
            tab == Tab.Albums -> LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { items(state.albums, key = { it.id }) { a -> AlbumCard(a, onClick = { onAlbum(a.id) }, onQueue = { onQueue(a.tracks, it) }) } }
            else -> LazyColumn(Modifier.fillMaxSize()) { items(state.artists, key = { it.name }) { a -> ArtistRow(a, onClick = { onArtist(a.name) }) } }
        }
    }
}

@Composable
private fun SearchBody(r: SearchResults, onPlay: (List<Track>, Int) -> Unit, onAlbum: (Long) -> Unit, onArtist: (String) -> Unit, onQueue: (List<Track>, Boolean) -> Unit) {
    if (r.tracks.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty()) { Empty("Nothing found", "Try another spelling."); return }
    LazyColumn(Modifier.fillMaxSize()) {
        if (r.artists.isNotEmpty()) { item { Eyebrow("ARTISTS") }; items(r.artists, key = { "ar" + it.name }) { a -> ArtistRow(a, onClick = { onArtist(a.name) }) } }
        if (r.albums.isNotEmpty()) { item { Eyebrow("ALBUMS") }; items(r.albums, key = { "al" + it.id }) { a ->
            SongRowLike(a.title, a.artist) { onAlbum(a.id) } } }
        if (r.tracks.isNotEmpty()) { item { Eyebrow("SONGS") }; items(r.tracks, key = { "t" + it.id }) { t -> SongRow(t, onClick = { onPlay(r.tracks, r.tracks.indexOf(t)) }, onQueue = { onQueue(listOf(t), it) }) } }
    }
}

@Composable
private fun SongRowLike(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Eyebrow(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))

@Composable
fun Empty(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
