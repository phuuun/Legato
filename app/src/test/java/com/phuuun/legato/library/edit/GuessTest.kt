package com.phuuun.legato.library.edit

import org.junit.Assert.assertEquals
import org.junit.Test

/** Fixtures are real titles from the phone's library. */
class GuessTest {
    @Test
    fun artist_dash_title_with_trailing_junk() = assertEquals(Guess("Agnes Monica", "Rindu"), guessFromTitle("Agnes Monica - Rindu  Official 4K Remastered"))

    @Test
    fun bracketed_junk_is_dropped() {
        assertEquals(Guess("Bunga Citra Lestari", "Cinta Sejati"), guessFromTitle("Bunga Citra Lestari - Cinta Sejati [ lirik ]"))
        assertEquals(Guess("Chrisye", "Aku Cinta Dia"), guessFromTitle("Chrisye - Aku Cinta Dia (Official Music Video)"))
        assertEquals(Guess("Chrisye", "Andai Aku Bisa"), guessFromTitle("Chrisye - Andai Aku Bisa (Official Lyric Video)"))
    }

    @Test
    fun meaningful_brackets_stay() = assertEquals(Guess("Can't", "Daniel Caesar (Unreleased)"), guessFromTitle("Can't - Daniel Caesar (Unreleased)"))

    @Test
    fun no_separator_leaves_artist_blank() {
        assertEquals(Guess("", "Alarm Clock Loud"), guessFromTitle("Alarm Clock Loud"))
        assertEquals(Guess("", "BIBI 'Bam Yang Gang' Easy"), guessFromTitle("BIBI 'Bam Yang Gang' Easy Lyrics"))
    }

    @Test
    fun en_and_em_dashes_split_too() {
        assertEquals(Guess("A", "B"), guessFromTitle("A – B"))
        assertEquals(Guess("A", "B"), guessFromTitle("A — B"))
    }

    @Test
    fun a_title_never_strips_to_nothing() = assertEquals(Guess("Madonna", "Music"), guessFromTitle("Madonna - Music (Official Video)"))
}
