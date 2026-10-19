# Legato converter implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Share or paste a YouTube video or playlist link, pick Music or Audiobook and a folder, and get tagged `.m4a` files with covers in the library.

**Architecture:**
- NewPipeExtractor resolves links into metadata and direct M4A stream URLs.
- An app-wide `DownloadQueue` (a `StateFlow` of jobs) is fed by the Converter screen and worked through by a `dataSync` foreground service. The service downloads in 8 MB `Range` chunks to a `.part` file, writes tags and the thumbnail cover with the phase 2 `TagWriter`, renames the file, and has MediaStore scan it.
- The pure logic (link parsing, guesses, file names, crop, best stream, queue transitions) lives in `converter/` files with JVM tests.

**Tech Stack:** Kotlin, Compose Material3, NewPipeExtractor `v0.26.5` (JitPack), TagLib (existing), Media3 (existing), JUnit and Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-19-legato-converter-design.md`

## Global Constraints

- **Versions:** minSdk 30, compile/target 37, AGP 9.1.1, Kotlin 2.4.20, single `:app` module, no DI framework. No new dependency besides `com.github.TeamNewPipe:NewPipeExtractor:v0.26.5`. JitPack is added to `dependencyResolutionManagement` only, with `content { includeGroup("com.github.TeamNewPipe") }`.
- **Output:** M4A only, from the highest-bitrate M4A audio stream.
- **Download:** 8 MB `Range` chunks into `<folder>/<name>.m4a.part`, renamed to `.m4a` after tagging. A failure deletes the `.part`. Retry refetches the stream URL.
- **Tags:**
  - Music: `MusicEdit(title, listOf(artist), album = "")`.
  - Audiobook: `BookEdit(bookTitle, chapterTitle or null, author, narrator)`.
  - Playlist chapters add `TRACKNUMBER` = their 1-based position.
  - Cover: the largest thumbnail, centre-cropped square, long side ≤ 1000px JPEG.
- **File names:**
  - Music: `Artist - Title.m4a`, or `Title.m4a` with no artist.
  - A one-file book: `Book title.m4a`.
  - A chapter: `NN Chapter title.m4a` (two-digit position).
  - Characters `/ \ : * ? " < > |` become `_`. The base name is trimmed to 120 characters. A name already in the folder gets ` (2)`, ` (3)`… and is never overwritten.
- **Last folder:** stored in SharedPreferences `settings` as `last_music_folder` and `last_audiobook_folder`.
- **Copy, exact:**
  - "That isn't a YouTube link".
  - "Just this video" / "Whole playlist".
  - "Add folder".
  - Settings Converter pane: "Downloads go to the folder you pick in the Converter. Add more folders under Music and Audiobooks."
