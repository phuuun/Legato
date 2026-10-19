package com.phuuun.legato.converter

import java.net.URI

/** What a shared or pasted link points at. Both set means a video opened from inside a playlist. */
data class YouTubeLink(val video: String?, val playlist: String?)

private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
private val firstUrl = Regex("""https?://\S+""")

/** The first YouTube link in [text] (shares often wrap it in a title), or null. "RD…" lists are endless mixes, not playlists. */
fun parseYouTube(text: String): YouTubeLink? {
    val uri = firstUrl.find(text)?.value?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host !in hosts) return null
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    val path = uri.path.orEmpty()
    val video = when {
        host == "youtu.be" -> path.trim('/').substringBefore('/')
        path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/')
        else -> query["v"]
    }?.takeIf { it.isNotBlank() }
    val playlist = query["list"]?.takeIf { it.isNotBlank() && !it.startsWith("RD") }
    return if (video == null && playlist == null) null else YouTubeLink(video, playlist)
}

fun videoUrl(id: String) = "https://www.youtube.com/watch?v=$id"

fun playlistUrl(id: String) = "https://www.youtube.com/playlist?list=$id"
