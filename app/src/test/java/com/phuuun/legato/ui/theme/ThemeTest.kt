package com.phuuun.legato.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun dark_background_is_pure_black() {
        assertEquals(Color(0xFF000000), DarkScheme.background)
        assertEquals(Color(0xFF000000), DarkScheme.surfaceContainerLowest)
    }

    @Test
    fun light_background_is_ivory() {
        assertEquals(Color(0xFFFFFDF4), LightScheme.background)
        assertEquals(Color(0xFF2A2924), LightScheme.primary)
    }
}
