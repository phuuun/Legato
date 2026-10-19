package com.phuuun.legato.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import org.junit.Test

class ReplayGainTest {
    @Test
    fun parses_track_gain_with_db_suffix() = assertEquals(-7.5f, parseGainDb(mapOf("REPLAYGAIN_TRACK_GAIN" to "-7.50 dB")))

    @Test
    fun tag_keys_are_case_insensitive() = assertEquals(-3f, parseGainDb(mapOf("replaygain_track_gain" to "-3 dB")))

    @Test
    fun falls_back_to_album_gain() = assertEquals(-4.2f, parseGainDb(mapOf("REPLAYGAIN_ALBUM_GAIN" to "-4.20 dB")))

    @Test
    fun garbage_gain_is_null() = assertNull(parseGainDb(mapOf("REPLAYGAIN_TRACK_GAIN" to "loud")))

    @Test
    fun negative_gain_attenuates() = assertEquals(0.501f, gainToVolume(-6f, null), 0.01f)

    @Test
    fun positive_gain_never_exceeds_one() = assertEquals(1f, gainToVolume(9f, null))

    @Test
    fun null_gain_is_full_volume() = assertEquals(1f, gainToVolume(null, null))

    @Test
    fun nan_gain_is_full_volume() = assertEquals(1f, gainToVolume(Float.NaN, null))

    @Test
    fun peak_caps_volume() {
        val v = gainToVolume(-2f, 0.5f)
        assertTrue(v in 0f..1f)
        // A peak above full scale caps the volume so the loudest sample can't clip.
        assertEquals(1f / 1.6f, gainToVolume(0f, 1.6f), 0.001f)
    }

    @Test
    fun reads_gain_from_flac_mp3_and_m4a_tags() {
        assertEquals(-1f, parseGainDb(replayGainTags(Metadata(VorbisComment("REPLAYGAIN_TRACK_GAIN", "-1 dB")))))
        assertEquals(-2f, parseGainDb(replayGainTags(Metadata(TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", listOf("-2 dB"))))))
        assertEquals(-3f, parseGainDb(replayGainTags(Metadata(InternalFrame("com.apple.iTunes", "replaygain_track_gain", "-3 dB")))))
    }
}
