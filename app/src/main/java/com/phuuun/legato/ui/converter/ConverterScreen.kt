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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phuuun.legato.ui.components.screen
import com.phuuun.legato.converter.DownloadJob
import com.phuuun.legato.converter.PickedVideo
import com.phuuun.legato.converter.Playlist
import com.phuuun.legato.converter.YouTubeLink
import com.phuuun.legato.converter.albumJobs
import com.phuuun.legato.converter.bookJobs
import com.phuuun.legato.converter.findBook
import com.phuuun.legato.converter.findDuplicate
import com.phuuun.legato.converter.guessAlbum
import com.phuuun.legato.converter.isAlbumPlaylist
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
import com.phuuun.legato.converter.youTubeError
import com.phuuun.legato.library.FolderKind
import com.phuuun.legato.library.FolderStore
import com.phuuun.legato.library.Track
import com.phuuun.legato.ui.edit.Field
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import com.phuuun.legato.ui.components.BoldMenu
import com.phuuun.legato.ui.components.OutlinePill
import com.phuuun.legato.ui.components.PillButton
import com.phuuun.legato.ui.components.PillSwitch
import com.phuuun.legato.ui.components.RoundButton
import com.phuuun.legato.ui.components.SmallPill
import com.phuuun.legato.ui.theme.Bold
import com.phuuun.legato.ui.theme.Motion
import com.phuuun.legato.ui.theme.enterFrom
import com.phuuun.legato.ui.theme.shake
import com.phuuun.legato.ui.theme.tap
import com.phuuun.legato.ui.nowplaying.formatTime
import com.phuuun.legato.ui.settings.rememberFolderPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

internal sealed interface Fetch {
    data object Idle : Fetch
    data object Loading : Fetch
    data class Error(val message: String) : Fetch
    /** A video opened from inside a playlist: which one did they mean? */
    data class Ask(val link: YouTubeLink) : Fetch
    data class One(val video: Video) : Fetch
    data class Many(val playlist: Playlist, val choices: PlaylistChoices) : Fetch
}

/** What a playlist becomes and the names it gets. Kept with the preview, so scrolling and swiping away keep them. */
internal class PlaylistChoices(playlist: Playlist, album: Boolean, artists: List<String>) {
    private val albumGuess = guessAlbum(playlist.name, artists)
    private val book = guessBook(playlist.name)

    /** A YouTube Music album opens on ALBUM. */
    var mode by mutableStateOf(if (album) PlaylistAs.Album else PlaylistAs.Songs)
    var albumTitle by mutableStateOf(albumGuess.title)
    var albumArtist by mutableStateOf(albumGuess.artist)
    var bookTitle by mutableStateOf(book.title)
    var author by mutableStateOf(book.author)
    var narrator by mutableStateOf("")

    /** The folder picked by hand; until then, the last one used. */
    var folder by mutableStateOf<String?>(null)
}

/**
 * The converter's link and preview. It lives above the swipeable pages, so swiping away and back keeps a
 * fetched preview, and a fetch keeps going while the page is off screen.
 */
class ConverterState(private val scope: CoroutineScope) {
    var link by mutableStateOf("")
    internal var fetch by mutableStateOf<Fetch>(Fetch.Idle)
        private set

    /** A playlist's rows with their (editable) guesses. */
    val picks = mutableStateListOf<PickedVideo>()

    fun loadVideo(id: String) {
        fetch = Fetch.Loading
        scope.launch { fetch = try { Fetch.One(YouTube.video(videoUrl(id))) } catch (e: Exception) { Fetch.Error(youTubeError(e, "Couldn't load that video")) } }
    }

