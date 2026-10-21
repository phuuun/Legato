package com.phuuun.legato.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    @Test
    fun music_names() {
        assertEquals("Agnes Monica - Rindu.m4a", musicFileName("Agnes Monica", "Rindu"))
        assertEquals("Rindu.m4a", musicFileName(" ", "Rindu"))
    }

    @Test
    fun illegal_characters_become_underscores() = assertEquals("AC_DC - Back_ In_Black.m4a", musicFileName("AC/DC", "Back? In:Black"))

    @Test
    fun chapter_names_are_numbered() = assertEquals("03 The Will.m4a", chapterFileName(3, "The Will"))

    @Test
    fun long_names_are_trimmed_keeping_the_extension() = assertEquals(120 + ".m4a".length, bookFileName("x".repeat(300)).length)

    @Test
    fun a_leading_dot_would_hide_the_file() {
        assertEquals("Ready For It_.m4a", bookFileName("...Ready For It?"))
        assertEquals("Untitled.m4a", bookFileName(". . ."))
    }

    @Test
    fun empty_title_still_gets_a_name() = assertEquals("Untitled.m4a", bookFileName("   "))

    @Test
    fun taken_names_get_a_number() {
        assertEquals("A.m4a", uniqueName("A.m4a", emptySet()))
        assertEquals("A (2).m4a", uniqueName("A.m4a", setOf("A.m4a")))
        assertEquals("A (3).m4a", uniqueName("A.m4a", setOf("A.m4a", "A (2).m4a")))
    }
}
