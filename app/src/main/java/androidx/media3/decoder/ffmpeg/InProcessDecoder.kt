package androidx.media3.decoder.ffmpeg

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer

/**
 * An AAC decoder that runs inside Legato's own process, for Chapterfy: Android's runs in another process, and handing it
 * each 23 ms sample was most of Chapterfy's time. It's Media3's FFmpeg decoder (Jellyfin's build), whose class is kept to
 * this package, hence this file's package. Null when the library isn't there.
 */
@OptIn(UnstableApi::class)
fun inProcessAacDecoder(sampleRate: Int, channels: Int, config: ByteArray): SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>? {
    if (!FfmpegLibrary.isAvailable() || !FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_AAC)) return null
    val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setSampleRate(sampleRate).setChannelCount(channels).setInitializationData(listOf(config)).build()
    return FfmpegAudioDecoder(format, 16, 16, 8192, false)
}

/** The rate [inProcessAacDecoder]'s decoder puts out, known once it has decoded something. */
@OptIn(UnstableApi::class)
val SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>.outputRate: Int get() = (this as FfmpegAudioDecoder).sampleRate

/** The channels it puts out, likewise. */
@OptIn(UnstableApi::class)
val SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>.outputChannels: Int get() = (this as FfmpegAudioDecoder).channelCount
