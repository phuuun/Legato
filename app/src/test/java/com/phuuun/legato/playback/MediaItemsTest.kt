package com.phuuun.legato.playback

import androidx.media3.common.MediaMetadata
import com.phuuun.legato.library.trackFromRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(track.uri, item.localConfiguration?.uri)
    }

    // Media3 loads artworkUri by reading the whole resource into memory: pointing it at the audio file
    // pulled a 300 MB WAV into RAM for every notification update. The cover travels in extras instead.
    @Test
    fun artwork_uri_is_never_the_audio_file() = assertNull(track.toMediaItem().mediaMetadata.artworkUri)

    @Test
    fun cover_travels_with_the_item() = assertEquals(track.artUri, track.toMediaItem().coverUri())

    @Test
    fun audiobook_items_are_marked_so_the_service_saves_their_spot() {
        assertEquals(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER, track.toMediaItem(audiobook = true).mediaMetadata.mediaType)
        assertNull(track.toMediaItem().mediaMetadata.mediaType)
    }
}
