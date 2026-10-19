# Legato converter: YouTube to your library

Follows phase 2 (`2026-10-19-legato-folders-edit-design.md`): folders, edit, audiobooks. Fills the third bottom tab, Converter.

## Intent

- **Why:** the user saves music and audiobooks from YouTube with save4k, then fixes the tags and moves the files by hand. Legato should do it in one go: link in, a tagged file with a cover out, in the right folder, playable at once.
- **Success:**
  - Sharing a YouTube video to Legato, or pasting its link, then tapping Download produces an `.m4a` in the chosen folder with title, artist and cover, and it shows up in Music or Audiobooks without a rescan.
  - A playlist link downloads every video in it. An audiobook playlist becomes one book with its chapters in order.
  - Someone who just installed Legato and has no folders yet is never stuck: the converter offers "Add folder" right there.
- **Not in this phase:** formats other than M4A, other sites, video, scheduled or background-resumed downloads after the app process dies.

## Decisions taken with the user

- **The flow:** Share → Legato from YouTube, or paste the link in the Converter tab. A preview card shows the thumbnail, title and length, with artist and title guessed and editable. The user picks Music or Audiobook and a folder, then taps Download. Progress shows in the tab and in a notification. The file lands tagged, with the thumbnail as its cover.
- **Playlists:** supported.
- **Destination:** the folder choice is limited to the folders set in Settings for that kind. Legato remembers the last choice. With no folder for the chosen kind, an "Add folder" button sits where the choice would be. It opens the same folder picker as Settings and adds the folder there too.

## Findings that shape the design (checked 2026-10-19)

- **save4k is a NewPipe fork:** NewPipeExtractor, plus NewPipe's PO-token web view, plus FFmpegKit.
- **NewPipeExtractor `v0.26.5`** (JitPack `com.github.TeamNewPipe:NewPipeExtractor`), run from this network with no PO token:
  - Extracted the video's metadata.
  - Returned audio streams 139/140 (M4A, 48/128 kbps) and 249/250/251 (Opus, up to 160 kbps), all as direct file links.
  - Range requests at both the start and the end of the file returned 206.
- **yt-dlp** has needed an external JavaScript runtime (Deno) for YouTube since late 2025, and Android builds don't ship one. Ruled out.
- **YouTube's M4A is fragmented MP4:** `ftyp, moov, sidx, moof/mdat…`. ExoPlayer plays and seeks it. Whether TagLib can write tags and a cover into it safely is unknown, so it's tested first (§4).

## 1. Getting the link

- `MainActivity` gains an `ACTION_SEND` / `text/plain` intent filter. Sharing from the YouTube app, YouTube Music or a browser opens Legato on the Converter tab with the link filled in.
- **Converter tab:** a link field with a paste-from-clipboard button and "Fetch". Tapping the tab with a YouTube link on the clipboard offers it in the field's placeholder.
- **Accepted links:** `youtube.com/watch?v=`, `youtu.be/`, `music.youtube.com/watch?v=`, `youtube.com/shorts/`, and any of these carrying `list=`, or `youtube.com/playlist?list=`. A link with both `v` and `list` asks "Just this video / Whole playlist". Anything else shows "That isn't a YouTube link".

## 2. Preview and destination

- **A video:** a card with the thumbnail, title, channel and length, then editable fields:
  - **Music:** Artist and Title (the guess, §3).
  - **Audiobook:** Book title and Author (the book guess, §3), plus Narrator, blank.
- **A playlist:** the playlist name, then one row per video with a checkbox (all ticked), its title and its length.
  - **Music:** each video gets its own guessed artist and title, shown on its row and editable there.
  - **Audiobook:** the playlist name becomes the editable Book title shared by all items. Each chapter keeps its video title, and its track number is its position in the playlist.
- **Destination row:**
  - A MUSIC / AUDIOBOOK toggle, in the library tab style.
  - A folder dropdown listing that kind's folders from Settings, defaulting to the last one used for that kind.
  - With no folder for the kind, an "Add folder" button instead. It opens the folder picker and adds the result to Settings.
  - The last folder per kind lives in SharedPreferences `settings` under the keys `last_music_folder` and `last_audiobook_folder`.
- "Download" queues the job(s) and clears the card.

## 3. Guesses

