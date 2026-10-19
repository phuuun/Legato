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
}