- **Mixes:** a playlist id starting with `RD` (YouTube's endless mixes) counts as no playlist.
- **Licence:** a GPLv3 `LICENSE` at the repo root lands with the extractor dependency.
- **Commits:** no `Co-Authored-By` trailers or assistant attribution in commit messages (user rule).
- **Build env** (as phase 2): `JAVA_HOME=/home/phuuun/Projects/Tools/android-studio/jbr`; install with `./gradlew :app:installDebug`; adb at `/home/phuuun/Projects/Tools/Android/Sdk/platform-tools/adb`; never `connectedDebugAndroidTest`. adb can't tap, so the user taps for device checks.

## Review Focus

1. **YouTube refuses a stream mid-download** (403 or a dropped connection on chunk N). The job fails with a reason, the `.part` is deleted, and Retry gets a fresh URL and finishes. Task 3 test `failed_job_retries_as_queued`; Task 4 device check (airplane mode mid-download).
2. **TagLib on fragmented M4A** (`ftyp, moov, sidx, moof/mdat…`). After tagging, the file still plays, seeks to 80%, and MediaStore shows the new title. If it doesn't, Task 4 inserts a remux before tagging. Task 4 device check.
3. **A title that's all illegal characters, or a duplicate name.** The file is still written, under a safe unique name, never overwriting another. Task 1 tests.
4. **The app killed while the service runs** (swiped away). The service, being in the foreground, keeps going; if its process dies, unfinished jobs are simply gone and leave no `.part`. Task 3 deletes stale `.part` files in queued folders on service start.
5. **A playlist with private or deleted videos.** Those rows are skipped with no crash, and the rest download. Task 5: map items whose stream fetch fails to `Failed("Unavailable")`.

---

### Task 1: Links, guesses, names and crop (pure logic)

**Files:**
- Create: `converter/Links.kt`, `converter/Guesses.kt`, `converter/Names.kt`
- Modify: `library/edit/Tags.kt` (add `squareCrop`)
- Test: `converter/LinksTest.kt`, `converter/GuessesTest.kt`, `converter/NamesTest.kt` (JVM), and `TagsTest.kt` (add a case)

**Interfaces:**
- Produces:
  - `data class YouTubeLink(val video: String?, val playlist: String?)`, `fun parseYouTube(text: String): YouTubeLink?`, `fun videoUrl(id: String): String`, `fun playlistUrl(id: String): String`.
  - `fun guessSong(title: String, uploader: String): Guess` (phase 2's `Guess`).
  - `data class BookGuess(val title: String, val author: String)` and `fun guessBook(title: String): BookGuess`.
  - `fun musicFileName(artist: String, title: String): String`, `fun bookFileName(title: String): String`, `fun chapterFileName(position: Int, title: String): String`, `fun uniqueName(name: String, taken: Set<String>): String`.
  - `data class Crop(val left: Int, val top: Int, val size: Int)` and `fun squareCrop(width: Int, height: Int): Crop`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// LinksTest
@Test fun watch_short_music_and_shorts_links() {
    assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("https://youtu.be/dQw4w9WgXcQ?si=Ab12Cd"))
    assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("https://music.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"))
    assertEquals(YouTubeLink("abc123DEF45", null), parseYouTube("https://youtube.com/shorts/abc123DEF45"))
}
@Test fun playlists_and_video_in_playlist() {
    assertEquals(YouTubeLink(null, "PLx0sYbCqOb8Q"), parseYouTube("https://www.youtube.com/playlist?list=PLx0sYbCqOb8Q"))
    assertEquals(YouTubeLink("dQw4w9WgXcQ", "PLx0sYbCqOb8Q"), parseYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLx0sYbCqOb8Q&index=2"))
}
@Test fun mixes_are_not_playlists() = assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"))
@Test fun link_inside_shared_text() = assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("Never Gonna Give You Up https://youtu.be/dQw4w9WgXcQ"))
@Test fun other_sites_are_rejected() {
    assertNull(parseYouTube("https://example.com/watch?v=dQw4w9WgXcQ"))
    assertNull(parseYouTube("hello"))
}

// GuessesTest
@Test fun topic_channel_is_the_artist() = assertEquals(Guess("Agnes Monica", "Rindu"), guessSong("Rindu", "Agnes Monica - Topic"))
@Test fun artist_dash_title_wins() = assertEquals(Guess("Rick Astley", "Never Gonna Give You Up"), guessSong("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", "Rick Astley"))
@Test fun no_dash_falls_back_to_the_channel() = assertEquals(Guess("Rick Astley", "Never Gonna Give You Up"), guessSong("Never Gonna Give You Up", "Rick Astley"))
@Test fun vevo_is_dropped_from_the_channel() = assertEquals("Rick Astley", guessSong("Song", "Rick AstleyVEVO").artist)
@Test fun book_by_author() = assertEquals(BookGuess("The Inheritance Games", "Jennifer Lynn Barnes"), guessBook("The Inheritance Games by Jennifer Lynn Barnes _ Full Audiobook _ The Inheritance Games #1"))
@Test fun book_without_author() {
    assertEquals(BookGuess("Dune", ""), guessBook("Dune (Full Audiobook)"))
    assertEquals(BookGuess("Harry Potter and the Sorcerer's Stone", ""), guessBook("Harry Potter and the Sorcerer's Stone - Audiobook"))
}
@Test fun book_author_stops_at_a_pipe() = assertEquals(BookGuess("Emma", "Jane Austen"), guessBook("Emma by Jane Austen | Unabridged"))

