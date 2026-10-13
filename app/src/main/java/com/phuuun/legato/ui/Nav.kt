package com.phuuun.legato.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.Track
import com.phuuun.legato.ui.library.AlbumScreen
import com.phuuun.legato.ui.library.ArtistScreen
import com.phuuun.legato.ui.library.LibraryScreen
import com.phuuun.legato.ui.library.LibraryViewModel

@Composable
fun LegatoNav(library: LibraryRepository, onPlay: (List<Track>, Int) -> Unit) {
    val nav = rememberNavController()
    // One view model for every destination, so the loaded library and the search box survive navigation.
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(library) } })
    val state by vm.state.collectAsState()

    NavHost(nav, startDestination = "library") {
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
}
