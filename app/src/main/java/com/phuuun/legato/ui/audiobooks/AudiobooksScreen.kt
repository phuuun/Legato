package com.phuuun.legato.ui.audiobooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Book
import com.phuuun.legato.library.Spot
import com.phuuun.legato.library.asAlbum
import com.phuuun.legato.library.bookProgress
import com.phuuun.legato.library.formatLeft
import com.phuuun.legato.library.lastBook
import com.phuuun.legato.ui.components.Artwork
import com.phuuun.legato.ui.library.AlbumCard
import com.phuuun.legato.ui.library.Empty
import com.phuuun.legato.ui.library.TrackActions

/** [books] and [hasFolders] are null until the first scan lands, and nothing shows until then, not even "No audiobooks". */
@Composable
fun AudiobooksScreen(books: List<Book>?, spots: Map<Long, Spot>, hasFolders: Boolean?, query: String, onBook: (Book) -> Unit, onResume: (Book) -> Unit, actions: TrackActions) {
    val q = query.trim().lowercase()
    val shown = remember(books, q) { books?.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.author.lowercase().contains(q) } }
    Column(Modifier.fillMaxSize()) {
        when {
            books == null || hasFolders == null || shown == null -> Unit
            !hasFolders -> Empty("No audiobooks", "Add an audiobook folder in Settings")
            books.isEmpty() -> Empty("No audiobooks", "Nothing in your audiobook folders yet.")
            shown.isEmpty() -> Empty("Nothing found", "Try another spelling.")
            else -> LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val last = if (q.isEmpty()) lastBook(books, spots) else null
                if (last != null) item(span = { GridItemSpan(maxLineSpan) }, key = "continue") { ContinueListening(last, spots) { onResume(last) } }
                items(shown, key = { it.id }) { b -> AlbumCard(b.asAlbum(), onClick = { onBook(b) }, actions = actions) { BookProgress(b, spots) } }
            }
        }
    }
}

/** The book heard last, one tap from picking up where it stopped. */
@Composable
private fun ContinueListening(book: Book, spots: Map<Long, Spot>, onResume: () -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text("CONTINUE LISTENING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onResume).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Artwork(book.artUri, Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BookProgress(book, spots)
            }
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume", Modifier.size(32.dp))
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
