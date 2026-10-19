package com.phuuun.legato.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test
    fun watch_short_music_and_shorts_links() {
        assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("https://youtu.be/dQw4w9WgXcQ?si=Ab12Cd"))
        assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("https://music.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"))
        assertEquals(YouTubeLink("abc123DEF45", null), parseYouTube("https://youtube.com/shorts/abc123DEF45"))
    }

    @Test
    fun playlists_and_video_in_playlist() {
        assertEquals(YouTubeLink(null, "PLx0sYbCqOb8Q"), parseYouTube("https://www.youtube.com/playlist?list=PLx0sYbCqOb8Q"))
        assertEquals(YouTubeLink("dQw4w9WgXcQ", "PLx0sYbCqOb8Q"), parseYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLx0sYbCqOb8Q&index=2"))
    }

    @Test
    fun mixes_are_not_playlists() = assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"))

    @Test
    fun link_inside_shared_text() = assertEquals(YouTubeLink("dQw4w9WgXcQ", null), parseYouTube("Never Gonna Give You Up https://youtu.be/dQw4w9WgXcQ"))

    @Test
    fun other_sites_are_rejected() {
        assertNull(parseYouTube("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(parseYouTube("hello"))
    }
}
