package com.phuuun.legato.ui.nowplaying

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Chapter
import com.phuuun.legato.library.Chapterfy
import com.phuuun.legato.library.MODEL_DOWNLOAD_MB
import com.phuuun.legato.library.formatTimestamp
import com.phuuun.legato.library.parseTimestamp

/**
 * The chapters of the file playing, as a time and a title each: change them, delete some, or add one where the book is
 * now. Saved in Legato only; Reset goes back to the file's own chapters.
 */
@Composable
fun ChapterEditor(chapters: List<Chapter>, positionMs: Long, canReset: Boolean, onSave: (List<Chapter>) -> Unit, onReset: () -> Unit, onClose: () -> Unit) {
    val rows = remember { chapters.map { formatTimestamp(it.startMs) to it.title }.toMutableStateList() }
    val parsed = rows.map { (time, title) -> parseTimestamp(time)?.let { Chapter(title.trim(), it) } }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            if (canReset) TextButton(onClick = { onReset(); onClose() }) { Text("Reset") }
            TextButton(enabled = parsed.all { it != null }, onClick = { onSave(parsed.filterNotNull()); onClose() }) { Text("Save") }
        }
        LazyColumn {
            itemsIndexed(rows) { i, (time, title) ->
                Row(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(time, { rows[i] = it to title }, singleLine = true, isError = parseTimestamp(time) == null, modifier = Modifier.width(112.dp))
                    OutlinedTextField(title, { rows[i] = time to it }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Rounded.Close, "Delete chapter") }
                }
            }
            item {
                TextButton(onClick = { rows.add(formatTimestamp(positionMs) to "Chapter ${rows.size + 1}") }, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Text("Add one at ${formatTime(positionMs)}", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * Chapterfy for the file playing (see Chapterfy): offers the speech model first, then listens, then shows what it heard
 * to use or discard. Closing the dialog doesn't stop it; the button opens it again. Listening goes on with Legato
 * closed (see ChapterfyService); the model download doesn't, so the screen stays on for it.
 */
@Composable
fun ChapterfyDialog(vm: NowPlayingViewModel, path: String, onClose: () -> Unit) {
    val chapterfy = vm.chapterfy
    val s by chapterfy.state.collectAsState()
    LaunchedEffect(Unit) { chapterfy.pickUpResult() }
    val context = LocalContext.current
    // Listening goes on with the app closed; the notification is how it says it's done.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { chapterfy.start(path) }
    val start = {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else chapterfy.start(path)
    }
    val busy = s is Chapterfy.State.Downloading
    val view = LocalView.current
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (val state = s) {
        Chapterfy.State.Idle -> if (!chapterfy.hasModel) AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Chapterfy") },
            text = { Text("Chapterfy listens through the book for the narrator saying \"Chapter …\" and marks each one. It needs an English speech model: a $MODEL_DOWNLOAD_MB MB download, once. Listening happens on this phone; nothing is sent anywhere.") },
            confirmButton = { TextButton(onClick = chapterfy::download) { Text("Download") } },
            dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        ) else AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Chapterfy") },
            text = { Text("Listens through this file for the narrator saying \"Chapter …\" and marks each one. It goes on with Legato closed; a notification says when it's done. The chapters it finds replace the list, and Reset in Edit brings the old one back.") },
            confirmButton = { TextButton(onClick = start) { Text("Start") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { chapterfy.deleteModel(); onClose() }) { Text("Delete model (${chapterfy.modelMb} MB)") }
                    TextButton(onClick = onClose) { Text("Cancel") }
                }
            },
        )
        is Chapterfy.State.Downloading -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Downloading the speech model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.percent}% of $MODEL_DOWNLOAD_MB MB", color = muted)
                }
            },
            confirmButton = { TextButton(onClick = chapterfy::cancel) { Text("Cancel") } },
        )
        // A share, not a position: the book is listened to in several stretches at once.
        is Chapterfy.State.Listening -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(if (state.path == path) "Listening…" else "Listening to another book…") },
            text = {
                val share = if (state.totalMs > 0) state.doneMs.toFloat() / state.totalMs else 0f
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
                    Text("${(share * 100).toInt()}% · ${state.found} chapters so far", color = muted)
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Hide") } },
            dismissButton = { TextButton(onClick = chapterfy::cancel) { Text("Stop") } },
        )
        is Chapterfy.State.Done -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(if (state.chapters.isEmpty()) "No chapters heard" else "Found ${state.chapters.size} chapters") },
            text = {
                if (state.chapters.isEmpty()) Text("The narrator never said \"Chapter …\" after a pause, or the book has music under the reading.")
                else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.chapters) { c ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatTime(c.startMs), color = muted)
                        }
                    }
                }
            },
            confirmButton = { if (state.chapters.isNotEmpty()) TextButton(onClick = { vm.useChapterfy(state); onClose() }) { Text("Use these") } },
            dismissButton = { TextButton(onClick = { chapterfy.dismiss(); onClose() }) { Text(if (state.chapters.isEmpty()) "OK" else "Discard") } },
        )
        is Chapterfy.State.Failed -> AlertDialog(
            onDismissRequest = { chapterfy.dismiss(); onClose() },
            title = { Text("Chapterfy stopped") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = { chapterfy.dismiss(); onClose() }) { Text("OK") } },
        )
    }
}
