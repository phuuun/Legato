package com.phuuun.legato.ui.converter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phuuun.legato.converter.DownloadJob
import com.phuuun.legato.converter.DownloadQueue
import com.phuuun.legato.converter.DownloadService
import com.phuuun.legato.converter.JobState
import com.phuuun.legato.converter.Video
import com.phuuun.legato.converter.YouTube
import com.phuuun.legato.converter.bookFileName
import com.phuuun.legato.converter.guessBook
import com.phuuun.legato.converter.guessSong
import com.phuuun.legato.converter.musicFileName
import com.phuuun.legato.converter.parseYouTube
import com.phuuun.legato.converter.videoUrl
import com.phuuun.legato.library.FolderKind
import com.phuuun.legato.library.FolderStore
import com.phuuun.legato.library.edit.BookEdit
import com.phuuun.legato.library.edit.MusicEdit
import com.phuuun.legato.library.edit.properties
import com.phuuun.legato.ui.components.LegatoHeader
import com.phuuun.legato.ui.edit.Field
import com.phuuun.legato.ui.nowplaying.formatTime
import com.phuuun.legato.ui.settings.rememberFolderPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

private sealed interface Fetch {
    data object Idle : Fetch
    data object Loading : Fetch
    data class Error(val message: String) : Fetch
    data class One(val video: Video) : Fetch
}

/** Link in, preview with guessed tags, pick where it goes, download; the queue below shows what's happening. */
@Composable
fun ConverterScreen(initialLink: String?, folders: FolderStore, downloads: DownloadQueue, onSettings: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var link by rememberSaveable { mutableStateOf(initialLink.orEmpty()) }
    var fetch by remember { mutableStateOf<Fetch>(Fetch.Idle) }
    val jobs by downloads.jobs.collectAsState()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun load(text: String) {
        val parsed = parseYouTube(text)
        if (parsed?.video == null) { fetch = Fetch.Error(if (parsed == null) "That isn't a YouTube link" else "Playlists come next"); return }
        fetch = Fetch.Loading
        scope.launch {
            fetch = try { Fetch.One(YouTube.video(videoUrl(parsed.video))) } catch (e: Exception) { Fetch.Error(e.message ?: "Couldn't load that video") }
        }
    }
    LaunchedEffect(initialLink) { if (initialLink != null) { link = initialLink; load(initialLink) } }

    fun queue(job: DownloadJob, kind: FolderKind) {
        folders.setLastFolder(kind, job.folder)
        downloads.add(listOf(job))
        DownloadService.start(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        fetch = Fetch.Idle
        link = ""
    }

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        item { LegatoHeader(onSettings) }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Field("YouTube link", link, onRemove = null) { link = it }
                }
                IconButton(onClick = { clipboard.getText()?.text?.let { link = it; load(it) } }) { Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste") }
            }
            Button(onClick = { load(link) }, enabled = link.isNotBlank() && fetch != Fetch.Loading, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Fetch") }
        }
        item {
            when (val f = fetch) {
                Fetch.Idle -> Text(
                    "Paste a YouTube link, or share one to Legato from the YouTube app.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
                )
                Fetch.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is Fetch.Error -> Text(f.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
                is Fetch.One -> VideoCard(f.video, folders, downloads) { job, kind -> queue(job, kind) }
            }
        }
        if (jobs.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("DOWNLOADS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = downloads::clearFinished) { Text("Clear finished") }
                }
            }
            items(jobs.asReversed(), key = { it.id }) { job -> JobRow(job, onRetry = { downloads.retry(job.id); DownloadService.start(context) }) }
        }
    }
}

