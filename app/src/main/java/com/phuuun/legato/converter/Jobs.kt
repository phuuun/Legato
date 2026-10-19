package com.phuuun.legato.converter

import com.phuuun.legato.library.edit.BookEdit
import com.phuuun.legato.library.edit.MusicEdit
import com.phuuun.legato.library.edit.properties

/** A video on the preview with its (edited) guess, and whether it's ticked. */
data class PickedVideo(val video: Video, val artist: String, val title: String, val include: Boolean)

fun musicJobs(picked: List<PickedVideo>, folder: String, firstId: Long): List<DownloadJob> =
    picked.filter { it.include }.mapIndexed { i, p ->
        DownloadJob(firstId + i, p.video.url, p.title, folder, musicFileName(p.artist, p.title), MusicEdit(p.title, listOf(p.artist), "").properties(), p.video.thumbnail)
    }

/**
 * One video is a one-file book. Several are one book: they share the book title (ALBUM), keep their
 * video titles as chapter titles, and are numbered in order so the Audiobooks tab lists them that way.
 */
fun bookJobs(videos: List<Video>, bookTitle: String, author: String, narrator: String, folder: String, firstId: Long): List<DownloadJob> {
    if (videos.size == 1) {
        val v = videos.single()
        return listOf(DownloadJob(firstId, v.url, bookTitle, folder, bookFileName(bookTitle), BookEdit(bookTitle, null, author, narrator).properties(), v.thumbnail))
    }
    return videos.mapIndexed { i, v ->
        val properties = BookEdit(bookTitle, v.title, author, narrator).properties() + ("TRACKNUMBER" to "${i + 1}")
        DownloadJob(firstId + i, v.url, v.title, folder, chapterFileName(i + 1, v.title), properties, v.thumbnail)
    }
}
