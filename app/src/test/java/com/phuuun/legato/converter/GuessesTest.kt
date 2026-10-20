package com.phuuun.legato.converter

import com.phuuun.legato.library.edit.Guess
import org.junit.Assert.assertEquals
import org.junit.Test

class GuessesTest {
    @Test
    fun topic_channel_is_the_artist() = assertEquals(Guess("Agnes Monica", "Rindu"), guessSong("Rindu", "Agnes Monica - Topic"))

    @Test
    fun artist_dash_title_wins() =
        assertEquals(Guess("Rick Astley", "Never Gonna Give You Up"), guessSong("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", "Rick Astley"))

    @Test
    fun no_dash_falls_back_to_the_channel() = assertEquals(Guess("Rick Astley", "Never Gonna Give You Up"), guessSong("Never Gonna Give You Up", "Rick Astley"))

    @Test
    fun vevo_is_dropped_from_the_channel() = assertEquals("Rick Astley", guessSong("Song", "Rick AstleyVEVO").artist)

    @Test
    fun book_by_author() =
        assertEquals(BookGuess("The Inheritance Games", "Jennifer Lynn Barnes"), guessBook("The Inheritance Games by Jennifer Lynn Barnes _ Full Audiobook _ The Inheritance Games #1"))

    @Test
    fun book_without_author() {
        assertEquals(BookGuess("Dune", ""), guessBook("Dune (Full Audiobook)"))
        assertEquals(BookGuess("Harry Potter and the Sorcerer's Stone", ""), guessBook("Harry Potter and the Sorcerer's Stone - Audiobook"))
    }

    @Test
    fun book_author_stops_at_a_pipe() = assertEquals(BookGuess("Emma", "Jane Austen"), guessBook("Emma by Jane Austen | Unabridged"))

    @Test
    fun audiobook_playlist_is_one_book_in_order() {
        val vids = listOf(Video("u1", "Part 1", "c", 60, null), Video("u2", "Part 2", "c", 60, null))
        val jobs = bookJobs(vids, "Dune", "Frank Herbert", "", "Audiobooks/", 10)
        assertEquals(listOf("01 Part 1.m4a", "02 Part 2.m4a"), jobs.map { it.fileName })
        assertEquals(listOf("1", "2"), jobs.map { it.properties["TRACKNUMBER"] })
        assertEquals(setOf("Dune"), jobs.map { it.properties["ALBUM"] }.toSet())
        assertEquals(listOf(10L, 11L), jobs.map { it.id })
    }

    @Test
    fun single_video_book_is_one_file() =
        assertEquals("Dune.m4a", bookJobs(listOf(Video("u", "Dune", "c", 60, null)), "Dune", "", "", "A/", 1).single().fileName)

    @Test
    fun music_jobs_skip_unticked() = assertEquals(
        listOf("A - B.m4a"),
        musicJobs(listOf(PickedVideo(Video("u1", "x", "c", 1, null), "A", "B", true), PickedVideo(Video("u2", "y", "c", 1, null), "C", "D", false)), "Music/", 1).map { it.fileName },
    )

    @Test
    fun album_guess_drops_the_album_prefix_and_takes_the_usual_artist() {
        assertEquals(AlbumGuess("Currents", "Tame Impala"), guessAlbum("Album - Currents", listOf("Tame Impala", "Tame Impala; Rihanna", "Tame Impala", "Other")))
        assertEquals(AlbumGuess("Road trip", ""), guessAlbum("Road trip", emptyList()))
    }

    @Test
    fun album_playlist_is_one_album_in_ticked_order_with_one_cover() {
        val picks = listOf(
            PickedVideo(Video("u1", "v1", "c", 60, "thumb1"), "A", "One", true),
            PickedVideo(Video("u2", "v2", "c", 60, "thumb2"), "A", "Skipped", false),
            PickedVideo(Video("u3", "v3", "c", 60, "thumb3"), "A; B", "Two", true),
        )
        val jobs = albumJobs(picks, "Currents", "Tame Impala", "Music/", 5)
        assertEquals(listOf(5L, 6L), jobs.map { it.id })
        assertEquals(listOf("One", "Two"), jobs.map { it.properties["TITLE"] })
        assertEquals(listOf("1", "2"), jobs.map { it.properties["TRACKNUMBER"] })
        assertEquals(setOf("Currents"), jobs.map { it.properties["ALBUM"] }.toSet())
        assertEquals(setOf("Tame Impala"), jobs.map { it.properties["ALBUMARTIST"] }.toSet())
        assertEquals(setOf("thumb1"), jobs.map { it.thumbnail }.toSet())
    }
}
