package com.phuuun.legato.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun scheme_is_the_bold_palette() {
        assertEquals(Bold.Paper, DarkScheme.background)
        assertEquals(Bold.Mint, DarkScheme.primary)
    }

    @Test
    fun containers_never_fall_back_to_material_lavender() {
        assertEquals(Bold.Paper, DarkScheme.surfaceContainerLowest)
        assertEquals(Bold.SurfaceHigh, DarkScheme.surfaceContainerHigh)
        assertEquals(Bold.SurfaceHigh, DarkScheme.secondaryContainer)
    }
}
