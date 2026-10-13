# Murmur — design

Working name. An Android-only local music player, built for one person (phuuun). No accounts, no network, no catalog.

## Intent

- **Why:** the current music app is bloated and slow, locks features away, and has bad UI/UX. A "better Spotify" for *owned* music.
- **Success:** cold start feels instant, gapless lossless playback works over Bluetooth and the lock screen, the whole app is one small APK, and it looks like Before and Tsundoku (pure black, ivory by day, artwork is the only colour).
- **Not goals:** streaming, other platforms, sharing, social, publishing to the Play Store.

## Stack

Kotlin, Jetpack Compose (Material3), Media3 (ExoPlayer + MediaSession), MediaStore, Room, Coil for artwork. Single `app` module, manual DI, MVVM with StateFlow, same as Before. Matches Tsundoku's versions where they overlap: AGP 9.1.x, Kotlin 2.4.x, compile/target SDK 37, minSdk 24, Gradle 9.3.1.

No DI framework, no multi-module, no navigation library beyond Compose Navigation.

## Theme

Copy, don't share, Before's and Tsundoku's tokens (no cross-project dependency):

- Dark: background `#000000`, surface `#0D0D0E`, elevated `#161618`, outline `#26262A`, outlineVariant `#3F3F46`, text `#FFFFFF` / `#A1A1AA` / `#71717A`. `surfaceContainer*` set explicitly so sheets and menus never show Material's lavender tint.
- Light: ivory `#FFFDF4`, ink `#2A2924`, outline `#DDD9C8`.
- Follows system dark/light. No dynamic colour.
- Type: tight bold headlines (-0.5 to -1sp tracking), small wide-tracked caps for eyebrows (`labelSmall`, 1.5sp).

## v1 scope

In:
- Scan the library through MediaStore, and rescan on a `ContentObserver` change.
- Browse songs, albums, artists. Search across all three.
- Gapless playback of FLAC/MP3/AAC/Opus/ALAC (whatever Media3 decodes).
- Queue: play next, add to queue, drag to reorder, remove.
- Playlists: create, rename, delete, add/remove/reorder tracks. Stored in Room.
- Background playback: `MediaSessionService`, media notification, lock-screen, Bluetooth and headset buttons.
- ReplayGain: read from tags, applied as player volume.
- Now-playing screen with artwork, seek bar, shuffle, repeat.

Out until v2: EQ, smart shuffle and mixes, lyrics, sleep timer, widgets, tag editing, Android Auto.

## Architecture

```
app/
  playback/   PlaybackService (MediaSessionService, owns ExoPlayer), ReplayGain
  library/    MediaStoreScanner, Room (playlists, playlist_tracks, play_stats), LibraryRepository
  ui/         theme/, screens (Library, Album, Artist, Playlist, NowPlaying, Search), components/
  MainActivity, MurmurApp
```

- `PlaybackService` is the only thing that touches the player. UI talks to it through a `MediaController`; all queue operations are `MediaController` calls (`addMediaItem`, `moveMediaItem`, ...), so the queue lives in the player, not in a second copy.
- `LibraryRepository` is an interface; the MediaStore-backed implementation is the only one. ViewModels see the interface so tests can fake it.
- Room tables: `playlists(id, name, createdAt)`, `playlist_tracks(playlistId, position, mediaStoreId)`, `play_stats(mediaStoreId, playCount, lastPlayedAt)`. `play_stats` is written in v1 (incremented on track completion) so smart shuffle in v2 needs no migration.
- Tracks are referenced by MediaStore id. If a file is deleted, the playlist row is skipped on load (and pruned on next write); no crash, no tombstones.

## Data flow

Scan → `LibraryRepository` exposes `Flow<List<Track>>` (and album/artist groupings derived from it) → ViewModel → Compose. Play tap → `MediaController.setMediaItems(...)` → service. Player events → `MediaController.Listener` → ViewModel state → now-playing UI.

## Error handling

- Permission (`READ_MEDIA_AUDIO`, plus `POST_NOTIFICATIONS` on 13+) requested on first launch with a one-screen explainer; denial shows an empty state with a re-request button.
- Unplayable or missing file: skip to next, show a brief message, never loop on a bad file.
- Empty library: empty state, not a blank screen.

## Testing

- JVM unit tests for queue/playlist logic (reorder, remove, prune of missing tracks) and ReplayGain gain-to-volume math.
- Manual device checklist (in the plan): gapless between two album tracks, lock-screen controls, Bluetooth play/pause/next, kill-app-while-playing recovery, permission denial.
- Device debugging over adb on the connected phone (`943f15`). Per Before's notes, avoid `connectedDebugAndroidTest` because it uninstalls the app; use `adb install -r` plus `am instrument`.

## Open items

- Final app name and `applicationId` (placeholder: `com.phuuun.murmur`).
- Whether v2 starts with EQ or smart shuffle.
