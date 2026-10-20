package com.phuuun.legato.converter

import com.phuuun.legato.library.trackFromRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuplicatesTest {
    private val library = listOf(
        trackFromRow(7, "Let It Happen", "Tame Impala", "Currents", 1, 1000, "a.mp3", path = "/storage/emulated/0/Music/a.mp3"),
        trackFromRow(8, "Duet", "Daniel Caesar; H.E.R.", "X", 2, 1000, "b.mp3"),
    )

    @Test
    fun same_title_and_artist_ignoring_case_and_spaces() {
        assertEquals(7L, findDuplicate(" tame impala", "let it happen ", library)?.id)
        assertEquals(8L, findDuplicate("H.E.R.; Daniel Caesar", "Duet", library)?.id)
        assertNull(findDuplicate("Tame Impala", "The Less I Know the Better", library))
        assertNull(findDuplicate("Someone Else", "Let It Happen", library))
    }

    @Test
    fun ticked_replace_carries_the_old_track_and_unticked_keeps_both() {
        val v = Video("u", "v", "c", 60, null)
        val jobs = musicJobs(listOf(PickedVideo(v, "Tame Impala", "Let It Happen", true), PickedVideo(v, "Tame Impala", "Let It Happen", true, replace = false)), "Music/", 1, library)
        assertEquals(listOf(7L, null), jobs.map { it.replaces?.id })
        assertEquals(7L, albumJobs(listOf(PickedVideo(v, "Tame Impala", "Let It Happen", true)), "Currents", "Tame Impala", "Music/", 1, library).single().replaces?.id)
    }
}
