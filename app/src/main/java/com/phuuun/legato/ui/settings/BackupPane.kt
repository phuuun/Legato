package com.phuuun.legato.ui.settings

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.Backups

/**
 * Back up now, or restore: the daily backup file in Downloads (a null address), or any backup file picked by hand.
 * Both call `done` when finished, so the status line re-reads.
 */
@Composable
fun BackupPane(backups: Backups, backUp: (done: () -> Unit) -> Unit, restore: (Uri?, done: () -> Unit) -> Unit) {
    // `last` and `fromBefore` aren't observable; bumping this re-reads them after a backup or restore.
    var refresh by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf<Uri?>(null) }
    var confirmingDaily by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirming = uri }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    key(refresh) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Playlists, bookmarks, edits, listening progress, play counts, hidden files and folders go to Download/${backups.file.name}, once a day. It stays when Legato is uninstalled, and the one before it is kept as \"${backups.previous.name}\". After reinstalling Legato, Android hides the old backup from it: restore it with Restore from another file.", style = MaterialTheme.typography.bodyMedium, color = muted)
        Text(
            when {
                backups.fromBefore -> "Daily backups are paused: there's a backup from before this install. Restore it, or back up now to replace it."
                backups.last == 0L -> "No backup yet."
                else -> "Last backup ${DateUtils.getRelativeTimeSpanString(backups.last)}."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { backUp { refresh++ } }) { Text("Back up now") }
        if (backups.file.exists()) OutlinedButton(onClick = { confirmingDaily = true }) { Text("Restore this backup") }
        TextButton(onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore from another file…") }
    } }
    if (confirming != null || confirmingDaily) AlertDialog(
        onDismissRequest = { confirming = null; confirmingDaily = false },
        title = { Text("Restore this backup?") },
        text = { Text("Playlists, bookmarks, edits, folders and hidden files are replaced with the backup's. Listening progress and play counts are added.") },
        confirmButton = { TextButton(onClick = { restore(confirming) { refresh++ }; confirming = null; confirmingDaily = false }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { confirming = null; confirmingDaily = false }) { Text("Cancel") } },
    )
}
