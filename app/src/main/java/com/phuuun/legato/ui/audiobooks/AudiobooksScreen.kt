package com.phuuun.legato.ui.audiobooks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.phuuun.legato.ui.components.screen
import com.phuuun.legato.library.Book
import com.phuuun.legato.library.Spot
import com.phuuun.legato.library.asAlbum
import com.phuuun.legato.library.bookProgress
import com.phuuun.legato.library.formatLeft
import com.phuuun.legato.ui.components.LegatoHeader
import com.phuuun.legato.ui.library.AlbumCard
import com.phuuun.legato.ui.library.Empty
import com.phuuun.legato.ui.library.TrackActions

@Composable
fun AudiobooksScreen(books: List<Book>, spots: Map<Long, Spot>, hasFolders: Boolean, onBook: (Book) -> Unit, actions: TrackActions, onSettings: () -> Unit) {
    Column(Modifier.screen()) {
        LegatoHeader(onSettings)
        when {
            !hasFolders -> Empty("No audiobooks", "Add an audiobook folder in Settings")
            books.isEmpty() -> Empty("No audiobooks", "Nothing in your audiobook folders yet.")
            else -> LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { items(books, key = { it.id }) { b -> AlbumCard(b.asAlbum(), onClick = { onBook(b) }, actions = actions) { BookProgress(b, spots) } } }
        }
    }
}

/** A thin bar and the time left; nothing for a book not started yet. */
@Composable
fun BookProgress(book: Book, spots: Map<Long, Spot>) {
    val (listened, total) = bookProgress(book.chapters, spots)
    if (listened <= 0 || total <= 0) return
    val done = listened >= total - 1_000
    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth((listened.toFloat() / total).coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.onBackground))
    }
    Text(if (done) "Finished" else formatLeft(total - listened), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

/** On a chapter book's page: one tap back to where listening stopped. */
@Composable
fun ResumeButton(book: Book, spots: Map<Long, Spot>, onResume: () -> Unit) {
    val (listened, total) = bookProgress(book.chapters, spots)
    BookProgress(book, spots)
    Button(onClick = onResume, modifier = Modifier.padding(top = 12.dp)) { Text(if (listened > 0 && listened < total - 1_000) "Resume" else "Play") }
}
