package com.phuuun.legato.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.Track
import com.phuuun.legato.playback.PlayerConnection
import com.phuuun.legato.ui.components.MiniPlayer
import com.phuuun.legato.ui.library.AlbumScreen
import com.phuuun.legato.ui.library.ArtistScreen
import com.phuuun.legato.ui.library.Empty
import com.phuuun.legato.ui.library.LibraryScreen
import com.phuuun.legato.ui.library.LibraryViewModel
import com.phuuun.legato.ui.nowplaying.NowPlayingSheet
import com.phuuun.legato.ui.nowplaying.NowPlayingViewModel

private enum class Section(val route: String, val label: String, val icon: ImageVector) {
    Music("music", "MUSIC", Icons.Outlined.MusicNote),
    Audiobooks("audiobooks", "AUDIOBOOKS", Icons.Outlined.Headphones),
    Converter("converter", "CONVERTER", Icons.Outlined.Download),
}

@Composable
fun LegatoNav(library: LibraryRepository, player: PlayerConnection) {
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

    Column(Modifier.fillMaxSize()) {
        // The bar below owns the navigation-bar inset; screens keep systemBarsPadding for the status bar.
        NavHost(nav, startDestination = Section.Music.route, modifier = Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
            // Album and artist pages live inside the Music section, so its tab stays lit and keeps its back stack.
            navigation(startDestination = "library", route = Section.Music.route) {
                composable("library") {
                    LibraryScreen(state, vm::setQuery, onPlay, onAlbum = { nav.navigate("album/$it") }, onArtist = { nav.navigate("artist/${Uri.encode(it)}") })
                }
                composable("album/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id")
                    AlbumScreen(state.albums.firstOrNull { it.id == id }, onBack = { nav.popBackStack() }, onPlay = onPlay)
                }
                composable("artist/{name}", listOf(navArgument("name") { type = NavType.StringType })) { e ->
                    val name = e.arguments?.getString("name")
                    ArtistScreen(state.artists.firstOrNull { it.name == name }, onBack = { nav.popBackStack() }, onAlbum = { nav.navigate("album/$it") })
                }
            }
            composable(Section.Audiobooks.route) { ComingSoon("Audiobooks", "Your audiobooks will live here, with your place saved in each one.") }
            composable(Section.Converter.route) { ComingSoon("Converter", "Paste a YouTube link and save the audio to this phone.") }
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
    now?.let { if (expanded) NowPlayingSheet(it, nowPlayingVm, onDismiss = { expanded = false }) }
}

@Composable
private fun ComingSoon(title: String, body: String) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("LEGATO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Empty("Coming soon", body)
    }
}
