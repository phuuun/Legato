package com.phuuun.legato.playback

import com.phuuun.legato.library.trackFromRow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemsTest {
    private val track = trackFromRow(42, "Halo", "Beyoncé", "I Am... Sasha Fierce", 10, 1000, "a.flac")

    @Test
    fun round_trips_track_id() {
        assertEquals(42L, track.toMediaItem().trackId())
    }

    @Test
    fun metadata_carries_title_artist_album() {
        val item = track.toMediaItem()
        val m = item.mediaMetadata
        assertEquals("Halo", m.title.toString())
        assertEquals("Beyoncé", m.artist.toString())
        assertEquals("I Am... Sasha Fierce", m.albumTitle.toString())
        assertEquals(track.artUri, m.artworkUri)
        assertEquals(track.uri, item.localConfiguration?.uri)
    }
}
