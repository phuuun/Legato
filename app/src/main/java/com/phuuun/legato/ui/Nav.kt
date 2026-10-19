package com.phuuun.legato.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phuuun.legato.library.FolderKind
import com.phuuun.legato.library.Book
import com.phuuun.legato.library.FolderStore
import com.phuuun.legato.library.ProgressStore
import com.phuuun.legato.library.chapterStart
import com.phuuun.legato.library.resumePoint
import com.phuuun.legato.library.asAlbum
import com.phuuun.legato.library.edit.CoverChange
import com.phuuun.legato.library.edit.TagWriter
import com.phuuun.legato.library.edit.fixRows
import com.phuuun.legato.library.edit.properties
import com.phuuun.legato.library.groupBooks
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.PlaylistRepository
import com.phuuun.legato.library.Track
import com.phuuun.legato.playback.PlayerConnection
import com.phuuun.legato.ui.audiobooks.AudiobooksScreen
import com.phuuun.legato.ui.audiobooks.ResumeButton
import com.phuuun.legato.ui.components.LegatoHeader
import com.phuuun.legato.ui.edit.BulkFixScreen
import com.phuuun.legato.ui.edit.EditScreen
import com.phuuun.legato.ui.components.MiniPlayer
import com.phuuun.legato.ui.library.AlbumScreen
import com.phuuun.legato.ui.library.ArtistScreen
import com.phuuun.legato.ui.library.Empty
import com.phuuun.legato.ui.library.LibraryScreen
import com.phuuun.legato.ui.library.LibraryViewModel
import com.phuuun.legato.ui.library.TrackActions
import com.phuuun.legato.ui.nowplaying.NowPlayingSheet
import com.phuuun.legato.ui.nowplaying.NowPlayingViewModel
import com.phuuun.legato.ui.playlists.AddToPlaylistSheet
import com.phuuun.legato.ui.playlists.PlaylistScreen
import com.phuuun.legato.ui.playlists.PlaylistsList
import com.phuuun.legato.ui.settings.FoldersPane
import com.phuuun.legato.ui.settings.SettingsScreen
import com.phuuun.legato.ui.settings.SettingsSection
import kotlinx.coroutines.launch

private enum class Section(val route: String, val label: String, val icon: ImageVector) {
    Music("music", "MUSIC", Icons.Outlined.MusicNote),
    Audiobooks("audiobooks", "AUDIOBOOKS", Icons.Outlined.Headphones),
    Converter("converter", "CONVERTER", Icons.Outlined.Download),
}

