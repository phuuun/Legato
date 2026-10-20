package com.phuuun.legato.playback

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EqTest {
    @Test
    fun settings_round_trip_and_presets_are_recognised() = runTest {
        val store = EqStore(ApplicationProvider.getApplicationContext())
        assertEquals(EqSettings(), store.settings.first())
        store.set(EqSettings(on = true, gains = EqPreset.Bass.gains))
        assertEquals(EqPreset.Bass, store.settings.first().preset)
        store.set(EqSettings(on = true, gains = listOf(1.5f, 0f, 0f, 0f, -2f)))
        val custom = store.settings.first()
        assertEquals(listOf(1.5f, 0f, 0f, 0f, -2f), custom.gains)
        assertNull(custom.preset)
    }
}
