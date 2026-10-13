package com.phuuun.legato.library

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MediaStoreLibrary(context: Context) : LibraryRepository {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    /** Re-queries whenever MediaStore changes (new, deleted or retagged files). */
    override val tracks: Flow<List<Track>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(query()) }
        }
        resolver.registerContentObserver(collection, true, observer)
        trySend(query())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    override suspend fun track(id: Long): Track? = withContext(Dispatchers.IO) { tracks.first().firstOrNull { it.id == id } }

    private fun query(): List<Track> {
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TRACK,
        )
        val out = mutableListOf<Track>()
        resolver.query(
            collection, cols,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
            null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += trackFromRow(
                    id = c.getLong(0), title = c.getString(1), artist = c.getString(2), album = c.getString(3),
                    albumId = c.getLong(4), durationMs = c.getLong(5), displayName = c.getString(6) ?: "",
                    trackNumber = c.getInt(7),
                )
            }
        }
        return out
    }
}
