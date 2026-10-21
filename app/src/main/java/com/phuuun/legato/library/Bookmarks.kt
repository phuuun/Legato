package com.phuuun.legato.library

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** A marked spot in an audiobook file. [color] indexes the bookmark palette; [caption] may be blank. */
data class Bookmark(val id: Long, val trackId: Long, val positionMs: Long, val color: Int, val caption: String)

/**
 * Bookmarks keyed by id, stored as "trackId:positionMs:color:caption" (the caption last, so it may hold colons).
 * Preferences rather than the database: there are few of them, and no schema migration can put playlists at risk.
 */
class BookmarkStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)

    private fun read(): List<Bookmark> = prefs.all.mapNotNull { (key, value) ->
        val parts = (value as? String)?.split(':', limit = 4)?.takeIf { it.size == 4 } ?: return@mapNotNull null
        Bookmark(key.toLongOrNull() ?: return@mapNotNull null, parts[0].toLongOrNull() ?: return@mapNotNull null, parts[1].toLongOrNull() ?: 0, parts[2].toIntOrNull() ?: 0, parts[3])
    }

    val bookmarks: Flow<List<Bookmark>> get() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Adds or replaces. */
    fun save(b: Bookmark) = prefs.edit { putString(b.id.toString(), "${b.trackId}:${b.positionMs}:${b.color}:${b.caption}") }

    fun delete(id: Long) = prefs.edit { remove(id.toString()) }

    fun clear() = prefs.edit { clear() }

    /** A replaced file's bookmarks go to the file replacing it. */
    fun move(old: Long, new: Long) = read().filter { it.trackId == old }.forEach { save(it.copy(trackId = new)) }
}
