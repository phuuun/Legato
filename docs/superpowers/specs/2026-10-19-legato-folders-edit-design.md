# Legato phase 2: folders, audiobooks list, edit

Follows the v1 design (`2026-10-13-legato-design.md`), which is now built. Phase 3 is the audiobook player (resume, speed, sleep timer). Phase 4 is the YouTube converter, which will save into the folders chosen here.

## Intent

- **Why:** the phone's library is 38 YouTube rips spread over `Download/`, `Download/MUSIC/` and `Download/MUSIC/Etc/`, all showing "Unknown artist". The user wants to choose which folders are music and which are audiobooks, fix tags fast, and see audiobooks in their own tab.
- **Success:** adding `Download/MUSIC` as the music folder shows exactly its files. All 38 songs can get a proper artist and title in one sitting. Edits live in the files, so they survive a reinstall and show in other apps. Audiobooks show as books.
- **Not in this phase:** audiobook playback features (phase 3) and the converter (phase 4).

## Decisions taken with the user

- All files access (`MANAGE_EXTERNAL_STORAGE`). Legato is sideloaded, never on the Play Store.
- Songs with several artists are split: the song appears under each artist.
- A bulk "fix unknown artists" screen, with guessed artist and title. Edit pre-fills the same guess.
- Audiobooks come in both shapes: one file per book, or chapter files. Files that share a book title form one book.
- The "LEGATO" eyebrow and the "Library" title are replaced by a "Legato" title at the top left of every main screen, with a gear at the top right.
- Settings is two panes, like a file manager: Music / Audiobooks / Converter on the left, scrollable settings on the right.
- Rough edges get fixed along the way, on the device.

## 1. Header and settings

- Music, Audiobooks and Converter screens share one header: "Legato" (headlineLarge) on the left, a gear `IconButton` on the right. The Music screen keeps its search box and the SONGS / ALBUMS / ARTISTS / PLAYLISTS tabs below the header.
- The gear opens a Settings route with a back arrow and a "Settings" title. Left rail, about 120dp wide: MUSIC, AUDIOBOOKS, CONVERTER in the library tab style (small caps; the selected one is bold and bright). Right pane: a scrollable list for the selected section.
- **Music pane:** FOLDERS, with each folder's path (`Download/MUSIC`) and a ✕ to remove it, then "Add folder". A hint reads: "Only files directly in these folders show up. With no folders, all music shows." Below that is "Fix unknown artists (N)", which opens the bulk fix (§5).
- **Audiobooks pane:** FOLDERS, with ✕ and "Add folder".
- **Converter pane:** "Comes with the converter."
- "Add folder" opens Android's folder picker (`ACTION_OPEN_DOCUMENT_TREE`). The tree's document id `primary:Download/MUSIC` maps to the relative path `Download/MUSIC/`. Other volumes (SD cards) show a toast, "Only internal storage for now".
- Folder lists live in SharedPreferences as two string sets, `music_folders` and `audiobook_folders`, exposed as `Flow<Set<String>>`.

## 2. Permission

- `PermissionGate` asks for all files access instead of `READ_MEDIA_AUDIO`. It checks `Environment.isExternalStorageManager()`. The button opens `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` for Legato, and the gate checks again when the app resumes.
- The manifest declares `MANAGE_EXTERNAL_STORAGE` and drops the old read permissions.
- `minSdk` goes from 24 to 30, which both all files access and `RELATIVE_PATH` need.

## 3. Library by folder

