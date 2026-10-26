package com.phuuun.legato.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phuuun.legato.library.Album
import com.phuuun.legato.library.Artist
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.Sort
import com.phuuun.legato.library.db.PlayStat
import com.phuuun.legato.library.sortedFor
import kotlinx.coroutines.flow.Flow
import com.phuuun.legato.library.Track
import com.phuuun.legato.library.albumTag
import com.phuuun.legato.library.groupAlbums
import com.phuuun.legato.library.groupArtists
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryState(
    val loaded: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val query: String = "",
    val results: SearchResults = SearchResults(emptyList(), emptyList(), emptyList()),
)

class LibraryViewModel(repo: LibraryRepository, sort: Flow<Sort>, stats: Flow<Map<Long, PlayStat>>) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<LibraryState> = combine(repo.tracks, query, sort, stats) { tracks, q, s, st ->
        val albums = groupAlbums(tracks)
        LibraryState(true, tracks.sortedFor(s, st), albums.sortedFor(s, st), groupArtists(albums, tracks.filter { it.albumTag.isEmpty() }), q, search(q, tracks))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(q: String) { query.value = q }
}
