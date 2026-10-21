# Legato

A player for the music and audiobooks already on your Android phone, in black and ivory. Made for my own use and shared as is.

## What it does

**Music**
- Songs, albums, artists and playlists from the folders you choose, sorted by title, artist or date added.
- Smart playlists: most played, recently played, recently added.
- Edit titles, artists, narrators and covers, and fix untagged artists in bulk. Edits are kept in Legato: your files are never changed.
- ReplayGain, a five-band equalizer, and a full player tinted by the cover.

**Audiobooks**
- A book is one file or a folder of chapter files, and it resumes where you stopped.
- Chapters inside a file are read from M4B/M4A, MP3 (ID3 CHAP) and FLAC/Ogg/Opus tags; a long file without any gets 30-minute parts.
- Bookmarks with a colour and a caption, a sleep timer, playback speed, and 10-second skips with a "back to where I was" button.

**Converter**
- Paste or share a video link and save its audio as an M4A straight into your music or audiobook folder, with its title, cover and chapters kept in Legato. Playlists work too.

Everything stays on the phone: no account, no ads, no tracking. Playlists, bookmarks, edits and progress back up once a day to `Download/Legato backup.json`.

## Install

1. Download the latest `Legato-x.y.z.apk` from [Releases](../../releases).
2. Open it on your phone and allow installing from your browser or file manager.
3. Allow Legato to see your music and audio when it asks. That's read-only: Legato never changes your files. Converted audio can be saved into folders inside Music or Download.

Needs Android 11 or newer on a 64-bit ARM phone (nearly every phone since 2017). To update, install the newer APK over the old one; nothing is lost.

## About the converter

- Legato is not affiliated with, endorsed by or connected to YouTube or Google. YouTube is a trademark of Google LLC.
- It doesn't use the YouTube API, an account or any keys. It uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), the open-source library behind NewPipe, to read the same public pages a web browser loads.
- Legato doesn't host, share or distribute any media. It saves audio you choose to your own phone, for your own listening.
- Only save what you have the right to keep: your own uploads, or recordings under a licence that allows it (Creative Commons, public domain). Saving other content may break YouTube's Terms of Service or copyright law where you live. You're responsible for how you use it.

## Build

```sh
./gradlew :app:testDebugUnitTest   # tests (Robolectric)
./gradlew :app:assembleRelease     # APK in app/build/outputs/apk/release/
```

Release builds are signed only when `LEGATO_STORE_FILE`, `LEGATO_STORE_PASSWORD` and `LEGATO_KEY_ALIAS` are set in `~/.gradle/gradle.properties`; otherwise they come out unsigned.

## Licence

GPL-3.0, see [LICENSE](LICENSE). Legato is built on [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0), [TagLib](https://taglib.org) through [kyant0/taglib](https://github.com/Kyant0/taglib), [AndroidX Media3](https://github.com/androidx/media), Jetpack Compose and Room (Apache-2.0), and [Coil](https://github.com/coil-kt/coil) (Apache-2.0).
