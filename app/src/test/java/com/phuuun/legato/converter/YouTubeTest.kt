package com.phuuun.legato.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.UnknownHostException

class YouTubeTest {
    @Test
    fun best_audio_is_the_highest_m4a() {
        val best = bestAudio(listOf(AudioOption("a", true, 48), AudioOption("b", false, 160), AudioOption("c", true, 128)))
        assertEquals("c", best?.url)
    }

    @Test
    fun no_m4a_means_no_audio() = assertNull(bestAudio(listOf(AudioOption("b", false, 160))))

    @Test
    fun errors_say_what_went_wrong() {
        assertEquals(EXTRACTOR_BROKE, youTubeError(ParsingException("Could not get ytInitialData"), "x"))
        assertEquals("This video is private", youTubeError(ContentNotAvailableException("This video is private"), "x"))
        assertTrue(youTubeError(ReCaptchaException("reCaptcha", "u"), "x").contains("captcha"))
        assertTrue(youTubeError(UnknownHostException("www.youtube.com"), "x").startsWith("Couldn't reach YouTube"))
        assertEquals("No M4A audio for this video", youTubeError(IllegalStateException("No M4A audio for this video"), "x"))
        assertEquals("x", youTubeError(RuntimeException(), "x"))
    }
}
