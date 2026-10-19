package com.phuuun.legato.library.edit

import androidx.test.core.app.ApplicationProvider
import com.phuuun.legato.library.trackFromRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagWriterTest {
    @Test
    fun write_to_missing_file_is_false() = runTest {
        val track = trackFromRow(1, "T", "A", "X", 1, 1000, "x.mp3", path = "/nonexistent/x.mp3")
        assertFalse(TagWriter(ApplicationProvider.getApplicationContext()).write(track, mapOf("TITLE" to "New"), CoverChange.Keep))
    }
}
