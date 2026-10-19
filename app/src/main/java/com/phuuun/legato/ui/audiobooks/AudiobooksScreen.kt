package com.phuuun.legato.ui.audiobooks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Book
import com.phuuun.legato.library.asAlbum
import com.phuuun.legato.ui.components.LegatoHeader
import com.phuuun.legato.ui.library.AlbumCard
import com.phuuun.legato.ui.library.Empty
import com.phuuun.legato.ui.library.TrackActions

@Composable
fun AudiobooksScreen(books: List<Book>, hasFolders: Boolean, onBook: (Book) -> Unit, actions: TrackActions, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        LegatoHeader(onSettings)
        when {
            !hasFolders -> Empty("No audiobooks", "Add an audiobook folder in Settings")
            books.isEmpty() -> Empty("No audiobooks", "Nothing in your audiobook folders yet.")
            else -> LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { items(books, key = { it.id }) { b -> AlbumCard(b.asAlbum(), onClick = { onBook(b) }, actions = actions) } }
        }
    }
}
