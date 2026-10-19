package com.phuuun.legato.library

import android.content.Context
import android.content.SharedPreferences
import android.provider.MediaStore
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class FolderKind(val key: String, val lastKey: String) {
    Music("music_folders", "last_music_folder"),
    Audiobooks("audiobook_folders", "last_audiobook_folder"),
}

/** What a scope shows before any folder is chosen: music falls back to everything, audiobooks to nothing. */
enum class WhenEmpty { AllMusic, Nothing }

/** Chosen folders, stored as MediaStore relative paths with a trailing slash ("Download/MUSIC/"). */
class FolderStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // A copy: the set SharedPreferences hands out must never be modified or kept.
    private fun read(kind: FolderKind): Set<String> = prefs.getStringSet(kind.key, emptySet())!!.toSet()

    fun folders(kind: FolderKind): Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == kind.key) trySend(read(kind)) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(kind))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun add(kind: FolderKind, path: String) = prefs.edit { putStringSet(kind.key, read(kind) + path) }

    fun remove(kind: FolderKind, path: String) = prefs.edit { putStringSet(kind.key, read(kind) - path) }

    /** The folder the converter last saved this kind into. */
    fun lastFolder(kind: FolderKind): String? = prefs.getString(kind.lastKey, null)

    fun setLastFolder(kind: FolderKind, path: String) = prefs.edit { putString(kind.lastKey, path) }
}

/** "primary:Download/MUSIC" from the folder picker → "Download/MUSIC/". Storage roots and SD cards → null. */
fun relativePathFromTreeDocId(docId: String): String? {
    val volume = docId.substringBefore(':', missingDelimiterValue = "")
    val path = docId.substringAfter(':', missingDelimiterValue = "").trim('/')
    // ponytail: internal storage only; SD cards need the volume name next to the path.
    return if (volume == "primary" && path.isNotEmpty()) "$path/" else null
}

/** Exact folder match, so subfolders never count. Null means the scope shows nothing. */
fun audioSelection(folders: Set<String>, whenEmpty: WhenEmpty): Pair<String, Array<String>>? = when {
    folders.isNotEmpty() ->
        "${MediaStore.Audio.Media.RELATIVE_PATH} IN (${folders.joinToString(",") { "?" }}) AND ${MediaStore.Audio.Media.DURATION} > 0" to folders.sorted().toTypedArray()
    whenEmpty == WhenEmpty.AllMusic -> "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0" to emptyArray()
    else -> null
}
