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
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import com.phuuun.legato.ui.components.PillButton
import com.phuuun.legato.ui.components.StatusSticker
import com.phuuun.legato.ui.theme.Bold
import com.phuuun.legato.ui.theme.pressScale
import com.phuuun.legato.ui.theme.rememberSettled
import com.phuuun.legato.ui.theme.rise
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
            else -> {
                val settled = rememberSettled()
                LazyVerticalGrid(
                    GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val last = if (q.isEmpty()) lastBook(books, spots) else null
                    if (last != null) item(span = { GridItemSpan(maxLineSpan) }, key = "continue") { ContinueListening(last, spots, Modifier.rise(settled, 0)) { onResume(last) } }
                    itemsIndexed(shown, key = { _, b -> b.id }) { i, b -> AlbumCard(b.asAlbum(), onClick = { onBook(b) }, actions = actions, modifier = Modifier.rise(settled, i + 1)) { BookProgress(b, spots) } }
                }
            }
        }
    }
}

/** The book heard last, one tap from picking up where it stopped: a card with a Mint play button. */
@Composable
private fun ContinueListening(book: Book, spots: Map<Long, Spot>, modifier: Modifier, onResume: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val card = RoundedCornerShape(20.dp)
    Column(modifier.padding(bottom = 12.dp)) {
        Text("CONTINUE LISTENING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().pressScale(pressed, 0.98f).clip(card).background(Bold.Surface).border(2.dp, Bold.Ink, card)
                .clickable(source, indication = null, onClick = onResume).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Artwork(book.artUri, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BookProgress(book, spots)
            }
            Box(
                Modifier.size(48.dp).background(Bold.Mint, CircleShape).border(2.dp, Bold.Paper, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume", Modifier.size(28.dp), tint = Bold.DarkInk) }
        }
    }
}

/** A thin bar and the time left; nothing for a book not started yet. */
@Composable
fun BookProgress(book: Book, spots: Map<Long, Spot>) {
    val (listened, total) = bookProgress(book.chapters, spots)
    if (listened <= 0 || total <= 0) return
    val done = listened >= total - 1_000
    if (done) { StatusSticker("FINISHED", Bold.Mint, Modifier.padding(top = 6.dp)); return }
    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Bold.QuietLine)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth((listened.toFloat() / total).coerceIn(0f, 1f)).background(Bold.Mint, CircleShape))
    }
    Text(formatLeft(total - listened), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

/** On a chapter book's page: one tap back to where listening stopped. */
@Composable
fun ResumeButton(book: Book, spots: Map<Long, Spot>, onResume: () -> Unit) {
    val (listened, total) = bookProgress(book.chapters, spots)
    BookProgress(book, spots)
    PillButton(if (listened > 0 && listened < total - 1_000) "Resume" else "Play", onResume, Modifier.padding(top = 12.dp), icon = Icons.Rounded.PlayArrow)
}
