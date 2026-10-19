package com.phuuun.legato.playback

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlin.math.pow

private fun Map<String, String>.number(key: String): Float? =
    entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
        ?.trim()?.removeSuffix("dB")?.removeSuffix("db")?.trim()?.toFloatOrNull()

/** Track gain in dB, else album gain, else null. Tag keys are matched case-insensitively. */
fun parseGainDb(tags: Map<String, String>): Float? = tags.number("REPLAYGAIN_TRACK_GAIN") ?: tags.number("REPLAYGAIN_ALBUM_GAIN")

fun parsePeak(tags: Map<String, String>): Float? = tags.number("REPLAYGAIN_TRACK_PEAK") ?: tags.number("REPLAYGAIN_ALBUM_PEAK")

/** Player volume for a gain: 10^(dB/20), never above 1 (no boosting), capped at 1/peak, and 1 when unknown. */
fun gainToVolume(gainDb: Float?, peak: Float?): Float {
    if (gainDb == null || gainDb.isNaN()) return 1f
    var v = 10f.pow(gainDb / 20f)
    if (peak != null && peak > 0f) v = minOf(v, 1f / peak)
    return if (v.isNaN()) 1f else v.coerceIn(0f, 1f)
}

/** ReplayGain tags from FLAC/Ogg (Vorbis comments), MP3 (ID3 TXXX) and M4A (iTunes freeform atoms). */
@OptIn(UnstableApi::class)
fun replayGainTags(metadata: Metadata): Map<String, String> = buildMap {
    for (i in 0 until metadata.length()) {
        when (val e = metadata.get(i)) {
            is VorbisComment -> put(e.key, e.value)
            is TextInformationFrame -> if (e.id == "TXXX" && e.description != null) e.values.firstOrNull()?.let { put(e.description!!, it) }
            is InternalFrame -> put(e.description, e.text)
        }
    }
}