- MediaStore stays the index. Song ids, playlists and play stats are unchanged.
- **Music query:** with no music folders, use today's selection (`IS_MUSIC != 0 AND DURATION > 0`). With folders, use `RELATIVE_PATH IN (…) AND DURATION > 0`. That matches exact folders only, so subfolders are excluded. It also matches any audio type, so WAV files and files MediaStore didn't flag as music still count.
- **Audiobooks query:** `RELATIVE_PATH IN (audiobook folders) AND DURATION > 0`. With no folders it returns nothing.
- Both queries run again when MediaStore changes or a folder list changes.
- `Track` gains `path` (the file path, for writing tags), `composer` (the narrator), `relativePath`, and `artists: List<String>`, the artist tag split on `;` and trimmed. `artist` stays as the display string, the artists joined with ", ".
- **Artists tab:** a song appears under each of its artists.
- **Per-song covers:** a track's artwork is the picture embedded in its own file, not MediaStore's album art. Untagged rips in one folder share one MediaStore album id, so album art would put one song's cover on all of them. The track URI is loaded through a small Coil fetcher (`ContentResolver.loadThumbnail` for audio items), with the surfaceVariant box as the placeholder.

## 4. Audiobooks list

- A book is a group of audiobook-folder files that share a book title (`ALBUM`). If `ALBUM` is blank, or equals the file's folder name (MediaStore's default for untagged files), the file is its own book, named by its title.
- **Audiobooks tab:** header, then a two-column grid of books showing cover, title and author. With no folders, an empty state: "Add an audiobook folder in Settings".
- **Tapping a book:** a single-file book plays straight away and opens the player. A multi-file book opens a book screen listing its chapters by track number, then title. Tapping a chapter plays the book from there.
- Resume position, speed and sleep timer are phase 3.

## 5. Edit and bulk fix

- **Entry points:** "Edit info…" in the long-press menu on songs and chapters, and a pencil icon in the full player for the current track.
- **Music fields:** cover, title, artists (one field per artist, with "+ Add artist" and a ✕ on each), album.
- **Audiobook fields:** cover, book title, chapter title (shown only when the book has several files), author, narrator.
- **Tags written** (TagLib property map):
  - `TITLE`.
  - `ARTIST`: the artists joined with "; ". For an audiobook, the author.
  - `ALBUM`: the book title for audiobooks.
  - `ALBUMARTIST`: the author, for audiobooks.
  - `COMPOSER`: the narrator.
  - Cover: one front-cover JPEG, scaled down to at most 1000px.
  - A single-file book writes its book title to both `TITLE` and `ALBUM`.
- **Cover picking:** the Photo Picker (images only), plus "Remove cover".
- **Library:** `io.github.kyant0:taglib`, the TagLib engine, which handles MP3, WAV, M4A/M4B, FLAC and Ogg/Opus. It ships arm64-v8a only (`abiFilters`) to keep the APK small.
- **Saving:** Legato opens the file read-write, saves the property map and pictures, then calls `MediaScannerConnection.scanFile`. MediaStore updates the row in place, so the id stays the same. A failed save shows "Couldn't save <title>". Tracks already in the queue keep their old text until they're queued again.
- **Guess**, a pure function:
  1. Split the title on the first " - ", " – " or " — ": artist, then title.
  2. Drop any bracketed part `(…)` or `[…]` that contains a junk word.
  3. Strip trailing junk phrases: official, video, music video, audio, lyric(s), lirik, remaster(ed), 4k, hd, mv, visualizer.
  4. With no separator, the guess is the title alone, with the artist left blank.
- **Bulk fix:** every Unknown-artist song in the music library, one row each. A row has its guessed artist and title (both editable), a ⇄ button that swaps them (some rips are "Title - Artist") and an include checkbox. "Save N" writes them one by one with a running count, then lists any failures.

## Testing

- **JVM tests:**
  - The guess, with the phone's real titles as fixtures.
  - Tree document id to relative path.
  - Book grouping, including the folder-name rule, multi-file books and chapter order.
  - Artist split and join.
  - The property map built for music and for audiobooks.
- **Robolectric:** the folder store round-trip.
- **On the phone:** TagLib is native code, so tag writing is checked on the device. Edit an MP3 and a WAV, then confirm Legato and another player both show the new tags and cover.
- **Device walk:**
  - Adding and removing folders filters the library.
  - The permission flow.
  - Bulk fix on the real 38 songs.
  - Audiobook grouping.

## Open items

- SD card folders.
- The junk word list gets tuned on the real files.
