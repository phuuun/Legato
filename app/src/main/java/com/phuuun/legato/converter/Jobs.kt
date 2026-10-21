package com.phuuun.legato.converter

import com.phuuun.legato.library.Track
import com.phuuun.legato.library.albumTag
import com.phuuun.legato.library.splitArtists
import com.phuuun.legato.library.edit.BookEdit
import com.phuuun.legato.library.edit.MusicEdit
import com.phuuun.legato.library.edit.properties

/** A video on the preview with its (edited) guess, whether it's ticked, and whether it replaces the same song already on the phone. */
data class PickedVideo(val video: Video, val artist: String, val title: String, val include: Boolean, val replace: Boolean = true)

/** A song already in [library] with the same title and the same artists, ignoring case and spacing. */
fun findDuplicate(artist: String, title: String, library: List<Track>): Track? {
    val names = splitArtists(artist).map { it.lowercase() }.toSet()
    val t = title.trim().lowercase()
    return library.firstOrNull { it.title.trim().lowercase() == t && it.artists.map { a -> a.lowercase() }.toSet() == names }
}

/** The one-file book in [audiobooks] with this title, ignoring case and spacing: what converting it again replaces. */
fun findBook(title: String, audiobooks: List<Track>): Track? {
    val t = title.trim().lowercase()
    return audiobooks.filter { it.albumTag.ifEmpty { it.title }.trim().lowercase() == t }.singleOrNull()
}

private fun PickedVideo.duplicateIn(library: List<Track>) = if (replace) findDuplicate(artist, title, library) else null

fun musicJobs(picked: List<PickedVideo>, folder: String, firstId: Long, library: List<Track> = emptyList()): List<DownloadJob> =
    picked.filter { it.include }.mapIndexed { i, p ->
        DownloadJob(firstId + i, p.video.url, p.title, folder, musicFileName(p.artist, p.title), MusicEdit(p.title, listOf(p.artist), "").properties(), p.video.thumbnail, p.duplicateIn(library))
    }

/** One album for all: same album and album artist, numbered in playlist order, and the first video's thumbnail as every cover. */
fun albumJobs(picked: List<PickedVideo>, album: String, albumArtist: String, folder: String, firstId: Long, library: List<Track> = emptyList()): List<DownloadJob> {
    val ticked = picked.filter { it.include }
    val cover = ticked.firstOrNull()?.video?.thumbnail
    return ticked.mapIndexed { i, p ->
        val properties = MusicEdit(p.title, listOf(p.artist), album).properties() + mapOf("ALBUMARTIST" to albumArtist.trim(), "TRACKNUMBER" to "${i + 1}")
        DownloadJob(firstId + i, p.video.url, p.title, folder, musicFileName(p.artist, p.title), properties, cover, p.duplicateIn(library))
    }
}

/**
 * One video is a one-file book. Several are one book: they share the book title (ALBUM), keep their
 * video titles as chapter titles, and are numbered in order so the Audiobooks tab lists them that way.
 */
fun bookJobs(videos: List<Video>, bookTitle: String, author: String, narrator: String, folder: String, firstId: Long, replaces: Track? = null): List<DownloadJob> {
    if (videos.size == 1) {
        val v = videos.single()
        return listOf(DownloadJob(firstId, v.url, bookTitle, folder, bookFileName(bookTitle), BookEdit(bookTitle, null, author, narrator).properties(), v.thumbnail, replaces))
    }
    return videos.mapIndexed { i, v ->
        val properties = BookEdit(bookTitle, v.title, author, narrator).properties() + ("TRACKNUMBER" to "${i + 1}")
        DownloadJob(firstId + i, v.url, v.title, folder, chapterFileName(i + 1, v.title), properties, v.thumbnail)
    }
}
