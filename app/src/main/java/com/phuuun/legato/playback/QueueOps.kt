package com.phuuun.legato.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** Right after the current item; with nothing current (C.INDEX_UNSET) or an empty queue, the front. */
fun playNextIndex(currentIndex: Int, queueSize: Int): Int = (currentIndex + 1).coerceIn(0, queueSize)

/** Where a dragged item lands, or null when the move is out of range or changes nothing. */
fun reorderTarget(from: Int, to: Int, queueSize: Int): Int? {
    if (from !in 0 until queueSize) return null
    return to.coerceIn(0, queueSize - 1).takeIf { it != from }
}

// ponytail: with shuffle on, ExoPlayer's default shuffle order drops "Play next" items at a random spot; a custom ShuffleOrder in the service fixes it if that bites.
fun Player.playNext(items: List<MediaItem>) = addMediaItems(playNextIndex(currentMediaItemIndex, mediaItemCount), items)

fun Player.enqueue(items: List<MediaItem>) = addMediaItems(items)

fun Player.moveItem(from: Int, to: Int) { reorderTarget(from, to, mediaItemCount)?.let { moveMediaItem(from, it) } }

fun Player.removeItem(index: Int) { if (index in 0 until mediaItemCount) removeMediaItem(index) }
