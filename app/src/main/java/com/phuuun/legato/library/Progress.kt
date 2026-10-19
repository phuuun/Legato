package com.phuuun.legato.library

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil

/** Where listening stopped in one audiobook file, and when. */
data class Spot(val positionMs: Long, val updatedAt: Long)

/** Saved spots per audiobook file, keyed by MediaStore id. Written by the playback service, read by the Audiobooks tab. */
class ProgressStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    private fun read(): Map<Long, Spot> = prefs.all.mapNotNull { (key, value) ->
        val parts = (value as? String)?.split(':') ?: return@mapNotNull null
        val id = key.toLongOrNull() ?: return@mapNotNull null
        Spot(parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null, parts.getOrNull(1)?.toLongOrNull() ?: 0).let { id to it }
    }.toMap()

    fun spots(): Flow<Map<Long, Spot>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun save(trackId: Long, positionMs: Long, now: Long) = prefs.edit { putString(trackId.toString(), "$positionMs:$now") }
}

private fun finished(chapter: Track, spot: Spot) = chapter.durationMs > 0 && spot.positionMs >= chapter.durationMs - 1_000

private fun latest(chapters: List<Track>, spots: Map<Long, Spot>): Int? =
    chapters.indices.filter { spots[chapters[it].id] != null }.maxByOrNull { spots.getValue(chapters[it].id).updatedAt }

/** Where one chapter picks up: its spot less a few seconds so no word is lost, or the top if it was heard to the end. */
fun chapterStart(chapter: Track, spot: Spot?, rewindMs: Long = 5_000): Long =
    if (spot == null || finished(chapter, spot)) 0 else (spot.positionMs - rewindMs).coerceAtLeast(0)

/**
 * Where tapping a book starts: the chapter heard most recently, at its spot less [rewindMs].
 * A chapter heard to its end moves on to the next; a book heard to its end starts over.
 */
fun resumePoint(chapters: List<Track>, spots: Map<Long, Spot>, rewindMs: Long = 5_000): Pair<Int, Long> {
    val i = latest(chapters, spots) ?: return 0 to 0L
    val spot = spots.getValue(chapters[i].id)
    return when {
        !finished(chapters[i], spot) -> i to chapterStart(chapters[i], spot, rewindMs)
        i < chapters.lastIndex -> i + 1 to 0L
        else -> 0 to 0L
    }
}

/** Listened and total milliseconds: every chapter before the latest counts as heard. */
fun bookProgress(chapters: List<Track>, spots: Map<Long, Spot>): Pair<Long, Long> {
    val total = chapters.sumOf { it.durationMs }
    val i = latest(chapters, spots) ?: return 0L to total
    return chapters.take(i).sumOf { it.durationMs } + spots.getValue(chapters[i].id).positionMs.coerceAtMost(chapters[i].durationMs) to total
}

/** "7h 42m left"; a started minute counts as a whole one. */
fun formatLeft(ms: Long): String {
    val minutes = ceil(ms / 60_000.0).toLong().coerceAtLeast(1)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m left" else "${minutes}m left"
}
