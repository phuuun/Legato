package com.phuuun.legato.library

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * One scope of the library: the tracks in [folders], or what [whenEmpty] says before any folder is chosen, less the [hidden]
 * files, with Legato's own [edits] over their tags.
 */
class MediaStoreLibrary(
    context: Context,
    folders: Flow<Set<String>>,
    private val whenEmpty: WhenEmpty,
    hidden: Flow<Set<String>> = flowOf(emptySet()),
    edits: Flow<Edits> = flowOf(emptyMap()),
) : LibraryRepository {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    /** Re-queries whenever the folders or edits change, or MediaStore does (new, deleted or retagged files). */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val tracks: Flow<List<Track>> = combine(folders, edits, ::Pair).flatMapLatest { (f, e) ->
        callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { trySend(query(f, e)) }
            }
            resolver.registerContentObserver(collection, true, observer)
            trySend(query(f, e))
            awaitClose { resolver.unregisterContentObserver(observer) }
        }
    }.combine(hidden) { tracks, h -> if (h.isEmpty()) tracks else tracks.filterNot { it.path in h } }.flowOn(Dispatchers.IO)

    override suspend fun track(id: Long): Track? = withContext(Dispatchers.IO) { tracks.first().firstOrNull { it.id == id } }

    private fun query(folders: Set<String>, edits: Edits): List<Track> {
        val (selection, args) = audioSelection(folders, whenEmpty) ?: return emptyList()
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.COMPOSER, MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ARTIST,
        )
        val out = mutableListOf<Track>()
        resolver.query(
            collection, cols, selection, args, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(8) ?: ""
                out += trackFromRow(
                    id = c.getLong(0), title = c.getString(1), artist = c.getString(2), album = c.getString(3),
                    albumId = c.getLong(4), durationMs = c.getLong(5), displayName = c.getString(6) ?: "",
                    trackNumber = c.getInt(7), path = path, relativePath = c.getString(9) ?: "",
                    composer = c.getString(10), modified = c.getLong(11), added = c.getLong(12), albumArtist = c.getString(13),
                    edit = edits[path].orEmpty(),
                )
            }
        }
        return out
    }
}
