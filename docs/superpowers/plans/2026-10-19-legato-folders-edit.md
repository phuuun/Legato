# Legato phase 2 implementation plan: folders, audiobooks list, edit

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Choose music and audiobook folders in a two-pane Settings, list audiobooks as books, and edit tags and covers in the files themselves, including a bulk fix for the 38 Unknown-artist rips.

**Architecture:**
- MediaStore stays the index. Folder lists from SharedPreferences filter it by exact `RELATIVE_PATH`: one `MediaStoreLibrary` instance per scope (music, audiobooks, and all music for playlists).
- Tags are written in place with TagLib (`io.github.kyant0:taglib`) through a read-write file descriptor, then `MediaScannerConnection` refreshes the row. The MediaStore id survives, so playlists, stats and queue ids are unaffected.
- Covers are per file, loaded through a Coil fetcher over `ContentResolver.loadThumbnail`.

**Tech Stack:** Kotlin, Compose Material3, Media3, MediaStore, Room (existing), Coil 3, TagLib (`io.github.kyant0:taglib:1.0.6`), JUnit and Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-19-legato-folders-edit-design.md`

## Global Constraints

- **Versions:** `minSdk` 30 (was 24). compile/target SDK 37, AGP 9.1.1, Kotlin 2.4.20, JVM target 11. Single `:app` module, no DI framework.
- **TagLib:** `io.github.kyant0:taglib:1.0.6`, with `ndk { abiFilters += "arm64-v8a" }` in `defaultConfig`.
- **Permission:** all files access (`MANAGE_EXTERNAL_STORAGE`), checked with `Environment.isExternalStorageManager()`.
- **Folders:**
  - Stored as relative paths with a trailing slash (`Download/MUSIC/`), in SharedPreferences `settings`, keys `music_folders` and `audiobook_folders`.
  - Matching is exact: subfolders never count.
  - No music folders means all music (`IS_MUSIC != 0`). No audiobook folders means no audiobooks.
- **Tags:**
  - `TITLE`.
  - `ARTIST`: artists joined with `"; "`. For an audiobook, the author.
  - `ALBUM`: the book title for audiobooks.
  - `ALBUMARTIST`: the author, for audiobooks.
  - `COMPOSER`: the narrator.
  - A blank value removes the key.
  - Cover: one `"Front Cover"` JPEG, long side ≤ 1000px.
  - When reading, the artist tag is split on `;`.
- **Copy, exact:**
  - The header is "Legato".
  - Music hint: "Only files directly in these folders show up. With no folders, all music shows."
  - Converter pane: "Comes with the converter."
  - Non-internal folder: "Only internal storage for now".
  - Audiobooks empty state: "Add an audiobook folder in Settings".
  - Failed save: "Couldn't save <title>".
  - Menu item: "Edit info…".
  - Settings link: "Fix unknown artists (N)".
- **Theme:** monochrome. Selected rail and tab items are bold `onBackground`, others `onSurfaceVariant`. No new colours.
- **Build env:** `export JAVA_HOME=/home/phuuun/Projects/Tools/android-studio/jbr`. adb is at `/home/phuuun/Projects/Tools/Android/Sdk/platform-tools/adb` and the phone is `943f15`. Install with `./gradlew :app:installDebug`. Never run `connectedDebugAndroidTest`. adb can't tap on this Xiaomi, so the user taps for device checks.
- **Tests:** `./gradlew :app:testDebugUnitTest` must stay green after every task.

## Review Focus

1. **Saving tags to the file that's playing** (ExoPlayer has it open). The save succeeds or fails cleanly, the file is never corrupted, and playback at worst skips. Task 7 device check.
2. **A file TagLib can't write** (read-only, an unsupported container such as `.webm`, a missing path). `TagWriter.write` returns false, the toast shows, nothing crashes, and the file is unchanged. Task 6 test `write_to_missing_file_is_false`; Task 7 device check.
3. **Removing a music folder must not drop songs from playlists.** Playlists resolve and prune against all music, not the folder-filtered view. Task 3 wiring and device check.
4. **A huge cover photo** (e.g. 8000×6000). It decodes downsampled without running out of memory and is stored ≤ 1000px. Task 6 test `sample_size_keeps_long_side_at_least_max`.
5. **A folder that later disappears or is renamed.** The library shows nothing for it, no crash, and it can still be removed in Settings. The query simply matches no rows. Task 3 device check.

---

### Task 1: "Legato" header and the two-pane Settings shell

**Files:**
- Create: `ui/components/Header.kt`, `ui/settings/SettingsScreen.kt`
- Modify: `ui/library/LibraryScreen.kt` (header), `ui/Nav.kt` (`ComingSoon` header, `settings` route)

**Interfaces:**
- Produces:
  - `@Composable fun LegatoHeader(onSettings: () -> Unit)`: "Legato" `headlineLarge` on the left, `Icons.Outlined.Settings` `IconButton` (contentDescription "Settings") on the right, padding 20dp horizontal.
  - `enum class SettingsSection(val label: String) { Music("MUSIC"), Audiobooks("AUDIOBOOKS"), Converter("CONVERTER") }`.
  - `@Composable fun SettingsScreen(onBack: () -> Unit, pane: @Composable (SettingsSection) -> Unit)`.
  - `LibraryScreen` gains `onSettings: () -> Unit`.

- [ ] **Step 1: Implement the header.** `LibraryScreen` replaces its "LEGATO" eyebrow and "Library" title with `LegatoHeader(onSettings)`. `ComingSoon(title, body)` in `Nav.kt` becomes `LegatoHeader` plus `Empty(title, body)`.
- [ ] **Step 2: Implement `SettingsScreen`.**
  - Top row: `Back(onBack)` and "Settings" (`headlineSmall`).
  - Below it, a `Row`. On the left, a 120dp `Column` rail of `SettingsSection` labels (`labelSmall`, selected bold `onBackground`, others `onSurfaceVariant`, 12dp vertical padding, selection in `rememberSaveable`). Then a 0.5dp `VerticalDivider` in `outline`. Then `Box(Modifier.weight(1f)) { pane(selected) }`.
  - `systemBarsPadding` as on the other screens.
- [ ] **Step 3: Wire the route.** Add a top-level `composable("settings")` in `Nav.kt`, outside the section graphs. Every `LegatoHeader` navigates there. For now the pane renders `Text("Comes with the converter.")` for Converter and nothing for the other two.
- [ ] **Step 4: Check it.** Run `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: all pass, "Installed on 1 device".
- [ ] **Step 5: Device check with the user.**
  - "Legato" is at the top left of Music, Audiobooks and Converter, with the gear at the top right.
  - The gear opens Settings, the rail switches sections, and back returns.
