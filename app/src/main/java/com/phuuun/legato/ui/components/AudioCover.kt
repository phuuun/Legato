package com.phuuun.legato.ui.components

import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** A track's own cover. The URI is the track's, with a "?v=<modified>" so an edited file gets a fresh cache key. */
data class AudioCover(val uri: Uri)

/** MediaStore's thumbnail of an audio item is its embedded picture (or a cover.jpg beside it); no art throws, and the placeholder stays. */
class AudioCoverFetcher(private val cover: AudioCover, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = options.context.contentResolver.loadThumbnail(cover.uri.buildUpon().clearQuery().build(), Size(512, 512), null)
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<AudioCover> {
        override fun create(data: AudioCover, options: Options, imageLoader: ImageLoader) = AudioCoverFetcher(data, options)
    }
}

class AudioCoverKeyer : Keyer<AudioCover> {
    override fun key(data: AudioCover, options: Options) = data.uri.toString()
}
