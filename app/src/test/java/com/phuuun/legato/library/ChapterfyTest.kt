package com.phuuun.legato.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterfyTest {
    @Test
    fun channel_config_from_the_audio_specific_config() {
        assertEquals(2, aacChannelConfig(byteArrayOf(0x12, 0x10))) // AAC-LC, 44.1 kHz, stereo (this book's)
        assertEquals(1, aacChannelConfig(byteArrayOf(0x12, 0x08))) // mono
        assertEquals(2, aacChannelConfig(byteArrayOf(0x11, 0x90.toByte()))) // 48 kHz stereo
        assertNull(aacChannelConfig(byteArrayOf(0x17.toByte(), 0x80.toByte(), 0, 0, 0))) // explicit rate
        assertNull(aacChannelConfig(byteArrayOf(0x12)))
    }

    @Test
    fun only_samples_that_keep_the_channels_reach_ffmpeg() {
        assertTrue(fitsChannels(0x21, 2)) // stereo pair first: this book's normal samples
        assertTrue(fitsChannels(0x20, 2))
        assertFalse(fitsChannels(0x01, 2)) // a single channel on a stereo track: FFmpeg would switch to mono
        assertTrue(fitsChannels(0x01, 1))
        assertFalse(fitsChannels(0x21, 1)) // and the other way round
        assertTrue(fitsChannels(0x80.toByte(), 2)) // data element
        assertTrue(fitsChannels(0xC0.toByte(), 1)) // fill element
        assertFalse(fitsChannels(0xA0.toByte(), 2)) // a new channel layout
    }
}
