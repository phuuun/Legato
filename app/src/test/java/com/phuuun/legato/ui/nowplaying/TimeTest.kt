package com.phuuun.legato.ui.nowplaying

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTest {
    @Test
    fun formats_minutes_and_seconds() = assertEquals("3:07", formatTime(187_000))

    @Test
    fun formats_hours() = assertEquals("1:02:03", formatTime(3_723_000))

    @Test
    fun unset_duration_is_zero() = assertEquals("0:00", formatTime(C.TIME_UNSET))

    @Test
    fun zero_duration_track_is_zero() = assertEquals("0:00", formatTime(0))
}
