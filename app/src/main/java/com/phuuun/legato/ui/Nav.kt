package com.phuuun.legato.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.phuuun.legato.ui.converter.ConverterState
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phuuun.legato.library.BookmarkStore
import com.phuuun.legato.library.FolderKind
import com.phuuun.legato.converter.DownloadQueue
import com.phuuun.legato.library.Book
import com.phuuun.legato.library.FolderStore
import com.phuuun.legato.library.ProgressStore
import com.phuuun.legato.library.chapterStart
import com.phuuun.legato.library.resumePoint
import com.phuuun.legato.library.asAlbum
import com.phuuun.legato.library.edit.CoverChange
import com.phuuun.legato.library.edit.TagWriter
import com.phuuun.legato.library.edit.fixRows
import com.phuuun.legato.library.edit.properties
import com.phuuun.legato.library.edit.renumber
import com.phuuun.legato.library.groupBooks
import com.phuuun.legato.library.LibraryRepository
import com.phuuun.legato.library.PlaylistRepository
import com.phuuun.legato.library.Track
import com.phuuun.legato.playback.PlayerConnection
import com.phuuun.legato.ui.audiobooks.AudiobooksScreen
import com.phuuun.legato.ui.audiobooks.ResumeButton
import com.phuuun.legato.ui.converter.ConverterScreen
import com.phuuun.legato.ui.edit.BulkFixScreen
import com.phuuun.legato.ui.edit.EditScreen
import com.phuuun.legato.ui.components.MiniPlayer
import com.phuuun.legato.ui.library.AlbumScreen
import com.phuuun.legato.ui.library.ArtistScreen
import com.phuuun.legato.ui.library.LibraryScreen
import com.phuuun.legato.ui.library.LibraryViewModel
import com.phuuun.legato.ui.library.TrackActions
import com.phuuun.legato.ui.nowplaying.NowPlayingSheet
import com.phuuun.legato.ui.nowplaying.NowPlayingViewModel
import com.phuuun.legato.ui.playlists.AddToPlaylistSheet
import com.phuuun.legato.ui.playlists.PlaylistScreen
import com.phuuun.legato.ui.playlists.PlaylistsList
import com.phuuun.legato.ui.settings.FoldersPane
import com.phuuun.legato.ui.settings.HiddenFiles
import com.phuuun.legato.ui.library.PlayShuffle
import com.phuuun.legato.library.inFolders
import com.phuuun.legato.ui.settings.SettingsScreen
import com.phuuun.legato.ui.settings.SettingsSection
import kotlinx.coroutines.launch
import android.text.format.DateUtils
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.phuuun.legato.library.Backups
import com.phuuun.legato.library.backupFromJson
import com.phuuun.legato.ui.settings.BackupPane
import com.phuuun.legato.ui.settings.EqPane
import com.phuuun.legato.playback.EqStore
import com.phuuun.legato.ui.playlists.SmartList
import com.phuuun.legato.ui.playlists.SmartPlaylistScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import com.phuuun.legato.library.Sort
import com.phuuun.legato.library.sortedFor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import com.phuuun.legato.converter.DownloadJob
import com.phuuun.legato.converter.JobState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import com.phuuun.legato.ui.components.LegatoHeader
import com.phuuun.legato.ui.components.offerUndo
import com.phuuun.legato.ui.components.screen
import com.phuuun.legato.ui.library.SearchField
import kotlin.math.roundToInt

private enum class Section(val label: String, val icon: ImageVector) {
    Music("MUSIC", Icons.Outlined.MusicNote),
    Audiobooks("AUDIOBOOKS", Icons.Outlined.Headphones),
    Converter("CONVERTER", Icons.Outlined.Download),
}

// Pages open by sliding in from the right and close by sliding back out; the page underneath drifts and fades.
private const val SLIDE_MS = 280
private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS))
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS), targetOffset = { it / 4 }) + fadeOut(tween(SLIDE_MS))
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS), initialOffset = { it / 4 }) + fadeIn(tween(SLIDE_MS))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS))
}