    fun loadPlaylist(id: String) {
        fetch = Fetch.Loading
        scope.launch {
            fetch = try {
                val playlist = YouTube.playlist(playlistUrl(id))
                picks.clear()
                picks += playlist.videos.map { v -> guessSong(v.title, v.uploader).let { PickedVideo(v, it.artist, it.title, true) } }
                Fetch.Many(playlist, PlaylistChoices(playlist, isAlbumPlaylist(id), picks.map { it.artist }))
            } catch (e: Exception) {
                Fetch.Error(youTubeError(e, "Couldn't load that playlist"))
            }
        }
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

    fun clear() {
        fetch = Fetch.Idle
        link = ""
        picks.clear()
    }
}

/** Link in, preview with guessed tags, pick where it goes, download; the queue below shows what's happening. */
@Composable
fun ConverterScreen(state: ConverterState, initialLink: String?, onInitialLinkUsed: () -> Unit, folders: FolderStore, downloads: DownloadQueue, library: List<Track>, audiobooks: List<Track>) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // The clipboard is read only when the paste button is tapped, never on its own.
    val copied = suspend { clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() }
    var link by state::link
    val fetch = state.fetch
    val picks = state.picks
    val jobs by downloads.jobs.collectAsState()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val load = state::load
    val loadVideo = state::loadVideo
    val loadPlaylist = state::loadPlaylist
    LaunchedEffect(initialLink) {
        if (initialLink != null) { link = initialLink; load(initialLink); onInitialLinkUsed() }
    }

    fun queue(batch: List<DownloadJob>, kind: FolderKind) {
        if (batch.isEmpty()) return
        folders.setLastFolder(kind, batch.first().folder)
        downloads.add(batch)
        DownloadService.start(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        state.clear()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    Field("YouTube link", link, onRemove = null) { link = it }
                }
                RoundButton("Paste", { scope.launch { copied()?.let { link = it; load(it) } } }) { Icon(Icons.Rounded.ContentPaste, contentDescription = null, Modifier.size(20.dp)) }
            }
            PillButton("Fetch", { load(link) }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), enabled = link.isNotBlank(), loading = fetch == Fetch.Loading)
            Text(
                "Only save what you have the right to keep: your own uploads, or recordings that are Creative Commons or public domain.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item {
            when (val f = fetch) {
                Fetch.Idle -> Column(Modifier.padding(20.dp)) {
                    Text(
                        "Paste a YouTube link, or share one to Legato from the YouTube app.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The Fetch button spins meanwhile.
                Fetch.Loading -> Unit
                is Fetch.Error -> Text(f.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp).shake(f))
                is Fetch.Ask -> Column(Modifier.padding(20.dp).enterFrom(offsetY = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This video is part of a playlist.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinePill("Just this video", { loadVideo(f.link.video!!) }, Modifier.weight(1f))
                        OutlinePill("Whole playlist", { loadPlaylist(f.link.playlist!!) }, Modifier.weight(1f))
                    }
                }
                is Fetch.One -> VideoCard(f.video, folders, downloads, library, audiobooks) { batch, kind -> queue(batch, kind) }
                is Fetch.Many -> Unit
            }
        }
        (fetch as? Fetch.Many)?.let { playlist(it.playlist, it.choices, picks, folders, downloads, library) { batch, kind -> queue(batch, kind) } }
        if (jobs.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("DOWNLOADS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    SmallPill("Clear finished", downloads::clearFinished, Modifier.padding(end = 12.dp))
                }
            }
            items(jobs.asReversed(), key = { it.id }) { job -> Box(Modifier.animateItem()) { JobRow(job, onRetry = { downloads.retry(job.id); DownloadService.start(context) }) } }
        }
    }
}

