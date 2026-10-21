package com.phuuun.legato.converter

import androidx.core.text.HtmlCompat
import com.phuuun.legato.library.Chapter
import com.phuuun.legato.library.chaptersFromText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

data class AudioOption(val url: String, val isM4a: Boolean, val bitrate: Int)

/** M4A only (AAC, plays and tags everywhere); the highest bitrate YouTube offers, usually 128 kbps. */
fun bestAudio(options: List<AudioOption>): AudioOption? = options.filter { it.isM4a }.maxByOrNull { it.bitrate }

data class Video(val url: String, val title: String, val uploader: String, val durationSec: Long, val thumbnail: String?)

data class Playlist(val name: String, val videos: List<Video>)

data class Stream(val audioUrl: String, val chapters: List<Chapter>)

/** YouTube through NewPipeExtractor, the engine save4k and NewPipe use. Every call is network work on the IO pool. */
object YouTube {
    private val started: Unit by lazy { NewPipe.init(HttpDownloader) }

    suspend fun video(url: String): Video = withContext(Dispatchers.IO) {
        started
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        Video(url, info.name, info.uploaderName.orEmpty(), info.duration, info.thumbnails.maxByOrNull { it.height }?.url)
    }

    /** Every video in the playlist, following YouTube's pages to the end. */
    suspend fun playlist(url: String): Playlist = withContext(Dispatchers.IO) {
        started
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val items = info.relatedItems.toMutableList()
        var page = info.nextPage
        while (page != null) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            items += more.items
            page = more.nextPage
        }
        Playlist(info.name, items.map { Video(it.url, it.name, it.uploaderName.orEmpty(), it.duration, it.thumbnails.maxByOrNull { t -> t.height }?.url) })
    }

    /**
     * A fresh stream link (they expire within hours and are tied to this phone's IP, so fetch one right before
     * downloading), and the video's chapters: YouTube's own, else the timestamp list in its description.
     */
    suspend fun stream(videoUrl: String): Stream = withContext(Dispatchers.IO) {
        started
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val url = bestAudio(info.audioStreams.map { AudioOption(it.content, it.format == MediaFormat.M4A, it.averageBitrate) })?.url
            ?: error("No M4A audio for this video")
        val chapters = info.streamSegments.map { Chapter(it.title.orEmpty(), it.startTimeSeconds * 1000L) }.takeIf { it.size >= 2 }
            ?: info.description?.let { d ->
                chaptersFromText(if (d.type == Description.HTML) HtmlCompat.fromHtml(d.content, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() else d.content)
            }.orEmpty()
        Stream(url, chapters)
    }
}

/** Said when YouTube changed and the extractor can't read it any more: only a newer Legato (with a newer extractor) fixes that. */
const val EXTRACTOR_BROKE = "YouTube changed something and Legato's extractor broke. Update Legato if there's a newer version on GitHub Releases."

/**
 * What to tell the user when fetching from YouTube fails: one video being unavailable, no connection, a captcha,
 * or the extractor broken by a YouTube change. Anything else keeps its own message.
 */
fun youTubeError(e: Throwable, fallback: String): String = when (e) {
    is ContentNotAvailableException -> e.message ?: "This video isn't available"
    is ReCaptchaException -> "YouTube is asking this network for a captcha. Try again later, or on another network."
    is ExtractionException -> EXTRACTOR_BROKE
    is UnknownHostException, is SocketTimeoutException, is ConnectException -> "Couldn't reach YouTube. Check the internet and try again."
    else -> e.message ?: fallback
}

/** NewPipe's network hook, on plain HttpURLConnection; a desktop browser's User-Agent gets the regular web pages. */
private object HttpDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val c = URI(request.url()).toURL().openConnection() as HttpURLConnection
        c.requestMethod = request.httpMethod()
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
        request.headers().forEach { (k, vs) -> vs.forEach { c.addRequestProperty(k, it) } }
        request.dataToSend()?.let { data -> c.doOutput = true; c.outputStream.use { it.write(data) } }
        val code = c.responseCode
        val body = (if (code >= 400) c.errorStream else c.inputStream)?.use { String(it.readBytes()) }.orEmpty()
        return Response(code, c.responseMessage, c.headerFields.filterKeys { it != null }, body, c.url.toString())
    }
}
