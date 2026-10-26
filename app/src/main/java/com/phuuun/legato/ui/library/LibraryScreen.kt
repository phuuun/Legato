package com.phuuun.legato.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.phuuun.legato.ui.components.PillSwitch
import com.phuuun.legato.ui.theme.Bold
import com.phuuun.legato.ui.theme.Motion
import com.phuuun.legato.ui.theme.enterFrom
import com.phuuun.legato.ui.theme.rememberSettled
import com.phuuun.legato.ui.theme.rise
import com.phuuun.legato.ui.theme.slideStep
import com.phuuun.legato.ui.theme.tap

private enum class Tab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists") }

@Composable
fun LibraryScreen(
    state: LibraryState,
    onPlay: (List<Track>, Int) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    actions: TrackActions,
    playlistsTab: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Songs) }
    Column(Modifier.fillMaxSize()) {
        if (state.query.isBlank()) {
            val position by animateFloatAsState(tab.ordinal.toFloat(), Motion.settle(), label = "tab")
            PillSwitch(Tab.entries.map { it.label }, position, { tab = Tab.entries[it] }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth())
        }
        when {
            !state.loaded -> Unit
            state.tracks.isEmpty() -> Empty("No music found", "Add some files to this phone and they'll show up here.")
            state.query.isNotBlank() -> SearchBody(state.results, onPlay, onAlbum, onArtist, actions)
            // Tabs switch by tap only (a swipe changes section); the new one slides in from its side.
            else -> AnimatedContent(tab, transitionSpec = { slideStep(targetState > initialState) }, label = "library tab") { t ->
                val settled = rememberSettled()
                when (t) {
                    Tab.Songs -> LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(state.tracks, key = { _, t -> t.id }) { i, t -> SongRow(t, onClick = { onPlay(state.tracks, i) }, actions = actions, modifier = Modifier.rise(settled, i)) }
                    }
                    Tab.Albums -> LazyVerticalGrid(
                        GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { itemsIndexed(state.albums, key = { _, a -> a.id }) { i, a -> AlbumCard(a, onClick = { onAlbum(a.id) }, actions = actions, modifier = Modifier.rise(settled, i)) } }
                    Tab.Artists -> LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(state.artists, key = { _, a -> a.name }) { i, a -> ArtistRow(a, onClick = { onArtist(a.name) }, modifier = Modifier.rise(settled, i)) }
                    }
                    Tab.Playlists -> playlistsTab()
                }
            }
        }
    }
}

@Composable
private fun SearchBody(r: SearchResults, onPlay: (List<Track>, Int) -> Unit, onAlbum: (Long) -> Unit, onArtist: (String) -> Unit, actions: TrackActions) {
    if (r.tracks.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty()) { Empty("Nothing found", "Try another spelling."); return }
    LazyColumn(Modifier.fillMaxSize()) {
        if (r.artists.isNotEmpty()) { item { Eyebrow("ARTISTS") }; items(r.artists, key = { "ar" + it.name }) { a -> ArtistRow(a, onClick = { onArtist(a.name) }) } }
        if (r.albums.isNotEmpty()) { item { Eyebrow("ALBUMS") }; items(r.albums, key = { "al" + it.id }) { a ->
            SongRowLike(a.title, a.artist) { onAlbum(a.id) } } }
        if (r.tracks.isNotEmpty()) { item { Eyebrow("SONGS") }; items(r.tracks, key = { "t" + it.id }) { t -> SongRow(t, onClick = { onPlay(r.tracks, r.tracks.indexOf(t)) }, actions = actions) } }
    }
}

@Composable
private fun SongRowLike(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().tap(onClick).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Eyebrow(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))

/** Tsundoku's empty shelf: a big tight title and a quiet line under it, settling in. */
@Composable
fun Empty(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.enterFrom(offsetY = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Bold.Ink, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