@Composable
private fun VideoCard(video: Video, folders: FolderStore, downloads: DownloadQueue, library: List<Track>, audiobooks: List<Track>, onDownload: (List<DownloadJob>, FolderKind) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(FolderKind.Music) }
    val song = remember(video) { guessSong(video.title, video.uploader) }
    val book = remember(video) { guessBook(video.title) }
    var artist by rememberSaveable(video) { mutableStateOf(song.artist) }
    var title by rememberSaveable(video) { mutableStateOf(song.title) }
    var bookTitle by rememberSaveable(video) { mutableStateOf(book.title) }
    var author by rememberSaveable(video) { mutableStateOf(book.author) }
    var narrator by rememberSaveable(video) { mutableStateOf("") }
    var replace by rememberSaveable(video) { mutableStateOf(true) }
    val oldBook = remember(bookTitle, audiobooks) { findBook(bookTitle, audiobooks) }
    val choices by remember(kind) { folders.folders(kind) }.collectAsState(emptySet())
    var folder by remember(kind, choices) { mutableStateOf(folders.lastFolder(kind)?.takeIf { it in choices } ?: choices.sorted().firstOrNull()) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).enterFrom(scale = 0.96f, offsetY = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
        Column {
            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${video.uploader} · ${formatTime(video.durationSec * 1000)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        KindToggle(kind) { kind = it }
        if (kind == FolderKind.Music) {
            Field("Artist", artist) { artist = it }
            Field("Title", title) { title = it }
            ReplaceBox(remember(artist, title, library) { findDuplicate(artist, title, library) }, replace) { replace = it }
        } else {
            Field("Book title", bookTitle) { bookTitle = it }
            Field("Author", author) { author = it }
            Field("Narrator", narrator) { narrator = it }
            ReplaceBox(oldBook, replace) { replace = it }
        }
        Destination(kind, choices, folder, onPick = { folder = it }, onAdd = { folders.add(kind, it); folder = it })
        PillButton(
            "Download",
            enabled = folder != null,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            onClick = {
                val dest = folder ?: return@PillButton
                onDownload(
                    if (kind == FolderKind.Music) musicJobs(listOf(PickedVideo(video, artist, title, true, replace)), dest, downloads.nextId(), library)
                    else bookJobs(listOf(video), bookTitle, author, narrator, dest, downloads.nextId(), oldBook?.takeIf { replace }),
                    kind,
                )
            },
        )
    }
}

@Composable
private fun KindToggle(kind: FolderKind, onKind: (FolderKind) -> Unit) {
    val position by animateFloatAsState(kind.ordinal.toFloat(), Motion.settle(), label = "kind")
    PillSwitch(FolderKind.entries.map { if (it == FolderKind.Music) "Music" else "Audiobook" }, position, { onKind(FolderKind.entries[it]) }, Modifier.fillMaxWidth())
}

/** What a playlist becomes: loose songs, one album, or one audiobook. */
internal enum class PlaylistAs(val label: String, val kind: FolderKind) { Songs("Music", FolderKind.Music), Album("Album", FolderKind.Music), Book("Audiobook", FolderKind.Audiobooks) }

/**
 * A playlist as list items: header with MUSIC / ALBUM / AUDIOBOOK, one row per video (ticked, with its guess for
 * music), then where to save and "Download N". An album or audiobook playlist is one album or book: one title for all.
 * Each video is its own item, so a long playlist only builds the rows on screen.
 */
private fun LazyListScope.playlist(
    playlist: Playlist, choices: PlaylistChoices, picks: SnapshotStateList<PickedVideo>, folders: FolderStore, downloads: DownloadQueue, library: List<Track>, onDownload: (List<DownloadJob>, FolderKind) -> Unit,
) {
    item(key = "playlist-head") { PlaylistHead(playlist, choices) }
    itemsIndexed(picks) { i, p -> PickRow(i, p, choices.mode.kind, picks, library) }
    item(key = "playlist-foot") { PlaylistFoot(choices, picks, folders, downloads, library, onDownload) }
}

@Composable
private fun PlaylistHead(playlist: Playlist, choices: PlaylistChoices) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).enterFrom(scale = 0.96f, offsetY = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
            Text("${playlist.videos.size} videos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val position by animateFloatAsState(choices.mode.ordinal.toFloat(), Motion.settle(), label = "playlist as")
        PillSwitch(PlaylistAs.entries.map { it.label }, position, { choices.mode = PlaylistAs.entries[it] }, Modifier.fillMaxWidth())
        if (choices.mode == PlaylistAs.Album) {
            Field("Album", choices.albumTitle) { choices.albumTitle = it }
            Field("Album artist", choices.albumArtist) { choices.albumArtist = it }
        }
        if (choices.mode.kind == FolderKind.Audiobooks) {
            Field("Book title", choices.bookTitle) { choices.bookTitle = it }
            Field("Author", choices.author) { choices.author = it }
            Field("Narrator", choices.narrator) { choices.narrator = it }
        }
    }
}