// NamesTest
@Test fun music_names() {
    assertEquals("Agnes Monica - Rindu.m4a", musicFileName("Agnes Monica", "Rindu"))
    assertEquals("Rindu.m4a", musicFileName(" ", "Rindu"))
}
@Test fun illegal_characters_become_underscores() = assertEquals("AC_DC - Back_ In_Black.m4a", musicFileName("AC/DC", "Back? In:Black"))
@Test fun chapter_names_are_numbered() = assertEquals("03 The Will.m4a", chapterFileName(3, "The Will"))
@Test fun long_names_are_trimmed_keeping_the_extension() = assertEquals(120 + ".m4a".length, bookFileName("x".repeat(300)).length)
@Test fun empty_title_still_gets_a_name() = assertEquals("Untitled.m4a", bookFileName("   "))
@Test fun taken_names_get_a_number() {
    assertEquals("A.m4a", uniqueName("A.m4a", emptySet()))
    assertEquals("A (2).m4a", uniqueName("A.m4a", setOf("A.m4a")))
    assertEquals("A (3).m4a", uniqueName("A.m4a", setOf("A.m4a", "A (2).m4a")))
}

// TagsTest
@Test fun square_crop_takes_the_centre() {
    assertEquals(Crop(280, 0, 720), squareCrop(1280, 720))
    assertEquals(Crop(0, 60, 480), squareCrop(480, 600))
    assertEquals(Crop(0, 0, 500), squareCrop(500, 500))
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*LinksTest' --tests '*GuessesTest' --tests '*NamesTest' --tests '*TagsTest'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement.**
  - **`parseYouTube`:**
    - Take the first `https?://\S+` in the text and parse it with `java.net.URI`, not `android.net.Uri`, to stay JVM-pure.
    - Accepted hosts: `youtube.com`, `www.youtube.com`, `m.youtube.com`, `music.youtube.com`, `youtu.be`.
    - Video id: `v=`, the `youtu.be/<id>` path, or `/shorts/<id>`.
    - Playlist: `list=` unless it starts with `RD`.
    - Return null when there's neither a video nor a playlist.
  - **`guessSong`:**
    - A Topic uploader gives `Guess(uploader.removeSuffix(" - Topic"), clean(title))`. Expose phase 2's private `clean` as `internal`.
    - Otherwise use `guessFromTitle`. If its artist is blank, the artist is the uploader with a trailing `VEVO` or ` Official` removed, trimmed.
  - **`guessBook`:**
    - Split at the first `" by "` (case-insensitive).
    - The author ends at the first `|`, `_`, ` - `, `(` or `[`.
    - The book part drops bracketed and trailing junk (`full audiobook`, `audiobook`, `unabridged`, `complete`) and trailing separators ` -_|`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat: converter links, guesses and file names`.

---

### Task 2: YouTube layer (NewPipeExtractor)

**Files:**
- Create: `converter/YouTube.kt`, `LICENSE` (GPLv3 text)
- Modify: `settings.gradle.kts` (JitPack, content-filtered), `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml` (`INTERNET`)
- Test: `converter/YouTubeTest.kt` (JVM: `bestAudio` only, no network)

**Interfaces:**
- Consumes: `YouTubeLink`, `videoUrl`, `playlistUrl` (Task 1).
- Produces:
  - `data class AudioOption(val url: String, val isM4a: Boolean, val bitrate: Int)` and `fun bestAudio(options: List<AudioOption>): AudioOption?`.
  - `data class Video(val url: String, val title: String, val uploader: String, val durationSec: Long, val thumbnail: String?)`.
  - `data class Playlist(val name: String, val videos: List<Video>)`.
  - `object YouTube` with `suspend fun video(url: String): Video`, `suspend fun playlist(url: String): Playlist` (all pages) and `suspend fun audioUrl(videoUrl: String): String` (a fresh stream URL, throws if there's no M4A).
  - The largest thumbnail is chosen by `height`.

- [ ] **Step 1: Write the failing test:**

```kotlin
@Test fun best_audio_is_the_highest_m4a() {
    val best = bestAudio(listOf(AudioOption("a", true, 48), AudioOption("b", false, 160), AudioOption("c", true, 128)))
    assertEquals("c", best?.url)
}
@Test fun no_m4a_means_no_audio() = assertNull(bestAudio(listOf(AudioOption("b", false, 160))))
```

- [ ] **Step 2: Run** `--tests '*YouTubeTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Dependency: `com.github.TeamNewPipe:NewPipeExtractor:v0.26.5`.
  - `YouTube` calls `NewPipe.init(HttpDownloader)` lazily once. `HttpDownloader : Downloader` is `HttpURLConnection`-based, with a desktop Firefox User-Agent, following the spike at `scratchpad/npe-spike/src/main/kotlin/Spike.kt`.
  - Every call runs on `Dispatchers.IO`.
  - `playlist()` follows `PlaylistInfo.getMoreItems` until `nextPage` is null.
  - Add the GPLv3 `LICENSE` file.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS, build OK.
- [ ] **Step 5: Commit** `feat: YouTube extraction via NewPipeExtractor`.

---

### Task 3: Download queue and service

**Files:**
- Create: `converter/DownloadQueue.kt`, `converter/DownloadService.kt`
- Modify:
  - `library/edit/TagWriter.kt`: add `write(path: String, …)`, with `write(track, …)` delegating to it; add `suspend fun coverFromUrl(url: String): ByteArray?` using `squareCrop` and `coverSize`.
  - `AndroidManifest.xml`: the service with `foregroundServiceType="dataSync"`, and `FOREGROUND_SERVICE_DATA_SYNC`.
  - `LegatoApp.kt`: `downloads: DownloadQueue`.
- Test: `converter/DownloadQueueTest.kt` (JVM)

**Interfaces:**
- Consumes: `YouTube.audioUrl` (Task 2); `TagWriter`, `CoverChange` (phase 2); `uniqueName` (Task 1).
- Produces:
  - `sealed interface JobState { Queued; Downloading(percent: Int); Tagging; Done; Failed(reason: String) }`.
  - `data class DownloadJob(val id: Long, val videoUrl: String, val label: String, val folder: String, val fileName: String, val properties: Map<String, String>, val thumbnail: String?, val state: JobState = JobState.Queued)`.
  - `class DownloadQueue` with `val jobs: StateFlow<List<DownloadJob>>`, `fun add(jobs: List<DownloadJob>)`, `fun next(): DownloadJob?` (the first Queued), `fun set(id: Long, state: JobState)`, `fun retry(id: Long)` (Failed back to Queued) and `fun clearFinished()`.
  - `DownloadService.start(context)` (a companion function) starts it as a foreground service.

- [ ] **Step 1: Write the failing tests:**

```kotlin
private fun job(id: Long) = DownloadJob(id, "u$id", "L$id", "Music/", "f$id.m4a", emptyMap(), null)
@Test fun next_is_the_first_queued() {
    val q = DownloadQueue(); q.add(listOf(job(1), job(2)))
    q.set(1, JobState.Done)
    assertEquals(2L, q.next()?.id)
}
@Test fun failed_job_retries_as_queued() {
    val q = DownloadQueue(); q.add(listOf(job(1)))
    q.set(1, JobState.Failed("403"))
    assertNull(q.next())
    q.retry(1)
    assertEquals(JobState.Queued, q.jobs.value.single().state)
}
@Test fun clear_finished_keeps_active_and_failed() {
    val q = DownloadQueue(); q.add(listOf(job(1), job(2), job(3)))
    q.set(1, JobState.Done); q.set(2, JobState.Failed("x"))
    q.clearFinished()
    assertEquals(listOf(2L, 3L), q.jobs.value.map { it.id })
}
```

- [ ] **Step 2: Run** `--tests '*DownloadQueueTest'`. Expected: FAIL.
- [ ] **Step 3: Implement the queue** (a `MutableStateFlow` with `update {}`).
- [ ] **Step 4: Implement the service.**
  - `onStartCommand` starts the foreground notification (channel "Downloads", low importance), then runs one coroutine loop: `while (next != null) process(next)`, then `stopSelf()`.
  - `process`:
    1. Resolve the file: `File(Environment.getExternalStorageDirectory(), folder + uniqueName(fileName, existing names))` and its `.part`.
    2. `YouTube.audioUrl`.
    3. Download in 8 MB `Range` chunks until the `Content-Range` total is reached, updating `Downloading(percent)` and the notification at most once a second.
    4. `Tagging`: `tagWriter.write(part.path, properties, coverFromUrl(thumbnail)?.let(CoverChange::Set) ?: CoverChange.Keep)`.
    5. Rename to the final name, then `MediaScannerConnection.scanFile`, then `Done`.
  - Any exception gives `Failed(message ?: "Download failed")` and deletes the `.part`.
  - On service start, delete `*.m4a.part` files in the folders of queued jobs.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS, build OK.
- [ ] **Step 6: Commit** `feat: download queue and service`.

---

### Task 4: Converter screen (single video), and the fragmented-M4A check

**Files:**
- Create: `ui/converter/ConverterScreen.kt`
- Modify:
  - `ui/Nav.kt`: the Converter route replaces `ComingSoon` and takes an optional initial link.
  - `library/Folders.kt`: `FolderStore.lastFolder(kind): String?` and `setLastFolder(kind, path)`, keys `last_music_folder` / `last_audiobook_folder`.
  - `MainActivity.kt`: pass `app.downloads` (`LegatoNav` gains `downloads: DownloadQueue`).

**Interfaces:**
- Consumes: Tasks 1–3, `FolderStore`, `FoldersPane`'s picker logic.
- Produces: `@Composable fun ConverterScreen(initialLink: String?, folders: FolderStore, downloads: DownloadQueue, onSettings: () -> Unit)`.
  - Extract the folder-picker launcher from `FoldersPane` into `@Composable fun rememberFolderPicker(onPicked: (String) -> Unit): () -> Unit` in `ui/settings/FoldersPane.kt`. Both screens use it.

- [ ] **Step 1: Implement the screen.**
  - `LegatoHeader`; a link `TextField` with a paste `IconButton` (`LocalClipboardManager`) and "Fetch"; then the states:
    - **Idle:** hint text.
    - **Loading:** `CircularProgressIndicator`.
    - **Error:** the message, including "That isn't a YouTube link".
    - **Video:** the thumbnail (Coil `AsyncImage` on the URL, 16:9), title, channel, `formatTime(durationSec * 1000)`. Then the MUSIC / AUDIOBOOK toggle and the fields: Artist and Title, or Book title, Author and Narrator, prefilled by `guessSong` or `guessBook`.
  - **Destination:** a dropdown of the kind's folders, defaulting to `lastFolder(kind)` or the first; with none, an "Add folder" button (`rememberFolderPicker`) that adds to `FolderStore` and selects it.
  - **"Download"** builds a `DownloadJob`, calls `setLastFolder`, `downloads.add`, `DownloadService.start`, and requests `POST_NOTIFICATIONS` the first time.
  - **Below:** a DOWNLOADS list of `downloads.jobs`. Each row shows its label and state (a progress bar while downloading; "Tagging…"; "Done"; or the failure reason with a Retry `TextButton`). A "Clear finished" `TextButton` sits above the list.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: PASS, installed.
- [ ] **Step 3: Device check with the user (Review Focus 2).**
  - Paste a music video link and download it into `Music/LegatoTest`.
  - The file appears with the guessed artist, title and the square thumbnail cover.
  - It plays, and seeking to about 80% works.
  - `adb shell content query --uri content://media/external/audio/media --projection title:artist --where "relative_path='Music/LegatoTest/'"` shows the tags.
  - **If tagging broke the file** (it doesn't play or seek, or the title is missing), add a step before tagging: remux `.part` to a plain MP4 with Media3 Transformer (`media3-transformer`, same version as Media3), and record the ruling.
- [ ] **Step 4: Device check, failure path.** Turn on airplane mode mid-download. The job fails with a reason and no `.part` remains. Turn airplane mode off and tap Retry: it finishes.
- [ ] **Step 5: Commit** `feat: converter screen`.

---

### Task 5: Playlists

**Files:**
- Modify: `ui/converter/ConverterScreen.kt`
- Test: `converter/GuessesTest.kt` (add `playlistJobs` cases)
- Create: `converter/Jobs.kt`

**Interfaces:**
- Consumes: `Playlist`, `Video` (Task 2); `DownloadJob` (Task 3); the guesses and names (Task 1).
- Produces:
  - `data class PickedVideo(val video: Video, val artist: String, val title: String, val include: Boolean)`.
  - `fun musicJobs(picked: List<PickedVideo>, folder: String, firstId: Long): List<DownloadJob>`.
  - `fun bookJobs(videos: List<Video>, bookTitle: String, author: String, narrator: String, folder: String, firstId: Long): List<DownloadJob>`: chapters numbered 1..n in playlist order, with `TRACKNUMBER` and `chapterFileName`. A single video becomes a one-file book named by `bookFileName`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun audiobook_playlist_is_one_book_in_order() {
    val vids = listOf(Video("u1", "Part 1", "c", 60, null), Video("u2", "Part 2", "c", 60, null))
    val jobs = bookJobs(vids, "Dune", "Frank Herbert", "", "Audiobooks/", 10)
    assertEquals(listOf("01 Part 1.m4a", "02 Part 2.m4a"), jobs.map { it.fileName })
    assertEquals(listOf("1", "2"), jobs.map { it.properties["TRACKNUMBER"] })
    assertEquals(setOf("Dune"), jobs.map { it.properties["ALBUM"] }.toSet())
}
@Test fun single_video_book_is_one_file() = assertEquals("Dune.m4a", bookJobs(listOf(Video("u", "Dune", "c", 60, null)), "Dune", "", "", "A/", 1).single().fileName)
@Test fun music_jobs_skip_unticked() =
    assertEquals(listOf("A - B.m4a"), musicJobs(listOf(PickedVideo(Video("u1", "x", "c", 1, null), "A", "B", true), PickedVideo(Video("u2", "y", "c", 1, null), "C", "D", false)), "Music/", 1).map { it.fileName })
```

- [ ] **Step 2: Run** `--tests '*GuessesTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `Jobs.kt`. Task 4's single-video Download now calls these too.
- [ ] **Step 4: The playlist UI.**
  - A link with both a video and a playlist asks with two `TextButton`s, "Just this video" and "Whole playlist".
  - A playlist shows its name and, per video, a checkbox, the title and length.
    - **Music:** editable artist and title per row, prefilled by `guessSong`.
    - **Audiobook:** a single Book title field (the playlist name), plus Author and Narrator. Rows have checkboxes only.
  - "Download N" queues them all.
  - A video whose stream fails at download time just fails its own job (Review Focus 5).
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: PASS, installed.
- [ ] **Step 6: Device check.** Download a short public playlist into an audiobook folder. Audiobooks shows one book with chapters in playlist order.
- [ ] **Step 7: Commit** `feat: playlist downloads`.

---

### Task 6: Share target, clipboard offer and settings pane

**Files:**
- Modify:
  - `AndroidManifest.xml`: an `ACTION_SEND` `text/plain` intent filter on `MainActivity`, and `android:launchMode="singleTask"`.
  - `MainActivity.kt`: read `EXTRA_TEXT` in `onCreate` and `onNewIntent` into a `mutableStateOf<String?>` passed to `LegatoNav`.
  - `ui/Nav.kt`: a non-null shared link navigates to the Converter section and hands it over.
  - The Settings Converter pane copy.

**Interfaces:**
- Consumes: `ConverterScreen(initialLink, …)` (Task 4).
- Produces: `LegatoNav(…, sharedLink: String?, onSharedLinkUsed: () -> Unit)`.

- [ ] **Step 1: Implement.**
  - The shared text goes to the Converter tab, prefilled and fetched automatically when `parseYouTube` accepts it.
  - On entering the Converter tab with an empty field, if the clipboard holds a YouTube link, show it as a tappable "Paste <link>" suggestion. Don't read the clipboard in the background.
  - Settings pane: the exact copy from the Global Constraints.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: PASS, installed.
- [ ] **Step 3: Device check.**
  - In the YouTube app, tap Share → Legato: Legato opens on Converter with the preview loading.
  - Copy a link, open the Converter tab: the paste suggestion shows.
  - With no folders set, choosing Audiobook shows "Add folder", and picking a folder adds it to Settings too.
- [ ] **Step 4: Commit** `feat: share to Legato, converter settings`.
