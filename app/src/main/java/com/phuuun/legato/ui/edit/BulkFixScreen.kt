package com.phuuun.legato.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Track
import com.phuuun.legato.library.albumTag
import com.phuuun.legato.library.edit.FixRow
import com.phuuun.legato.library.edit.MusicEdit
import com.phuuun.legato.ui.library.Back
import com.phuuun.legato.ui.library.Empty
import kotlinx.coroutines.launch

/**
 * Every Unknown-artist song with a guessed artist and title. Fix the guesses, swap the title-first ones,
 * untick what shouldn't change, then save them all. Saved rows leave; failed and unticked ones stay.
 */
@Composable
fun BulkFixScreen(rows: List<FixRow>, onBack: () -> Unit, write: suspend (Track, MusicEdit) -> Boolean) {
    val items = remember { rows.toMutableStateList() }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ticked = items.count { it.include }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Back(onBack)
            Text("Fix unknown artists", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(enabled = ticked > 0 && !saving, modifier = Modifier.padding(end = 8.dp), onClick = {
                saving = true
                scope.launch {
                    val todo = items.filter { it.include }
                    val failed = mutableListOf<FixRow>()
                    todo.forEachIndexed { i, r ->
                        status = "Saving ${i + 1} / ${todo.size}"
                        if (!write(r.track, MusicEdit(r.title, listOf(r.artist), r.track.albumTag))) failed += r
                    }
                    status = "Saved ${todo.size - failed.size}" + if (failed.isEmpty()) "" else ". Couldn't save: " + failed.joinToString { it.track.title }
                    items.removeAll { it in todo && it !in failed }
                    saving = false
                }
            }) { Text("Save $ticked") }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        if (items.isEmpty()) Empty("Nothing to fix", "Every song has an artist.")
        else LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(items, key = { _, r -> r.track.id }) { i, r ->
                Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(r.include, onCheckedChange = { items[i] = r.copy(include = it) })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(r.track.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Field("Artist", r.artist) { items[i] = r.copy(artist = it) }
                        Field("Title", r.title) { items[i] = r.copy(title = it) }
                    }
                    // Some rips are "Title - Artist"; one tap flips the guess.
                    IconButton(onClick = { items[i] = r.copy(artist = r.title, title = r.artist) }) { Icon(Icons.Rounded.SwapVert, contentDescription = "Swap artist and title") }
                }
            }
        }
    }
}
