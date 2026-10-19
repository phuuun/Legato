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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
import com.phuuun.legato.converter.PickedVideo
import com.phuuun.legato.converter.Playlist
import com.phuuun.legato.converter.YouTubeLink
import com.phuuun.legato.converter.bookJobs
import com.phuuun.legato.converter.musicJobs
import com.phuuun.legato.converter.playlistUrl
import com.phuuun.legato.converter.DownloadQueue
import com.phuuun.legato.converter.DownloadService
import com.phuuun.legato.converter.JobState
import com.phuuun.legato.converter.Video
import com.phuuun.legato.converter.YouTube
import com.phuuun.legato.converter.guessBook
import com.phuuun.legato.converter.guessSong
import com.phuuun.legato.converter.parseYouTube
import com.phuuun.legato.converter.videoUrl
import com.phuuun.legato.library.FolderKind
import com.phuuun.legato.library.FolderStore
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
    /** A video opened from inside a playlist: which one did they mean? */
    data class Ask(val link: YouTubeLink) : Fetch
    data class One(val video: Video) : Fetch
    data class Many(val playlist: Playlist) : Fetch
}

/** Link in, preview with guessed tags, pick where it goes, download; the queue below shows what's happening. */
@Composable
fun ConverterScreen(initialLink: String?, onInitialLinkUsed: () -> Unit, folders: FolderStore, downloads: DownloadQueue, onSettings: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var link by rememberSaveable { mutableStateOf(initialLink.orEmpty()) }
    var fetch by remember { mutableStateOf<Fetch>(Fetch.Idle) }
    val jobs by downloads.jobs.collectAsState()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // The playlist's rows live up here: the list is long, so its rows are LazyColumn items, not one big card.
    val picks = remember(fetch) {
        (fetch as? Fetch.Many)?.playlist?.videos.orEmpty().map { v -> guessSong(v.title, v.uploader).let { PickedVideo(v, it.artist, it.title, true) } }.toMutableStateList()
    }

    fun loadVideo(id: String) {
        fetch = Fetch.Loading
        scope.launch { fetch = try { Fetch.One(YouTube.video(videoUrl(id))) } catch (e: Exception) { Fetch.Error(e.message ?: "Couldn't load that video") } }
    }
    fun loadPlaylist(id: String) {
        fetch = Fetch.Loading
        scope.launch { fetch = try { Fetch.Many(YouTube.playlist(playlistUrl(id))) } catch (e: Exception) { Fetch.Error(e.message ?: "Couldn't load that playlist") } }
    }
    fun load(text: String) {
        val parsed = parseYouTube(text)
        when {
            parsed == null -> fetch = Fetch.Error("That isn't a YouTube link")
            parsed.video != null && parsed.playlist != null -> fetch = Fetch.Ask(parsed)
            parsed.video != null -> loadVideo(parsed.video)
            else -> loadPlaylist(parsed.playlist!!)
        }
    }
    LaunchedEffect(initialLink) {
        if (initialLink != null) { link = initialLink; load(initialLink); onInitialLinkUsed() }
    }
    // Opening the tab with a YouTube link already copied offers it; the clipboard is only read here, never in the background.
    var suggestion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (initialLink == null && link.isBlank()) suggestion = clipboard.getText()?.text?.takeIf { parseYouTube(it) != null }
    }

    fun queue(batch: List<DownloadJob>, kind: FolderKind) {
        if (batch.isEmpty()) return
        folders.setLastFolder(kind, batch.first().folder)
        downloads.add(batch)
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
                Fetch.Idle -> Column(Modifier.padding(20.dp)) {
                    suggestion?.let { s ->
                        TextButton(onClick = { link = s; suggestion = null; load(s) }) { Text("Paste $s", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Text(
                        "Paste a YouTube link, or share one to Legato from the YouTube app.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Fetch.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is Fetch.Error -> Text(f.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
                is Fetch.Ask -> Column(Modifier.padding(20.dp)) {
                    Text("This video is part of a playlist.", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { loadVideo(f.link.video!!) }) { Text("Just this video") }
                        TextButton(onClick = { loadPlaylist(f.link.playlist!!) }) { Text("Whole playlist") }
                    }
                }
                is Fetch.One -> VideoCard(f.video, folders, downloads) { batch, kind -> queue(batch, kind) }
                is Fetch.Many -> Unit
            }
        }
        (fetch as? Fetch.Many)?.let { playlist(it.playlist, picks, folders, downloads) { batch, kind -> queue(batch, kind) } }
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
private fun VideoCard(video: Video, folders: FolderStore, downloads: DownloadQueue, onDownload: (List<DownloadJob>, FolderKind) -> Unit) {
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
        KindToggle(kind) { kind = it }
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
                onDownload(
                    if (kind == FolderKind.Music) musicJobs(listOf(PickedVideo(video, artist, title, true)), dest, downloads.nextId())
                    else bookJobs(listOf(video), bookTitle, author, narrator, dest, downloads.nextId()),
                    kind,
                )
            },
        ) { Text("Download") }
    }
}