- **Music**, `guessSong(title, uploader)`:
  - An uploader ending in " - Topic" (YouTube Music's auto-generated channels) means the artist is the uploader without that suffix, and the title is the video title, cleaned.
  - Otherwise use phase 2's `guessFromTitle`.
  - When that finds no artist, the artist is the uploader with a trailing "VEVO" or "Official" removed.
- **Audiobook**, `guessBook(title)`:
  - Text before " by " is the book title, and the text after it, up to the first `|`, `_`, ` - `, `(` or `[`, is the author.
  - Junk such as "Full Audiobook", "Audiobook", "Unabridged" and "Complete" is stripped.
  - With no " by ", the whole cleaned title is the book title and the author is blank.
  - Example: "The Inheritance Games by Jennifer Lynn Barnes _ Full Audiobook _ The Inheritance Games #1" gives "The Inheritance Games" / "Jennifer Lynn Barnes".

## 4. Download, tag, file

- **Engine:** NewPipeExtractor with a small `HttpURLConnection` `Downloader`. Legato picks the highest-bitrate M4A audio stream (itag 140 when present). No PO-token web view now; add one only if YouTube starts refusing streams (403) on the phone.
- **Download service:** a foreground service (`foregroundServiceType="dataSync"`) works through a queue one job at a time.
  - It downloads in 8 MB `Range` chunks to `<folder>/<name>.m4a.part`. Chunks avoid long single responses, which YouTube throttles or cuts.
  - It shows progress in one notification and in the Converter tab.
  - `POST_NOTIFICATIONS` is requested the first time a download starts.
- **Job states:** queued → downloading (percent) → tagging → done, or failed with the reason and a Retry button. Jobs live in an app-wide `StateFlow` that the service writes and the tab reads.
- **Tagging:** reuses phase 2's `TagWriter` and tag maps (now taking a file path), plus `TRACKNUMBER` for playlist chapters.
  - The cover is the largest thumbnail, centre-cropped to a square and scaled to ≤ 1000px JPEG. Video thumbnails are 16:9, and a square crop matches the rest of the library.
  - **First build step, on the phone:** write tags and a cover into a downloaded fragmented M4A, then check that ExoPlayer plays it, seeks, and MediaStore reads the title. If it works, ship that. If not, remux to a plain MP4 with Media3 Transformer (no re-encode) before tagging. Media3 is already a dependency.
- **Finishing:** after tagging, the service renames `.part` to `.m4a` and runs `MediaScannerConnection.scanFile`, and the file appears in its tab.
- **File names:**
  - Music: "Artist - Title.m4a".
  - A one-file book: "Book title.m4a".
  - A playlist chapter: "NN Chapter title.m4a".
  - `/ \ : * ? " < > |` are replaced, names are trimmed to 120 characters, and an existing name gets " (2)", " (3)" and so on, so nothing is ever overwritten.
- **Failures:** a failed job leaves no `.part` file behind. Retry fetches a fresh stream link, because links expire after a few hours and are tied to the IP address.

## 5. Settings → Converter pane

- One line: "Downloads go to the folder you pick in the Converter. Add more folders under Music and Audiobooks."
- No other settings now. Format and quality choices come later if they're ever wanted.

## Licence

NewPipeExtractor is GPLv3. If Legato is shared beyond the user's own phone, it must be released under GPLv3 with its source. The repository gains a `LICENSE` (GPLv3) when the extractor dependency lands.

## Testing

- **JVM tests:**
  - Link parsing: every accepted form, `si=` and other tracking parameters, the video-plus-list choice, rejection of non-YouTube links.
  - `guessSong`, using real titles (Topic channels, VEVO, no separator).
  - `guessBook`, using the phone's real audiobook title.
  - Best-stream choice (the highest M4A bitrate; none when there's no M4A).
  - File-name sanitising and collision numbering.
  - The centre-crop rectangle for 16:9, 4:3 and square images.
  - The job state transitions in the queue.
- **On the phone:**
  - The fragmented-M4A tagging check (§4).
  - Share from the YouTube app into a music folder.
  - Paste a playlist link into an audiobook folder, and check it becomes one book with its chapters in order.
  - The first run with no folders goes through "Add folder".
  - Kill the network mid-download: the job fails, Retry finishes it, and no `.part` file is left.

## Open items

- A PO-token web view, if YouTube starts requiring one for these clients.
- The remux fallback, if the fragmented-M4A tagging check fails.
