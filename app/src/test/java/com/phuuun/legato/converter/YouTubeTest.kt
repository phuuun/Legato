package com.phuuun.legato.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeTest {
    @Test
    fun best_audio_is_the_highest_m4a() {
        val best = bestAudio(listOf(AudioOption("a", true, 48), AudioOption("b", false, 160), AudioOption("c", true, 128)))
        assertEquals("c", best?.url)
    }

    @Test
    fun no_m4a_means_no_audio() = assertNull(bestAudio(listOf(AudioOption("b", false, 160))))
}
