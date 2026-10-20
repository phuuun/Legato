package com.phuuun.legato.ui.settings

import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.relativePathFromTreeDocId

/** Android's folder picker, giving a relative path ("Download/MUSIC/"); a folder off internal storage gets a toast instead. */
@Composable
fun rememberFolderPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        val path = relativePathFromTreeDocId(DocumentsContract.getTreeDocumentId(tree))
        if (path == null) Toast.makeText(context, "Only internal storage for now", Toast.LENGTH_SHORT).show() else latest(path)
    }
    return { launcher.launch(null) }
}

/** Files hidden from the library, each with a ✕ to bring it back. Nothing shows when none are hidden. */
@Composable
fun HiddenFiles(paths: List<String>, onUnhide: (String) -> Unit) {
    if (paths.isEmpty()) return
    Column {
        Text("HIDDEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
        paths.sortedBy { it.substringAfterLast('/').lowercase() }.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onUnhide(p) }) { Icon(Icons.Rounded.Close, contentDescription = "Unhide ${p.substringAfterLast('/')}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** A section's folders: each with a ✕, then "Add folder" through Android's folder picker. */
@Composable
fun FoldersPane(folders: Set<String>, hint: String?, onAdd: (String) -> Unit, onRemove: (String) -> Unit, footer: @Composable () -> Unit = {}) {
    val pick = rememberFolderPicker(onAdd)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("FOLDERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        }
        items(folders.sorted(), key = { it }) { f ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.trimEnd('/'), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(f) }) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${f.trimEnd('/')}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { pick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("Add folder", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (hint != null) item {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        item { footer() }
    }
}