- [ ] **Step 6: Commit** `feat: Legato header and settings shell`.

---

### Task 2: All files access gate and minSdk 30

**Files:**
- Modify: `ui/PermissionGate.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` (`minSdk = 30`)

**Interfaces:**
- Produces: `PermissionGate(content)`, same signature; it now gates on `Environment.isExternalStorageManager()`.

- [ ] **Step 1: Update the manifest and minSdk.**
  - Manifest: replace the `READ_MEDIA_AUDIO` and `READ_EXTERNAL_STORAGE` lines with `<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />`.
  - Build: set `minSdk = 30`.
  - Code: delete `AudioPermission`.
- [ ] **Step 2: Rewrite the gate.**
  - `granted` is re-read on every `Lifecycle.Event.ON_RESUME`, via `LocalLifecycleOwner` and a `LifecycleEventObserver` in a `DisposableEffect`.
  - Copy: "Legato needs access to your files". Body: "To read your music and audiobooks and save your edits. Nothing leaves this phone."
  - The "Allow" button starts `Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))`.
- [ ] **Step 3: Check it.** Run `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: all pass, installed.
- [ ] **Step 4: Device check.**
  - Run `adb shell appops set com.phuuun.legato MANAGE_EXTERNAL_STORAGE deny`, launch: the gate shows.
  - Tap Allow, flip the switch, come back: the library shows.
  - If appops is blocked, the user revokes it in Settings → Apps → Legato.
- [ ] **Step 5: Commit** `feat: all files access gate, minSdk 30`.

---

### Task 3: Folder store, folder panes and library by folder

**Files:**
- Create: `library/Folders.kt`, `ui/settings/FoldersPane.kt`
- Modify:
  - `library/Models.kt`: `Track` gains `path`, `relativePath`, `composer` and `modified`.
  - `library/MediaStoreLibrary.kt`.
  - `LegatoApp.kt`.
  - `ui/Nav.kt`: the panes.
- Test: `library/FoldersTest.kt` (JVM, Robolectric for the store)

**Interfaces:**
- Produces:
  - `enum class FolderKind(val key: String) { Music("music_folders"), Audiobooks("audiobook_folders") }`.
  - `class FolderStore(context: Context)` with `fun folders(kind: FolderKind): Flow<Set<String>>`, `fun add(kind: FolderKind, path: String)` and `fun remove(kind: FolderKind, path: String)`.
  - `fun relativePathFromTreeDocId(docId: String): String?`.
  - `enum class WhenEmpty { AllMusic, Nothing }`.
  - `fun audioSelection(folders: Set<String>, whenEmpty: WhenEmpty): Pair<String, Array<String>>?`.
  - `class MediaStoreLibrary(context: Context, folders: Flow<Set<String>>, whenEmpty: WhenEmpty) : LibraryRepository`.
  - `Track` gains trailing defaults: `path: String = ""`, `relativePath: String = ""`, `composer: String = ""`, `modified: Long = 0`. `trackFromRow` gains the same trailing default params.
  - `LegatoApp`: `folders: FolderStore`; `library` is music folders with `AllMusic`; `audiobooks` is audiobook folders with `Nothing`; `allMusic` is `flowOf(emptySet())` with `AllMusic`. `PlaylistRepository` is built on `allMusic`, so removing a folder never prunes a playlist (Review Focus 3).
  - `@Composable fun FoldersPane(folders: Set<String>, hint: String, onAdd: (String) -> Unit, onRemove: (String) -> Unit, footer: @Composable () -> Unit = {})`.

- [ ] **Step 1: Write the failing tests** in `FoldersTest`:

```kotlin
@Test fun primary_tree_maps_to_relative_path() = assertEquals("Download/MUSIC/", relativePathFromTreeDocId("primary:Download/MUSIC"))
@Test fun trailing_slash_is_not_doubled() = assertEquals("Music/", relativePathFromTreeDocId("primary:Music/"))
@Test fun storage_root_and_sd_card_are_rejected() {
    assertNull(relativePathFromTreeDocId("primary:"))
    assertNull(relativePathFromTreeDocId("1A2B-3C4D:Music"))
}
@Test fun folders_select_exact_relative_paths() {
    val (sel, args) = audioSelection(setOf("Download/MUSIC/", "Music/"), WhenEmpty.AllMusic)!!
    assertEquals("${MediaStore.Audio.Media.RELATIVE_PATH} IN (?,?) AND ${MediaStore.Audio.Media.DURATION} > 0", sel)
    assertArrayEquals(arrayOf("Download/MUSIC/", "Music/"), args)  // sorted
}
@Test fun no_folders_means_all_music_or_nothing() {
    assertEquals("${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0", audioSelection(emptySet(), WhenEmpty.AllMusic)!!.first)
    assertNull(audioSelection(emptySet(), WhenEmpty.Nothing))
}
@Test fun store_round_trips_per_kind() = runTest {  // Robolectric context
    val store = FolderStore(ApplicationProvider.getApplicationContext())
    store.add(FolderKind.Music, "Download/MUSIC/")
    store.add(FolderKind.Audiobooks, "Audiobooks/")
    store.remove(FolderKind.Music, "Download/MUSIC/")
    assertEquals(emptySet<String>(), store.folders(FolderKind.Music).first())
    assertEquals(setOf("Audiobooks/"), store.folders(FolderKind.Audiobooks).first())
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*FoldersTest'`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement `Folders.kt`.**
  - `folders()` is a `callbackFlow` over `OnSharedPreferenceChangeListener`. Hold the listener in a local so it isn't garbage-collected, then `distinctUntilChanged`.
  - Store copies of the sets, never the instance SharedPreferences returns.
- [ ] **Step 4: Implement `MediaStoreLibrary`.**
  - `tracks = folders.flatMapLatest { f -> callbackFlow { … query(audioSelection(f, whenEmpty)) … } }`. A null selection emits an empty list.
  - Add the `DATA`, `RELATIVE_PATH`, `COMPOSER` and `DATE_MODIFIED` columns.
- [ ] **Step 5: Implement the panes.**
  - `FoldersPane` has a FOLDERS eyebrow, then one row per folder: the path without its trailing slash, plus a ✕. Then an "Add folder" row with `Icons.Rounded.Add`. Then the hint in `bodyMedium onSurfaceVariant`. Then `footer()`.
  - "Add folder" uses `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`, maps the result with `DocumentsContract.getTreeDocumentId`, and calls `relativePathFromTreeDocId`. On null it shows the toast "Only internal storage for now".
  - `Nav.kt` renders `FoldersPane` for Music (with the Music hint) and Audiobooks (no hint), each collecting `app.folders.folders(kind)`.
- [ ] **Step 6: Run** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: all pass, installed.
- [ ] **Step 7: Device check.**
  - Add `Download/MUSIC`: Songs shows exactly 17 tracks, without `Download/MUSIC/Etc`.
  - Remove it: all 38 return.
  - A playlist holding a song from `Download/` still shows it while only `Download/MUSIC` is selected (Review Focus 3).
- [ ] **Step 8: Commit** `feat: music and audiobook folders`.

---

### Task 4: Split artists and per-song covers

**Files:**
- Create: `ui/components/AudioCover.kt` (Coil fetcher and keyer)
- Modify:
  - `library/Models.kt`: `artists`, and `artUri` becomes the track's own URI.
  - `library/Grouping.kt`: `groupArtists`.
  - `ui/components/Artwork.kt`.
  - `LegatoApp.kt`: `SingletonImageLoader.Factory`.
- Test: `library/GroupingTest.kt` (add cases)

**Interfaces:**
- Consumes: `Track.modified` (Task 3).
- Produces:
  - `Track.artists: List<String>` (trailing default `emptyList()`): the artist tag split on `;`, trimmed, blanks and `MediaStore.UNKNOWN_STRING` dropped. `Track.artist` is `artists.joinToString(", ")`, or "Unknown artist" when empty.
  - `Track.artUri` is `uri.buildUpon().appendQueryParameter("v", modified.toString()).build()`.
  - `data class AudioCover(val uri: Uri)`. `Artwork` passes `AudioCover(uri)` to `AsyncImage`.
  - The fetcher strips the query and calls `contentResolver.loadThumbnail(stripped, Size(512, 512), null)`. The keyer uses the full URI, so an edited file (new `DATE_MODIFIED`) gets a new cache key.
  - `groupArtists` lists an album under every distinct name in its tracks' `artists`, or under "Unknown artist" when a track has none.

- [ ] **Step 1: Write the failing tests** in `GroupingTest`:

```kotlin
@Test fun artist_tag_splits_on_semicolon() {
    val t = row(1, "X", 1, artist = "Daniel Caesar; H.E.R.")
    assertEquals(listOf("Daniel Caesar", "H.E.R."), t.artists)
    assertEquals("Daniel Caesar, H.E.R.", t.artist)
}
@Test fun album_with_two_artists_appears_under_both() {
    val artists = groupArtists(groupAlbums(listOf(row(1, "X", 1, artist = "Daniel Caesar; H.E.R."), row(2, "Y", 2, artist = "H.E.R."))))
    assertEquals(listOf("Daniel Caesar", "H.E.R."), artists.map { it.name })
    assertEquals(listOf("X", "Y"), artists.single { it.name == "H.E.R." }.albums.map { it.title })
}
@Test fun cover_uri_is_the_track_itself_and_changes_with_the_file() {
    val a = trackFromRow(5, "T", "A", "X", 1, 1000, "a.mp3", modified = 100)
    val b = trackFromRow(5, "T", "A", "X", 1, 1000, "a.mp3", modified = 200)
    assertEquals(a.uri, a.artUri!!.buildUpon().clearQuery().build())
    assertNotEquals(a.artUri, b.artUri)
}
```

- [ ] **Step 2: Run** `--tests '*GroupingTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the model, grouping, fetcher, keyer and image-loader factory. `LegatoApp` implements `SingletonImageLoader.Factory` and adds both components.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: all pass, installed.
- [ ] **Step 5: Device check.** Rips with embedded art show their own cover. Songs in the same folder no longer share one cover. The notification still shows artwork (Media3 takes the embedded `artworkData`).
- [ ] **Step 6: Commit** `feat: split artists, per-song covers`.

---

### Task 5: Audiobooks list

**Files:**
- Create: `library/Books.kt`, `ui/audiobooks/AudiobooksScreen.kt`, `ui/audiobooks/BookScreen.kt`
- Modify: `ui/Nav.kt` (the Audiobooks section becomes a nested graph `audiobooks` with `books` and `book/{id}`), `MainActivity.kt` (passes `app.audiobooks`)
- Test: `library/BooksTest.kt` (Robolectric, for `trackFromRow`'s `Uri`)

**Interfaces:**
- Consumes: `Track.relativePath`, `Track.trackNumber`, `LegatoApp.audiobooks` (Task 3); `TrackActions` and `SongRow` (existing).
- Produces:
  - `data class Book(val title: String, val author: String, val artUri: Uri?, val chapters: List<Track>) { val id: Long get() = chapters.first().id }`.
  - `fun groupBooks(tracks: List<Track>): List<Book>`.
  - `@Composable fun AudiobooksScreen(books: List<Book>, hasFolders: Boolean, onBook: (Book) -> Unit, actions: TrackActions, onSettings: () -> Unit)`.
  - `@Composable fun BookScreen(book: Book?, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit, actions: TrackActions)`.
  - `LegatoNav` gains `audiobooks: LibraryRepository`.

- [ ] **Step 1: Write the failing tests** in `BooksTest`. `t(id, title, album, relativePath, trackNumber)` wraps `trackFromRow`.

```kotlin
@Test fun untagged_files_are_their_own_books() {  // MediaStore sets ALBUM to the folder name
    val books = groupBooks(listOf(t(1, "Dune", "Audiobooks", "Audiobooks/"), t(2, "Emma", "Audiobooks", "Audiobooks/")))
    assertEquals(listOf("Dune", "Emma"), books.map { it.title })
}
@Test fun blank_album_is_its_own_book() = assertEquals("Dune", groupBooks(listOf(t(1, "Dune", null, "Audiobooks/"))).single().title)
@Test fun files_sharing_a_book_title_form_one_book() {
    val books = groupBooks(listOf(t(1, "Ch 1", "Dune", "Audiobooks/"), t(2, "Ch 2", "Dune", "Audiobooks/"), t(3, "Emma", "Audiobooks", "Audiobooks/")))
    assertEquals(listOf("Dune", "Emma"), books.map { it.title })
    assertEquals(2, books.first().chapters.size)
}
@Test fun chapters_order_by_track_number_then_title() {
    val book = groupBooks(listOf(t(1, "B", "Dune", "A/", 2), t(2, "A", "Dune", "A/", 2), t(3, "Z", "Dune", "A/", 1))).single()
    assertEquals(listOf(3L, 2L, 1L), book.chapters.map { it.id })
}
```

- [ ] **Step 2: Run** `--tests '*BooksTest'`. Expected: FAIL.
- [ ] **Step 3: Implement `groupBooks`.**
  - The group key is the album, unless the album is "Unknown album" or equals `relativePath.trimEnd('/').substringAfterLast('/')`. In that case the key is `"#${track.id}"` and the title is the track title.
  - Author is the first chapter's `artist`, and `artUri` is the first chapter's.
  - Books are sorted by title, case-insensitive.
- [ ] **Step 4: Implement the screens.**
  - `AudiobooksScreen` is `LegatoHeader`, then a two-column grid of book cards in the `AlbumCard` style: cover, title, author. Long-press opens the `TrackMenu` over `chapters`.
  - With no folders it shows `Empty("No audiobooks", "Add an audiobook folder in Settings")`.
  - Tapping a book with one chapter calls `onPlay(chapters, 0)`. Otherwise it navigates to `book/{id}`.
  - `BookScreen` is `Back`, the cover, title and author, then `SongRow`s of the chapters.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: all pass, installed.
- [ ] **Step 6: Device check.** Add an audiobook folder holding a single-file book and two chapter files. The grid shows two books, the chapters open in order, and tapping plays.
- [ ] **Step 7: Commit** `feat: audiobooks list`.

---

### Task 6: Guess, tag maps and TagWriter

**Files:**
- Create: `library/edit/Guess.kt`, `library/edit/Tags.kt`, `library/edit/TagWriter.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (TagLib dependency, `abiFilters`)
- Test: `library/edit/GuessTest.kt`, `library/edit/TagsTest.kt` (JVM), `library/edit/TagWriterTest.kt` (Robolectric, failure path only)

**Interfaces:**
- Consumes: `Track.path`, `Track.title` (Task 3).
- Produces:
  - `data class Guess(val artist: String, val title: String)` and `fun guessFromTitle(raw: String): Guess`.
  - `data class MusicEdit(val title: String, val artists: List<String>, val album: String)` and `fun MusicEdit.properties(): Map<String, String>`.
  - `data class BookEdit(val bookTitle: String, val chapterTitle: String?, val author: String, val narrator: String)` and `fun BookEdit.properties(): Map<String, String>`.
  - `sealed interface CoverChange { data object Keep : CoverChange; data object Remove : CoverChange; class Set(val jpeg: ByteArray) : CoverChange }`.
  - `fun sampleSize(width: Int, height: Int, max: Int = 1000): Int`: the largest power of two that keeps the long side ≥ `max`.
  - `suspend fun coverJpeg(context: Context, image: Uri): ByteArray`: decodes with `sampleSize`, scales the long side to ≤ 1000, JPEG quality 90.
  - `class TagWriter(context: Context)` with `suspend fun write(track: Track, properties: Map<String, String>, cover: CoverChange): Boolean`.

- [ ] **Step 1: Write the failing tests.** `GuessTest` uses the phone's real titles:

```kotlin
@Test fun artist_dash_title_with_trailing_junk() = assertEquals(Guess("Agnes Monica", "Rindu"), guessFromTitle("Agnes Monica - Rindu  Official 4K Remastered"))
@Test fun bracketed_junk_is_dropped() {
    assertEquals(Guess("Bunga Citra Lestari", "Cinta Sejati"), guessFromTitle("Bunga Citra Lestari - Cinta Sejati [ lirik ]"))
    assertEquals(Guess("Chrisye", "Aku Cinta Dia"), guessFromTitle("Chrisye - Aku Cinta Dia (Official Music Video)"))
    assertEquals(Guess("Chrisye", "Andai Aku Bisa"), guessFromTitle("Chrisye - Andai Aku Bisa (Official Lyric Video)"))
}
@Test fun meaningful_brackets_stay() = assertEquals(Guess("Can't", "Daniel Caesar (Unreleased)"), guessFromTitle("Can't - Daniel Caesar (Unreleased)"))
@Test fun no_separator_leaves_artist_blank() {
    assertEquals(Guess("", "Alarm Clock Loud"), guessFromTitle("Alarm Clock Loud"))
    assertEquals(Guess("", "BIBI 'Bam Yang Gang' Easy"), guessFromTitle("BIBI 'Bam Yang Gang' Easy Lyrics"))
}
@Test fun en_and_em_dashes_split_too() = assertEquals(Guess("A", "B"), guessFromTitle("A – B"))
@Test fun a_title_never_strips_to_nothing() = assertEquals(Guess("Madonna", "Music"), guessFromTitle("Madonna - Music (Official Video)"))
```

`TagsTest`:

```kotlin
@Test fun music_joins_artists_with_semicolons() =
    assertEquals(mapOf("TITLE" to "Rindu", "ARTIST" to "Agnes Monica; Ahmad Dhani", "ALBUM" to ""), MusicEdit("Rindu", listOf("Agnes Monica", " Ahmad Dhani "), "").properties())
@Test fun single_file_book_writes_title_to_title_and_album() =
    assertEquals(mapOf("TITLE" to "Dune", "ALBUM" to "Dune", "ARTIST" to "Frank Herbert", "ALBUMARTIST" to "Frank Herbert", "COMPOSER" to "Scott Brick"),
        BookEdit("Dune", null, "Frank Herbert", "Scott Brick").properties())
@Test fun chapter_keeps_its_own_title() = assertEquals("Ch 1", BookEdit("Dune", "Ch 1", "", "").properties()["TITLE"])
@Test fun sample_size_keeps_long_side_at_least_max() {
    assertEquals(8, sampleSize(8000, 6000))  // 8000/8 = 1000; /16 would drop below 1000
    assertEquals(2, sampleSize(2500, 1000))
    assertEquals(1, sampleSize(900, 600))
}
```

`TagWriterTest`: `write_to_missing_file_is_false`. A track with `path = "/nonexistent/x.mp3"` makes `write(...)` return `false` without throwing (Review Focus 2).

- [ ] **Step 2: Run** `--tests '*GuessTest' --tests '*TagsTest' --tests '*TagWriterTest'`. Expected: FAIL.
- [ ] **Step 3: Implement `guessFromTitle`.**
  1. Split at the first of `" - "`, `" – "`, `" — "`.
  2. In each part, drop bracketed `(…)` or `[…]` segments containing a junk word.
  3. Repeatedly drop the last word while it's a junk word and more than one word remains.
  4. Collapse whitespace and trim.
  - Junk words, matched case-insensitively as whole words: `official, video, music, audio, lyric, lyrics, lirik, remaster, remastered, 4k, hd, mv, visualizer`.
- [ ] **Step 4: Implement the tag maps and covers.** `properties()` trims every value and keeps blanks; the writer removes keys whose value is blank. Also `sampleSize` and `coverJpeg`.
- [ ] **Step 5: Implement `TagWriter.write`**, on `Dispatchers.IO`:
  1. `ParcelFileDescriptor.open(File(track.path), MODE_READ_WRITE)`.
  2. `TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)`. Merge `properties` into its `propertyMap`: a blank value removes the key, any other value becomes `arrayOf(value)`.
  3. `TagLib.savePropertyMap(fd.dup().detachFd(), map)`.
  4. Cover: `Set` calls `TagLib.savePictures(fd.dup().detachFd(), arrayOf(Picture(jpeg, "Front Cover", "Front Cover", "image/jpeg")))`. `Remove` saves `emptyArray()`.
  5. Close the descriptor, then await `MediaScannerConnection.scanFile` with `suspendCancellableCoroutine`.
  - Any exception returns `false`.
- [ ] **Step 6: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: all pass. The APK contains `lib/arm64-v8a/` from TagLib only.
- [ ] **Step 7: Commit** `feat: title guessing, tag maps, TagLib writer`.

---

### Task 7: Edit screen and entry points

**Files:**
- Create: `ui/edit/EditScreen.kt`
- Modify:
  - `ui/library/Rows.kt`: `TrackActions.edit`, and "Edit info…" in `TrackMenu` when `tracks.size == 1`.
  - `ui/nowplaying/NowPlayingViewModel.kt`: `NowPlayingState.trackId: Long?`.
  - `ui/nowplaying/NowPlayingScreen.kt`: a pencil `IconButton` beside the queue icon, with `onEdit`.
  - `ui/Nav.kt`: the `edit/{id}` route, `TagWriter` in `LegatoApp`.

**Interfaces:**
- Consumes: `guessFromTitle`, `MusicEdit`, `BookEdit`, `CoverChange`, `coverJpeg`, `TagWriter` (Task 6); `groupBooks` (Task 5); `FolderKind` (Task 3).
- Produces:
  - `class TrackActions(queue, addToPlaylist, val edit: (Track) -> Unit)`.
  - `@Composable fun EditScreen(track: Track?, kind: FolderKind, chapters: Int, onBack: () -> Unit, onSave: (properties: Map<String, String>, cover: CoverChange) -> Unit)`.
  - `NowPlayingSheet(state, vm, onDismiss, onEdit: (Long) -> Unit)`.

- [ ] **Step 1: Implement `EditScreen`.**
  - Top row: `Back`, then "Edit" (`headlineSmall`), then a "Save" `TextButton`. Below that, the cover at 160dp square: `Artwork(track.artUri)`, or the picked image once chosen. Under it, "Change cover" (`PickVisualMedia(ImageOnly)` → `coverJpeg` → `CoverChange.Set`) and "Remove cover". Then the fields as `TextField`s in the library search style.
  - **Music:**
    - Title.
    - Artists: one field per artist, each with ✕, then "+ Add artist".
    - Album.
    - Prefill: when `track.artists` is empty, `guessFromTitle(track.title)` fills one artist (if non-blank) and the title. Otherwise the fields hold the track's values. "Unknown album" prefills as blank.
  - **Audiobook:**
    - Book title. Prefilled from the album, or the track title when it's a single-file book.
    - Chapter title, only when `chapters > 1`.
    - Author (blank when "Unknown artist").
    - Narrator, from `composer`.
  - "Save" calls `onSave(edit.properties(), coverChange)`.
- [ ] **Step 2: Wire it up in `Nav.kt`.**
  - The `edit/{id}` route looks the track up in the audiobook tracks first. If found, kind is `Audiobooks` and `chapters` is its book's chapter count. Otherwise it looks in music, with kind `Music`.
  - `onSave` launches in the Nav scope: `app.tagWriter.write(...)`. On `true`, pop back. On `false`, toast "Couldn't save <title>".
  - `actions.edit` navigates to `edit/{id}`. The player's pencil collapses the sheet, then navigates.
- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: all pass, installed.
- [ ] **Step 4: Device check, on copies first.**
  - Run `adb shell mkdir -p /sdcard/Music/LegatoTest` and copy in one MP3 and one WAV from the library (`adb shell cp`). Add `Music/LegatoTest` as a music folder.
  - On both copies, edit the title, two artists, the album and the cover. Legato shows the changes, the song is under both artists, and the cover updates.
  - Another player (e.g. the Files app's audio preview) shows the new title.
  - Edit the song that's playing (Review Focus 1): the save works and the file still plays.
  - Edit an audiobook chapter's narrator and see it persist.
  - Remove the test folder afterwards.
- [ ] **Step 5: Commit** `feat: edit tags and covers`.

---

### Task 8: Bulk fix unknown artists

**Files:**
- Create: `ui/edit/BulkFixScreen.kt`
- Modify: `ui/Nav.kt` (the `fix` route, plus the Music pane footer "Fix unknown artists (N)")
- Test: `library/edit/GuessTest.kt` (add the row builder cases)

**Interfaces:**
- Consumes: `guessFromTitle`, `MusicEdit`, `TagWriter`, `CoverChange.Keep` (Task 6).
- Produces:
  - `data class FixRow(val track: Track, val artist: String, val title: String, val include: Boolean)`.
  - `fun fixRows(tracks: List<Track>): List<FixRow>`.
  - `@Composable fun BulkFixScreen(rows: List<FixRow>, onBack: () -> Unit, write: suspend (Track, MusicEdit) -> Boolean)`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun rows_cover_only_unknown_artist_songs() {
    val rows = fixRows(listOf(trackFromRow(1, "Chrisye - Aku Cinta Dia (Official Music Video)", null, null, 1, 1000, "a.mp3"), trackFromRow(2, "Halo", "Beyoncé", null, 1, 1000, "b.mp3")))
    assertEquals(listOf(FixRow(rows[0].track, "Chrisye", "Aku Cinta Dia", include = true)), rows)
}
@Test fun row_without_separator_starts_unticked() =
    assertFalse(fixRows(listOf(trackFromRow(1, "Alarm Clock Loud", null, null, 1, 1000, "a.mp3"))).single().include)
```

- [ ] **Step 2: Run** `--tests '*GuessTest'`. Expected: FAIL.
- [ ] **Step 3: Implement `fixRows`**, which keeps tracks whose `artists` is empty, and **`BulkFixScreen`**:
  - Top row: `Back`, "Fix unknown artists", then "Save N", where N is the ticked row count.
  - Each row has a checkbox, artist and title `TextField`s, and a ⇄ `IconButton` (`Icons.Rounded.SwapHoriz`) that swaps them. Row state lives in a `mutableStateListOf`.
  - Save writes the ticked rows one by one through `write(track, MusicEdit(title, listOf(artist), track.album.takeUnless { it == "Unknown album" } ?: ""))`, showing "Saving i / N". Then it shows "Saved X" plus the title of each failure, and leaves only the failed rows.
  - With no rows it shows `Empty("Nothing to fix", "Every song has an artist.")`.
- [ ] **Step 4: Wire it up.** The Music pane `footer` is a row "Fix unknown artists (N)", where N is the music tracks with empty `artists`; it navigates to `fix`. The `fix` route passes `fixRows(state.tracks)` once, using `remember` keyed on entering the route, and `write = { t, e -> app.tagWriter.write(t, e.properties(), CoverChange.Keep) }`.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: all pass, installed.
- [ ] **Step 6: Device check with the user.** This writes the user's real files. Say so before the user taps "Save". Run it on the real library, swap the title-first rows, and save. Artists then lists real names, and Unknown artist only holds the songs that were left unticked.
- [ ] **Step 7: Commit** `feat: bulk fix unknown artists`.
