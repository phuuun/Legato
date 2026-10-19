package com.phuuun.legato.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueOpsTest {
    @Test
    fun play_next_goes_after_current() = assertEquals(3, playNextIndex(currentIndex = 2, queueSize = 5))

    @Test
    fun play_next_on_empty_queue_is_zero() = assertEquals(0, playNextIndex(currentIndex = 0, queueSize = 0))

    @Test
    fun play_next_with_no_current_item_is_zero() = assertEquals(0, playNextIndex(currentIndex = -1, queueSize = 3))

    @Test
    fun reorder_noop_when_same_index() = assertNull(reorderTarget(from = 2, to = 2, queueSize = 5))

    @Test
    fun reorder_out_of_range_is_null() {
        assertNull(reorderTarget(from = 7, to = 1, queueSize = 5))
        assertNull(reorderTarget(from = -1, to = 1, queueSize = 5))
    }

    @Test
    fun reorder_clamps_target_to_last() {
        assertEquals(4, reorderTarget(from = 1, to = 9, queueSize = 5))
        assertEquals(0, reorderTarget(from = 3, to = -2, queueSize = 5))
        // Dragging the last item further down clamps back onto itself: nothing to move.
        assertNull(reorderTarget(from = 4, to = 9, queueSize = 5))
    }
}