@Composable
private fun KindToggle(kind: FolderKind, onKind: (FolderKind) -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        FolderKind.entries.forEach { k ->
            Text(
                if (k == FolderKind.Music) "MUSIC" else "AUDIOBOOK", style = MaterialTheme.typography.labelSmall,
                fontWeight = if (k == kind) FontWeight.Bold else FontWeight.Normal,
                color = if (k == kind) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onKind(k) }.padding(vertical = 8.dp),
            )
        }
    }

/**
 * A playlist as list items: header with MUSIC / AUDIOBOOK, one row per video (ticked, with its guess for
 * music), then where to save and "Download N". An audiobook playlist is one book: one title for all.
 */
private fun LazyListScope.playlist(
    playlist: Playlist, picks: SnapshotStateList<PickedVideo>, folders: FolderStore, downloads: DownloadQueue, onDownload: (List<DownloadJob>, FolderKind) -> Unit,
) {
    item(key = "playlist-head") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
            Text("${playlist.videos.size} videos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item(key = "playlist-form") { PlaylistForm(playlist, picks, folders, downloads, onDownload) }
}

@Composable
private fun PlaylistForm(playlist: Playlist, picks: SnapshotStateList<PickedVideo>, folders: FolderStore, downloads: DownloadQueue, onDownload: (List<DownloadJob>, FolderKind) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(FolderKind.Music) }
    val book = remember(playlist) { guessBook(playlist.name) }
    var bookTitle by remember(playlist) { mutableStateOf(book.title) }
    var author by remember(playlist) { mutableStateOf(book.author) }
    var narrator by remember(playlist) { mutableStateOf("") }
    val choices by remember(kind) { folders.folders(kind) }.collectAsState(emptySet())
    var folder by remember(kind, choices) { mutableStateOf(folders.lastFolder(kind)?.takeIf { it in choices } ?: choices.sorted().firstOrNull()) }
    val ticked = picks.count { it.include }

    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KindToggle(kind) { kind = it }
        if (kind == FolderKind.Audiobooks) {
            Field("Book title", bookTitle) { bookTitle = it }
            Field("Author", author) { author = it }
            Field("Narrator", narrator) { narrator = it }
        }
        picks.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(p.include, onCheckedChange = { picks[i] = p.copy(include = it) })
                Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${i + 1}. ${p.video.title} · ${formatTime(p.video.durationSec * 1000)}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (kind == FolderKind.Music && p.include) {
                        Field("Artist", p.artist) { picks[i] = p.copy(artist = it) }
                        Field("Title", p.title) { picks[i] = p.copy(title = it) }
                    }
                }
            }
        }
        Destination(kind, choices, folder, onPick = { folder = it }, onAdd = { folders.add(kind, it); folder = it })
        Button(
            enabled = folder != null && ticked > 0,
            onClick = {
                val dest = folder ?: return@Button
                onDownload(
                    if (kind == FolderKind.Music) musicJobs(picks, dest, downloads.nextId())
                    else bookJobs(picks.filter { it.include }.map { it.video }, bookTitle, author, narrator, dest, downloads.nextId()),
                    kind,
                )
            },
        ) { Text("Download $ticked") }
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
