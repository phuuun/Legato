package com.phuuun.legato.library

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.phuuun.legato.library.edit.CoverChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Tag changes per file path, as TagLib keys ("TITLE", "ARTIST", …); [COVER] holds a cover file's path, or "" for no cover. */
typealias Edits = Map<String, Map<String, String>>

const val COVER = "COVER"

/** A book's chapter list, as timestamp lines ("1:02:03 Title"): from the converter, the chapter editor or Chapterfy. */
const val CHAPTERS = "CHAPTERS"

/**
 * What Edit, Bulk fix, album reorder, the chapter editor and the converter set. Kept in Legato only: files are never
 * written, so an edit can't damage one (tag writes once garbled an audiobook). Other apps keep showing the files' own tags.
 */
class EditStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("edits", Context.MODE_PRIVATE)
    private val covers = File(context.filesDir, "covers")

    fun all(): Edits = prefs.all.mapNotNull { (path, value) ->
        val o = JSONObject(value as? String ?: return@mapNotNull null)
        path to o.keys().asSequence().associateWith { o.getString(it) }
    }.toMap()

    val edits: Flow<Edits> get() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(all()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(all())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Adds [properties] (a blank value clears that tag) and the cover to what's saved for the file at [path]. */
    suspend fun save(path: String, properties: Map<String, String>, cover: CoverChange = CoverChange.Keep) = withContext(Dispatchers.IO) {
        val old = all()[path].orEmpty()
        val new = old + properties + when (cover) {
            CoverChange.Keep -> emptyMap()
            CoverChange.Remove -> mapOf(COVER to "")
            is CoverChange.Set -> mapOf(COVER to saveCover(cover.jpeg))
        }
        if (new[COVER] != old[COVER]) old[COVER]?.takeIf { it.isNotEmpty() }?.let { File(it).delete() }
        prefs.edit { putString(path, JSONObject(new).toString()) }
    }

    /** Replaces every edit, for a restore; covers come as JPEG bytes. */
    suspend fun replaceAll(edits: Map<String, Map<String, String>>, coverJpegs: Map<String, ByteArray>) = withContext(Dispatchers.IO) {
        covers.listFiles()?.forEach { it.delete() }
        prefs.edit {
            clear()
            for ((path, tags) in edits) putString(path, JSONObject(tags + (coverJpegs[path]?.let { mapOf(COVER to saveCover(it)) } ?: emptyMap())).toString())
        }
    }

    // A new name for every cover, so the image cache never shows the one it replaced.
    private fun saveCover(jpeg: ByteArray): String {
        covers.mkdirs()
        return File(covers, "${UUID.randomUUID()}.jpg").apply { writeBytes(jpeg) }.path
    }
}