@Composable
private fun VideoCard(video: Video, folders: FolderStore, downloads: DownloadQueue, onDownload: (DownloadJob, FolderKind) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(FolderKind.Music) }
    val song = remember(video) { guessSong(video.title, video.uploader) }
    val book = remember(video) { guessBook(video.title) }
    var artist by remember(video) { mutableStateOf(song.artist) }
    var title by remember(video) { mutableStateOf(song.title) }
    var bookTitle by remember(video) { mutableStateOf(book.title) }
    var author by remember(video) { mutableStateOf(book.author) }
    var narrator by remember(video) { mutableStateOf("") }
    val choices by remember(kind) { folders.folders(kind) }.collectAsState(emptySet())
    var folder by remember(kind, choices) { mutableStateOf(folders.lastFolder(kind)?.takeIf { it in choices } ?: choices.sorted().firstOrNull()) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
        Column {
            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${video.uploader} · ${formatTime(video.durationSec * 1000)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            FolderKind.entries.forEach { k ->
                Text(
                    if (k == FolderKind.Music) "MUSIC" else "AUDIOBOOK", style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (k == kind) FontWeight.Bold else FontWeight.Normal,
                    color = if (k == kind) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { kind = k }.padding(vertical = 8.dp),
                )
            }
        }
        if (kind == FolderKind.Music) {
            Field("Artist", artist) { artist = it }
            Field("Title", title) { title = it }
        } else {
            Field("Book title", bookTitle) { bookTitle = it }
            Field("Author", author) { author = it }
            Field("Narrator", narrator) { narrator = it }
        }
        Destination(kind, choices, folder, onPick = { folder = it }, onAdd = { folders.add(kind, it); folder = it })
        Button(
            enabled = folder != null,
            onClick = {
                val dest = folder ?: return@Button
                val (props, name, label) = if (kind == FolderKind.Music) Triple(MusicEdit(title, listOf(artist), "").properties(), musicFileName(artist, title), title)
                else Triple(BookEdit(bookTitle, null, author, narrator).properties(), bookFileName(bookTitle), bookTitle)
                onDownload(DownloadJob(downloads.nextId(), video.url, label, dest, name, props, video.thumbnail), kind)
            },
        ) { Text("Download") }
    }
}

/** Where the file goes: one of this kind's folders from Settings, or, with none yet, a button to add one right here. */
@Composable
private fun Destination(kind: FolderKind, choices: Set<String>, folder: String?, onPick: (String) -> Unit, onAdd: (String) -> Unit) {
    val pick = rememberFolderPicker(onAdd)
    var open by remember { mutableStateOf(false) }
    if (choices.isEmpty()) {
        TextButton(onClick = pick) { Icon(Icons.Rounded.Add, null); Text("Add folder", Modifier.padding(start = 8.dp)) }
        Text(
            "Pick where ${if (kind == FolderKind.Music) "music" else "audiobooks"} should go. It's added to Settings too.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Box {
        Row(Modifier.clickable { open = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Save to ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(folder?.trimEnd('/') ?: "", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose folder")
        }
        DropdownMenu(open, { open = false }) {
            choices.sorted().forEach { f -> DropdownMenuItem(text = { Text(f.trimEnd('/')) }, onClick = { onPick(f); open = false }) }
            DropdownMenuItem(text = { Text("Add folder…") }, onClick = { open = false; pick() })
        }
    }
}

@Composable
private fun JobRow(job: DownloadJob, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(job.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        when (val s = job.state) {
            JobState.Queued -> Text("Waiting · ${job.folder.trimEnd('/')}", style = MaterialTheme.typography.bodyMedium, color = muted)
            is JobState.Downloading -> {
                Text("Downloading ${s.percent}%", style = MaterialTheme.typography.bodyMedium, color = muted)
                LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            JobState.Tagging -> Text("Adding tags and cover…", style = MaterialTheme.typography.bodyMedium, color = muted)
            JobState.Done -> Text("Saved to ${job.folder.trimEnd('/')}", style = MaterialTheme.typography.bodyMedium, color = muted)
            is JobState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

/** The video's thumbnail, fetched directly (Coil here has no network fetcher, on purpose). */
@Composable
private fun Thumbnail(url: String?, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, url) {
        value = url?.let { withContext(Dispatchers.IO) { runCatching { URI(it).toURL().openStream().use { s -> BitmapFactory.decodeStream(s)?.asImageBitmap() } }.getOrNull() } }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}