@Composable
fun LegatoNav(library: LibraryRepository, audiobooks: LibraryRepository, player: PlayerConnection, playlistRepo: PlaylistRepository, folders: FolderStore, tagWriter: TagWriter, progress: ProgressStore) {
    val nav = rememberNavController()
    // One view model for every destination, so the loaded library and the search box survive navigation.
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(library) } })
    val state by vm.state.collectAsState()
    val current by nav.currentBackStackEntryAsState()
    val nowPlayingVm: NowPlayingViewModel = viewModel(factory = viewModelFactory { initializer { NowPlayingViewModel(player.controller) } })
    val now by nowPlayingVm.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Like YT Music: picking a song opens the full player; swiping it down leaves the mini player.
    val onPlay: (List<Track>, Int) -> Unit = { tracks, i -> player.play(tracks, i); expanded = true }
    val context = LocalContext.current
    // Playlist writes run in this activity-long scope, so leaving a screen mid-write doesn't cancel it.
    val scope = rememberCoroutineScope()
    val playlists by playlistRepo.playlists.collectAsState(emptyList())
    var adding by remember { mutableStateOf<List<Track>?>(null) }
    val toast = { text: String -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    val onSettings = { nav.navigate("settings") { launchSingleTop = true } }
    val audiobookTracks by audiobooks.tracks.collectAsState(emptyList())
    val books = remember(audiobookTracks) { groupBooks(audiobookTracks) }
    val audiobookFolders by remember { folders.folders(FolderKind.Audiobooks) }.collectAsState(emptySet())
    val spots by remember { progress.spots() }.collectAsState(emptyMap())
    // A book picks up where it stopped (less 5 s); a chapter picked by hand picks up at its own spot.
    val playBook = { b: Book -> val (i, ms) = resumePoint(b.chapters, spots); player.play(b.chapters, i, ms, audiobook = true); expanded = true }
    val playChapter: (List<Track>, Int) -> Unit = { chapters, i -> player.play(chapters, i, chapterStart(chapters[i], spots[chapters[i].id]), audiobook = true); expanded = true }
    val actions = remember {
        TrackActions(
            queue = { tracks, next -> player.queue(tracks, next); toast(if (next) "Playing next" else "Added to queue") },
            addToPlaylist = { adding = it },
            edit = { nav.navigate("edit/${it.id}") },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // The bar below owns the navigation-bar inset; screens keep systemBarsPadding for the status bar.
        NavHost(nav, startDestination = Section.Music.route, modifier = Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
            // Album and artist pages live inside the Music section, so its tab stays lit and keeps its back stack.
            navigation(startDestination = "library", route = Section.Music.route) {
                composable("library") {
                    LibraryScreen(state, vm::setQuery, onPlay, onAlbum = { nav.navigate("album/$it") }, onArtist = { nav.navigate("artist/${Uri.encode(it)}") }, actions = actions,
                        playlistsTab = { PlaylistsList(playlists, onOpen = { nav.navigate("playlist/$it") }, onCreate = { scope.launch { playlistRepo.create(it) } }) },
                        onSettings = onSettings,
                    )
                }
                composable("album/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id")
                    AlbumScreen(state.albums.firstOrNull { it.id == id }, onBack = { nav.popBackStack() }, onPlay = onPlay, actions = actions)
                }
                composable("artist/{name}", listOf(navArgument("name") { type = NavType.StringType })) { e ->
                    val name = e.arguments?.getString("name")
                    ArtistScreen(state.artists.firstOrNull { it.name == name }, onBack = { nav.popBackStack() }, onAlbum = { nav.navigate("album/$it") }, actions = actions)
                }
                composable("playlist/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id") ?: 0
                    val tracks by remember(id) { playlistRepo.tracksOf(id) }.collectAsState(emptyList())
                    PlaylistScreen(
                        playlists.firstOrNull { it.id == id }, tracks, onBack = { nav.popBackStack() }, onPlay = onPlay, actions = actions,
                        onRename = { scope.launch { playlistRepo.rename(id, it) } },
                        onDelete = { scope.launch { playlistRepo.delete(id) }; nav.popBackStack() },
                        onRemove = { scope.launch { playlistRepo.removeAt(id, it) } },
                        onMove = { from, to -> scope.launch { playlistRepo.move(id, from, to) } },
                    )
                }
            }
            // A one-file book plays straight away; chapter files open as a list, like an album.
            navigation(startDestination = "books", route = Section.Audiobooks.route) {
                composable("books") {
                    AudiobooksScreen(
                        books, spots, audiobookFolders.isNotEmpty(), actions = actions, onSettings = onSettings,
                        onBook = { b -> if (b.chapters.size == 1) playBook(b) else nav.navigate("book/${b.id}") },
                    )
                }
                composable("book/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id")
                    val book = books.firstOrNull { it.id == id }
                    AlbumScreen(book?.asAlbum(), onBack = { nav.popBackStack() }, onPlay = playChapter, actions = actions) {
                        if (book != null) ResumeButton(book, spots) { playBook(book) }
                    }
                }
            }
            composable(Section.Converter.route) { ComingSoon("Converter", "Paste a YouTube link and save the audio to this phone.", onSettings) }
            composable("edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id")
                val book = books.firstOrNull { b -> b.chapters.any { it.id == id } }
                val track = book?.chapters?.first { it.id == id } ?: state.tracks.firstOrNull { it.id == id }
                EditScreen(
                    track, if (book != null) FolderKind.Audiobooks else FolderKind.Music, book?.chapters?.size ?: 1, onBack = { nav.popBackStack() },
                    onSave = { properties, cover ->
                        // The Nav scope outlives this screen, so leaving mid-save doesn't cancel the write.
                        scope.launch { if (track != null && tagWriter.write(track, properties, cover)) nav.popBackStack() else toast("Couldn't save ${track?.title}") }
                    },
                )
            }
            composable("fix") {
                val rows = remember { fixRows(state.tracks) }
                BulkFixScreen(rows, onBack = { nav.popBackStack() }, write = { t, e -> tagWriter.write(t, e.properties(), CoverChange.Keep) })
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() }) { section ->
                    when (section) {
                        SettingsSection.Music -> {
                            val f by remember { folders.folders(FolderKind.Music) }.collectAsState(emptySet())
                            FoldersPane(f, "Only files directly in these folders show up. With no folders, all music shows.", { folders.add(FolderKind.Music, it) }, { folders.remove(FolderKind.Music, it) }) {
                                Text(
                                    "Fix unknown artists (${state.tracks.count { it.artists.isEmpty() }})", style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("fix") }.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                        SettingsSection.Audiobooks -> {
                            val f by remember { folders.folders(FolderKind.Audiobooks) }.collectAsState(emptySet())
                            FoldersPane(f, null, { folders.add(FolderKind.Audiobooks, it) }, { folders.remove(FolderKind.Audiobooks, it) })
                        }
                        SettingsSection.Converter -> Text("Comes with the converter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                    }
                }
            }
        }
        now?.let { MiniPlayer(it, nowPlayingVm::togglePlay, onOpen = { expanded = true }) }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
            Section.entries.forEach { s ->
                NavigationBarItem(
                    selected = current?.destination?.hierarchy?.any { it.route == s.route } == true,
                    onClick = {
                        nav.navigate(s.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(s.icon, contentDescription = null) },
                    label = { Text(s.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onBackground, selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
    now?.let { if (expanded) NowPlayingSheet(it, nowPlayingVm, onDismiss = { expanded = false }, onEdit = { id -> nav.navigate("edit/$id") }) }
    adding?.let { tracks ->
        val ids = tracks.map { it.id }
        AddToPlaylistSheet(
            playlists,
            onPick = { p -> scope.launch { playlistRepo.addTracks(p.id, ids) }; toast("Added to ${p.name}") },
            onCreate = { name -> scope.launch { playlistRepo.addTracks(playlistRepo.create(name), ids) }; toast("Added to $name") },
            onDismiss = { adding = null },
        )
    }
}

@Composable
private fun ComingSoon(title: String, body: String, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        LegatoHeader(onSettings)
        Empty(title, body)
    }
}
