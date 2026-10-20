package com.phuuun.legato.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Bookmark

/** Bookmark colours, by the index a bookmark stores. Yellow first: it's the default. */
val BookmarkColors = listOf(Color(0xFFFFC53D), Color(0xFFE5484D), Color(0xFFF76B15), Color(0xFF46A758), Color(0xFF0090FF), Color(0xFF8E4EC6))

fun bookmarkColor(i: Int) = BookmarkColors.getOrElse(i) { BookmarkColors.first() }

/**
 * A book's chapters and bookmarks. Chapters: the book's files when it has several, with the chapters inside the
 * one playing listed under it. Tapping anything jumps there and closes the sheet; the player then offers the way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSheet(state: NowPlayingState, vm: NowPlayingViewModel, onDismiss: () -> Unit) {
    val player = vm.controller.value ?: return
    val chapters by vm.chapters.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    val files = remember(state.trackId, player.mediaItemCount) { List(player.mediaItemCount) { player.getMediaItemAt(it) } }
    val playing = player.currentMediaItemIndex
    val indexOf = remember(files) { files.withIndex().associate { (i, f) -> f.mediaId.toLongOrNull() to i } }
    val here = chapters.lastOrNull { it.startMs <= state.positionMs }
    val go = { index: Int, ms: Long -> vm.jumpTo(index, ms); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(start = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf("CHAPTERS", "BOOKMARKS").forEachIndexed { i, label ->
                Text(
                    label, style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                    color = if (tab == i) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { tab = i }.padding(vertical = 8.dp),
                )
            }
        }
        LazyColumn {
            if (tab == 0) {
                files.forEachIndexed { i, file ->
                    if (files.size > 1) item(key = "f$i") {
                        Line(file.mediaMetadata.title?.toString().orEmpty(), file.mediaMetadata.durationMs?.let(::formatTime).orEmpty(), current = i == playing && chapters.isEmpty()) { go(i, 0) }
                    }
                    if (i == playing) items(chapters, key = { "c$i-${it.startMs}" }) { c ->
                        Line(c.title, formatTime(c.startMs), current = c == here, indent = files.size > 1) { go(i, c.startMs) }
                    }
                }
                if (files.size <= 1 && chapters.isEmpty()) item { Hint("This book has no chapters.") }
            } else {
                val mine = bookmarks.filter { it.trackId in indexOf }.sortedWith(compareBy({ indexOf[it.trackId] }, { it.positionMs }))
                if (mine.isEmpty()) item { Hint("Tap the bookmark in the player to mark a spot.") }
                items(mine, key = { it.id }) { b ->
                    val i = indexOf.getValue(b.trackId)
                    Row(Modifier.fillMaxWidth().clickable { go(i, b.positionMs) }.padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(bookmarkColor(b.color), CircleShape))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(b.caption.ifBlank { "Bookmark" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val where = if (files.size > 1) files[i].mediaMetadata.title?.toString().orEmpty() + " · " else ""
                            Text(where + formatTime(b.positionMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { editing = b }) { Icon(Icons.Rounded.Edit, "Edit bookmark", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
    editing?.let { b ->
        BookmarkDialog(
            "Bookmark at ${formatTime(b.positionMs)}", b.caption, b.color,
            onSave = { caption, color -> vm.saveBookmark(b.copy(caption = caption, color = color)) },
            onDelete = { vm.deleteBookmark(b.id) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Line(title: String, time: String, current: Boolean, indent: Boolean = false, onClick: () -> Unit) =
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (current) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(start = if (indent) 40.dp else 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
    }

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))

/** A caption (optional) and a colour. [onDelete] is null when the bookmark doesn't exist yet. */
@Composable
fun BookmarkDialog(title: String, caption: String, color: Int, onSave: (caption: String, color: Int) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(caption) }
    var picked by remember { mutableIntStateOf(color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(text, { text = it }, placeholder = { Text("Caption (optional)") }, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BookmarkColors.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(32.dp).background(c, CircleShape)
                                .then(if (i == picked) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { picked = i },
                            contentAlignment = Alignment.Center,
                        ) { if (i == picked) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, picked); onDismiss() }) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