@Composable
private fun PickRow(i: Int, p: PickedVideo, kind: FolderKind, picks: SnapshotStateList<PickedVideo>, library: List<Track>) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Checkbox(p.include, onCheckedChange = { picks[i] = p.copy(include = it) })
        Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${i + 1}. ${p.video.title} · ${formatTime(p.video.durationSec * 1000)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (kind == FolderKind.Music && p.include) {
                Field("Artist", p.artist) { picks[i] = p.copy(artist = it) }
                Field("Title", p.title) { picks[i] = p.copy(title = it) }
                ReplaceBox(remember(p.artist, p.title, library) { findDuplicate(p.artist, p.title, library) }, p.replace) { picks[i] = p.copy(replace = it) }
            }
        }
    }
}

@Composable
private fun PlaylistFoot(choices: PlaylistChoices, picks: SnapshotStateList<PickedVideo>, folders: FolderStore, downloads: DownloadQueue, library: List<Track>, onDownload: (List<DownloadJob>, FolderKind) -> Unit) {
    val mode = choices.mode
    val kind = mode.kind
    val options by remember(kind) { folders.folders(kind) }.collectAsState(emptySet())
    val folder = choices.folder?.takeIf { it in options } ?: folders.lastFolder(kind)?.takeIf { it in options } ?: options.sorted().firstOrNull()
    val ticked = picks.count { it.include }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Destination(kind, options, folder, onPick = { choices.folder = it }, onAdd = { folders.add(kind, it); choices.folder = it })
        PillButton(
            "Download $ticked",
            enabled = folder != null && ticked > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            onClick = {
                val dest = folder ?: return@PillButton
                onDownload(
                    when (mode) {
                        PlaylistAs.Songs -> musicJobs(picks, dest, downloads.nextId(), library)
                        PlaylistAs.Album -> albumJobs(picks, choices.albumTitle, choices.albumArtist, dest, downloads.nextId(), library)
                        PlaylistAs.Book -> bookJobs(picks.filter { it.include }.map { it.video }, choices.bookTitle, choices.author, choices.narrator, dest, downloads.nextId())
                    },
                    kind,
                )
            },
        )
    }
}

/** Shown only when the song or book is already on the phone: ticked, the download takes its place and the old file is hidden (see DownloadService). */
@Composable
private fun ReplaceBox(old: Track?, replace: Boolean, onReplace: (Boolean) -> Unit) {
    old ?: return
    Row(Modifier.tap({ onReplace(!replace) }), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(replace, onCheckedChange = onReplace)
        Text(
            if (replace) "Replaces ${old.path.substringAfterLast('/')}" else "Keeps ${old.path.substringAfterLast('/')} too",
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Where the file goes: one of this kind's folders from Settings, or, with none yet, a button to add one right here. */
@Composable
private fun Destination(kind: FolderKind, choices: Set<String>, folder: String?, onPick: (String) -> Unit, onAdd: (String) -> Unit) {
    val pick = rememberFolderPicker(onAdd)
    var open by remember { mutableStateOf(false) }
    if (choices.isEmpty()) {
        SmallPill("Add folder", pick, icon = Icons.Rounded.Add)
        Text(
            "Pick where ${if (kind == FolderKind.Music) "music" else "audiobooks"} should go. It's added to Settings too.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Box {
        Row(Modifier.tap({ open = true }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Save to ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(folder?.trimEnd('/') ?: "", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose folder")
        }
        BoldMenu(open, { open = false }) {
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
                val shown by animateFloatAsState(s.percent / 100f, Motion.settle(), label = "job")
                LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = Bold.Mint, trackColor = Bold.QuietLine)
            }
            JobState.Tagging -> Text("Saving…", style = MaterialTheme.typography.bodyMedium, color = muted)
            JobState.Done -> Text("Saved to ${job.folder.trimEnd('/')}", style = MaterialTheme.typography.bodyMedium, color = muted)
            is JobState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                SmallPill("Retry", onRetry, Modifier.padding(start = 8.dp))
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
