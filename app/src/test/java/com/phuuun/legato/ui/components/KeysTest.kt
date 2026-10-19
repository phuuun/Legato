package com.phuuun.legato.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class KeysTest {
    @Test
    fun repeated_items_get_distinct_keys() = assertEquals(listOf("7#0", "8#0", "7#1"), occurrenceKeys(listOf(7, 8, 7)) { it })

    @Test
    fun keys_follow_the_item_when_it_moves() {
        val before = occurrenceKeys(listOf(1, 2, 3)) { it }
        val after = occurrenceKeys(listOf(3, 1, 2)) { it }
        assertEquals(before.toSet(), after.toSet())
        assertEquals("3#0", after.first())
    }
}