/**
 * The Converter tab's icon, so downloads show from any section: a ring with the current file's progress (spinning
 * while it's tagged), a count when more are waiting, and a red dot when something failed and nothing is running.
 */
@Composable
private fun DownloadsIcon(icon: ImageVector, jobs: List<DownloadJob>) {
    val left = jobs.count { it.state !is JobState.Done && it.state !is JobState.Failed }
    val current = jobs.firstOrNull { it.state is JobState.Downloading || it.state == JobState.Tagging }?.state
    val percent by animateFloatAsState((current as? JobState.Downloading)?.percent?.div(100f) ?: 0f, label = "download")
    BadgedBox(badge = {
        when {
            left > 1 -> Badge(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background) { Text("$left") }
            left == 0 && jobs.any { it.state is JobState.Failed } -> Badge()
        }
    }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
            val ring = Modifier.requiredSize(34.dp)
            when (current) {
                is JobState.Downloading -> CircularProgressIndicator(progress = { percent }, ring, strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onBackground)
                JobState.Tagging -> CircularProgressIndicator(ring, strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onBackground)
                else -> Unit
            }
        }
    }
}

/** Beside search: title, artist (author, on Audiobooks), newest first or by plays, for songs, albums and books alike. */
@Composable
private fun SortButton(sort: Sort, books: Boolean, onPick: (Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 8.dp)) {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort") }
        DropdownMenu(open, { open = false }) {
            listOf(Sort.Title to "Title A–Z", Sort.Artist to if (books) "Author A–Z" else "Artist A–Z", Sort.Recent to "Recently added", Sort.Plays to "Most played", Sort.Played to "Recently played").forEach { (s, label) ->
                DropdownMenuItem(
                    text = { Text(label) }, onClick = { onPick(s); open = false },
                    trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                )
            }
        }
    }
}

@Composable
private fun SlidingNavHost(nav: NavHostController, start: String, modifier: Modifier = Modifier, builder: NavGraphBuilder.() -> Unit) =
    NavHost(nav, start, modifier, enterTransition = enter, exitTransition = exit, popEnterTransition = popEnter, popExitTransition = popExit, builder = builder)

/**
 * Music, Audiobooks and Converter are pages you swipe between (or tap in the bottom bar). Music and Audiobooks
 * each keep their own back stack, held up here so an open album survives swiping away and back. Settings,
 * Edit and Bulk fix are full screens above the pages.
 */
@Composable
fun LegatoNav(library: LibraryRepository, audiobooks: LibraryRepository, player: PlayerConnection, playlistRepo: PlaylistRepository, folders: FolderStore, tagWriter: TagWriter, progress: ProgressStore, bookmarks: BookmarkStore, backups: Backups, eq: EqStore, downloads: DownloadQueue,
    sharedLink: String?, onSharedLinkUsed: () -> Unit, openPlayer: Boolean, onPlayerOpened: () -> Unit,
) {
    val root = rememberNavController()
    val musicNav = rememberNavController()
    val bookNav = rememberNavController()
    val pager = rememberPagerState { Section.entries.size }
    // One view model for every destination, so the loaded library and the search box survive navigation.
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(library, folders.sort, playlistRepo.stats) } })
    val state by vm.state.collectAsState()
    val nowPlayingVm: NowPlayingViewModel = viewModel(factory = viewModelFactory { initializer { NowPlayingViewModel(player.controller, player.sleep, bookmarks) } })
    val now by nowPlayingVm.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Like YT Music: picking a song opens the full player; swiping it down leaves the mini player.
    val onPlay: (List<Track>, Int) -> Unit = { tracks, i -> player.play(tracks, i); expanded = true }
    val context = LocalContext.current
    // Writes and fetches run in this activity-long scope, so leaving a screen mid-write doesn't cancel it.
    val scope = rememberCoroutineScope()
    val converter = remember { ConverterState(scope) }
    val playlists by playlistRepo.playlists.collectAsState(emptyList())
    val jobs by downloads.jobs.collectAsState()
    var adding by remember { mutableStateOf<List<Track>?>(null) }
    val toast = { text: String -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    val snackbar = remember { SnackbarHostState() }
    val undo = { text: String, onUndo: () -> Unit -> scope.launch { snackbar.offerUndo(text, onUndo) }; Unit }
    val onSettings = { root.navigate("settings") { launchSingleTop = true } }
    val toSection = { s: Section ->
        if (root.currentDestination?.route != "main") root.popBackStack("main", inclusive = false)
        scope.launch { pager.animateScrollToPage(s.ordinal) }
    }
    // A link shared to Legato jumps to the converter, which takes it from there.
    LaunchedEffect(sharedLink) { if (sharedLink != null) toSection(Section.Converter) }
    // A backup from before this install is offered once; otherwise today's backup is made if it's due.
    var offerRestore by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (backups.offerRestore) offerRestore = true else backups.daily() }
    val backUpNow = { done: () -> Unit ->
        scope.launch { toast(if (runCatching { backups.backUp() }.isSuccess) "Backed up" else "Couldn't back up"); done() }
        Unit
    }
    val restoreFrom = { uri: Uri?, done: () -> Unit ->
        scope.launch {
            val restored = runCatching {
                val text = withContext(Dispatchers.IO) {
                    if (uri == null) backups.file.readText() else context.contentResolver.openInputStream(uri)!!.use { it.reader().readText() }
                }
                backups.restore(backupFromJson(text))
            }.isSuccess
            toast(if (restored) "Restored" else "That file isn't a Legato backup")
            done()
        }
        Unit
    }
    // Tapping the playback notification opens the full player, over wherever the app was.
    LaunchedEffect(openPlayer) { if (openPlayer) { expanded = true; onPlayerOpened() } }
    // Null until the first scan lands, so the Audiobooks tab doesn't flash "No audiobooks" first.
    val audiobookTracks by audiobooks.tracks.collectAsState(null)
    val sort by remember { folders.sort }.collectAsState(Sort.Title)
    val stats by remember { playlistRepo.stats }.collectAsState(emptyMap())
    val bookList = remember(audiobookTracks, sort, stats) { audiobookTracks?.let(::groupBooks)?.sortedFor(sort, stats) }
    val books = bookList.orEmpty()
    val audiobookFolders by remember { folders.folders(FolderKind.Audiobooks) }.collectAsState(null)
    val spots by remember { progress.spots() }.collectAsState(emptyMap())
    // A book picks up where it stopped (less 5 s); a chapter picked by hand picks up at its own spot.
    val playBook = { b: Book -> val (i, ms) = resumePoint(b.chapters, spots); player.play(b.chapters, i, ms, audiobook = true); expanded = true }
    val playChapter: (List<Track>, Int) -> Unit = { chapters, i -> player.play(chapters, i, chapterStart(chapters[i], spots[chapters[i].id]), audiobook = true); expanded = true }
    val actions = remember {
        TrackActions(
            queue = { tracks, next -> player.queue(tracks, next); toast(if (next) "Playing next" else "Added to queue") },
            addToPlaylist = { adding = it },
            edit = { root.navigate("edit/${it.id}") },
            hide = { tracks ->
                val paths = tracks.map { it.path }
                folders.hide(paths)
                undo(if (tracks.size == 1) "Hid ${tracks.single().title}" else "Hid ${tracks.size} files") { folders.unhide(paths) }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // The bar below owns the navigation-bar inset; each screen's frame handles the top (see Modifier.screen).
        // Undo snackbars sit just above the mini player, over whichever screen is showing.
        Box(Modifier.weight(1f)) {
            SlidingNavHost(root, "main", Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars)) {
                composable("main") {
                    Column(Modifier.screen()) {
                        // The name, settings and search stay put while the pages slide under them. Search only means
                        // something for Music and Audiobooks, so it folds away as Converter slides in, in step with the finger.
                        LegatoHeader(onSettings)
                        val toConverter by remember { derivedStateOf { (pager.currentPage + pager.currentPageOffsetFraction - Section.Audiobooks.ordinal).coerceIn(0f, 1f) } }
                        val focus = LocalFocusManager.current
                        LaunchedEffect(pager.settledPage) { if (pager.settledPage == Section.Converter.ordinal) focus.clearFocus() }
                        Row(
                            Modifier.clipToBounds().layout { m, c ->
                                val p = m.measure(c)
                                layout(p.width, (p.height * (1 - toConverter)).roundToInt()) {
                                    p.placeRelativeWithLayer(-(p.width * toConverter).roundToInt(), 0) { alpha = 1 - toConverter }
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchField(
                                state.query,
                                onQuery = { q ->
                                    vm.setQuery(q)
                                    // Results show on a section's first page, so typing over an open album goes back to it.
                                    if (pager.currentPage == Section.Music.ordinal) musicNav.popBackStack("library", inclusive = false)
                                    else bookNav.popBackStack("books", inclusive = false)
                                },
                                Modifier.weight(1f),
                            )
                            SortButton(sort, books = pager.currentPage == Section.Audiobooks.ordinal, folders::setSort)
                        }
                        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                            when (Section.entries[page]) {
                                Section.Music -> SlidingNavHost(musicNav, "library") {
                                    composable("library") {
                                        LibraryScreen(state, onPlay, onAlbum = { musicNav.navigate("album/$it") }, onArtist = { musicNav.navigate("artist/${Uri.encode(it)}") }, actions = actions,
                                            playlistsTab = {
                                        PlaylistsList(
                                            playlists, onOpen = { musicNav.navigate("playlist/$it") }, onSmart = { musicNav.navigate("smart/${it.name}") },
                                            onCreate = { scope.launch { playlistRepo.create(it) } },
                                        )
                                    },
                                        )
                                    }
                                    composable("album/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                                        val id = e.arguments?.getLong("id")
                                        val album = state.albums.firstOrNull { it.id == id }
                                        // A new order goes into the files as track numbers; MediaStore's re-read shows it.
                                        val saveOrder = { tracks: List<Track> ->
                                            scope.launch {
                                                val failed = renumber(tracks).count { (t, p) -> !tagWriter.write(t, p, CoverChange.Keep) }
                                                toast(if (failed == 0) "Order saved" else "Couldn't save $failed of the tracks")
                                            }
                                            Unit
                                        }
                                        AlbumScreen(album, onBack = { musicNav.popBackStack() }, onPlay = onPlay, actions = actions, onReorder = saveOrder) {
                                            if (album != null) PlayShuffle(album.tracks, onPlay, Modifier.padding(top = 12.dp))
                                        }
                                    }
                                    composable("artist/{name}", listOf(navArgument("name") { type = NavType.StringType })) { e ->
                                        val name = e.arguments?.getString("name")
                                        ArtistScreen(state.artists.firstOrNull { it.name == name }, onBack = { musicNav.popBackStack() }, onAlbum = { musicNav.navigate("album/$it") }, onPlay = onPlay, actions = actions)
                                    }
                                    composable("smart/{kind}", listOf(navArgument("kind") { type = NavType.StringType })) { e ->
                                    val kind = SmartList.entries.firstOrNull { it.name == e.arguments?.getString("kind") } ?: SmartList.MostPlayed
                                    val played by remember(kind) { playlistRepo.playedIds(byRecent = kind == SmartList.RecentlyPlayed) }.collectAsState(emptyList())
                                    // Resolved against the music library, so hidden songs and audiobook chapters stay out.
                                    val tracks = remember(kind, played, state.tracks) {
                                        val byId = state.tracks.associateBy { it.id }
                                        if (kind == SmartList.RecentlyAdded) state.tracks.sortedByDescending { it.added }.take(100) else played.mapNotNull { byId[it] }.take(100)
                                    }
                                    SmartPlaylistScreen(kind, tracks, onBack = { musicNav.popBackStack() }, onPlay = onPlay, actions = actions)
                                }
                                composable("playlist/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                                        val id = e.arguments?.getLong("id") ?: 0
                                        val tracks by remember(id) { playlistRepo.tracksOf(id) }.collectAsState(emptyList())
                                        PlaylistScreen(
                                            playlists.firstOrNull { it.id == id }, tracks, onBack = { musicNav.popBackStack() }, onPlay = onPlay, actions = actions,
                                            onRename = { scope.launch { playlistRepo.rename(id, it) } },
                                            onDelete = {
                                                val p = playlists.firstOrNull { it.id == id }
                                                scope.launch {
                                                    val ids = playlistRepo.trackIds(id)
                                                    playlistRepo.delete(id)
                                                    if (p != null) undo("Deleted ${p.name}") { scope.launch { playlistRepo.restore(p, ids) } }
                                                }
                                                musicNav.popBackStack()
                                            },
                                            onRemove = { pos ->
                                                val t = tracks.getOrNull(pos)
                                                scope.launch { playlistRepo.removeAt(id, pos) }
                                                if (t != null) undo("Removed ${t.title}") { scope.launch { playlistRepo.insertAt(id, pos, t.id) } }
                                            },
                                            onMove = { from, to -> scope.launch { playlistRepo.move(id, from, to) } },
                                        )
                                    }
                                }
                                // A one-file book plays straight away; chapter files open as a list, like an album.
                                Section.Audiobooks -> SlidingNavHost(bookNav, "books") {
                                    composable("books") {
                                        AudiobooksScreen(
                                            bookList, spots, audiobookFolders?.isNotEmpty(), state.query, actions = actions, onResume = playBook,
                                            onBook = { b -> if (b.chapters.size == 1) playBook(b) else bookNav.navigate("book/${b.id}") },
                                        )
                                    }
                                    composable("book/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                                        val id = e.arguments?.getLong("id")
                                        val book = books.firstOrNull { it.id == id }
                                        AlbumScreen(book?.asAlbum(), onBack = { bookNav.popBackStack() }, onPlay = playChapter, actions = actions) {
                                            if (book != null) ResumeButton(book, spots) { playBook(book) }
                                        }
                                    }
                                }
                                Section.Converter -> ConverterScreen(converter, sharedLink, onSharedLinkUsed, folders, downloads, state.tracks)
                            }
                        }
                    }
                }
                composable("edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id")
                    val book = books.firstOrNull { b -> b.chapters.any { it.id == id } }
                    val track = book?.chapters?.first { it.id == id } ?: state.tracks.firstOrNull { it.id == id }
                    EditScreen(
                        track, if (book != null) FolderKind.Audiobooks else FolderKind.Music, book?.chapters?.size ?: 1, onBack = { root.popBackStack() },
                        onSave = { properties, cover ->
                            // Pops Edit itself, never the screen below it, in case Edit was already left while saving.
                            scope.launch { if (track != null && tagWriter.write(track, properties, cover)) root.popBackStack("edit/{id}", inclusive = true) else toast("Couldn't save ${track?.title}") }
                        },
                    )
                }
                composable("fix") {
                    val rows = remember { fixRows(state.tracks) }
                    BulkFixScreen(rows, onBack = { root.popBackStack() }, write = { t, e -> tagWriter.write(t, e.properties(), CoverChange.Keep) })
                }
                composable("settings") {
                    SettingsScreen(onBack = { root.popBackStack() }) { section ->
                        when (section) {
                            SettingsSection.Music -> {
                                val f by remember { folders.folders(FolderKind.Music) }.collectAsState(emptySet())
                                val hidden by remember { folders.hidden }.collectAsState(emptySet())
                                FoldersPane(f, "Only files directly in these folders show up. With no folders, all music shows.", { folders.add(FolderKind.Music, it) }, { folders.remove(FolderKind.Music, it) }) {
                                    Text(
                                        "Fix unknown artists (${state.tracks.count { it.artists.isEmpty() }})", style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.fillMaxWidth().clickable { root.navigate("fix") }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                    // Anything not in an audiobook folder counts as music here.
                                    HiddenFiles(hidden.filterNot { inFolders(it, audiobookFolders.orEmpty()) }) { folders.unhide(listOf(it)) }
                                }
                            }
                            SettingsSection.Audiobooks -> {
                                val f by remember { folders.folders(FolderKind.Audiobooks) }.collectAsState(emptySet())
                                val hidden by remember { folders.hidden }.collectAsState(emptySet())
                                FoldersPane(f, null, { folders.add(FolderKind.Audiobooks, it) }, { folders.remove(FolderKind.Audiobooks, it) }) {
                                    HiddenFiles(hidden.filter { inFolders(it, f) }) { folders.unhide(listOf(it)) }
                                }
                            }
                            SettingsSection.Backup -> BackupPane(backups, backUpNow, restoreFrom)
                        SettingsSection.Sound -> EqPane(eq)
                        SettingsSection.Converter -> Text("Downloads go to the folder you pick in the Converter. Add more folders under Music and Audiobooks.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        // The mini player slides up when something starts and away when the queue empties; it shows the last
        // track while it leaves, so it doesn't blank out mid-animation.
        var shown by remember { mutableStateOf(now) }
        LaunchedEffect(now) { if (now != null) shown = now }
        AnimatedVisibility(now != null, enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(), exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
            (now ?: shown)?.let { MiniPlayer(it, nowPlayingVm::togglePlay, nowPlayingVm::skip, onOpen = { expanded = true }) }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
            Section.entries.forEach { s ->
                val selected = pager.currentPage == s.ordinal
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        // Tapping the open section again goes back to its first page.
                        if (selected && root.currentDestination?.route == "main") when (s) {
                            Section.Music -> musicNav.popBackStack("library", inclusive = false)
                            Section.Audiobooks -> bookNav.popBackStack("books", inclusive = false)
                            Section.Converter -> Unit
                        } else toSection(s)
                    },
                    icon = { if (s == Section.Converter) DownloadsIcon(s.icon, jobs) else Icon(s.icon, contentDescription = null) },
                    label = { Text(s.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onBackground, selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
    now?.let { if (expanded) NowPlayingSheet(it, nowPlayingVm, onDismiss = { expanded = false }, onEdit = { id -> root.navigate("edit/$id") }) }
    if (offerRestore) AlertDialog(
        onDismissRequest = { offerRestore = false; backups.decline() },
        title = { Text("Restore your backup?") },
        text = { Text("There's a Legato backup from ${DateUtils.getRelativeTimeSpanString(backups.file.lastModified())} in Downloads: playlists, bookmarks, listening progress and folders.") },
        confirmButton = { TextButton(onClick = { offerRestore = false; restoreFrom(null) {} }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { offerRestore = false; backups.decline() }) { Text("Not now") } },
    )
    adding?.let { tracks ->
        val ids = tracks.map { it.id }
        AddToPlaylistSheet(
            playlists,
            onPick = { p -> scope.launch { playlistRepo.addTracks(p.id, ids) }; toast("Added to ${p.name}") },
            onCreate = { name -> scope.launch { playlistRepo.addTracks(playlistRepo.create(name), ids) }; toast("Added to $name") },
            onDismiss = { adding = null },
        )
    }
}
