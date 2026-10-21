package com.phuuun.legato.library

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.edit
import com.phuuun.legato.library.db.LegatoDb
import com.phuuun.legato.library.db.PlayStat
import com.phuuun.legato.library.db.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedPlaylist(val name: String, val createdAt: Long, val paths: List<String>)
data class SavedBookmark(val path: String, val positionMs: Long, val color: Int, val caption: String)
data class SavedSpot(val path: String, val positionMs: Long, val updatedAt: Long)
data class SavedPlays(val path: String, val count: Int, val lastPlayedAt: Long)

/** Everything Legato keeps that the music files don't, by file path: MediaStore ids change on a new phone or a reindex. */
data class BackupData(
    val createdAt: Long,
    val playlists: List<SavedPlaylist>,
    val bookmarks: List<SavedBookmark>,
    val spots: List<SavedSpot>,
    val plays: List<SavedPlays>,
    val hidden: Set<String>,
    val musicFolders: Set<String>,
    val audiobookFolders: Set<String>,
    val sort: String,
)

fun BackupData.toJson(): String = JSONObject()
    .put("version", 1).put("createdAt", createdAt)
    .put("playlists", JSONArray(playlists.map { JSONObject().put("name", it.name).put("createdAt", it.createdAt).put("paths", JSONArray(it.paths)) }))
    .put("bookmarks", JSONArray(bookmarks.map { JSONObject().put("path", it.path).put("positionMs", it.positionMs).put("color", it.color).put("caption", it.caption) }))
    .put("spots", JSONArray(spots.map { JSONObject().put("path", it.path).put("positionMs", it.positionMs).put("updatedAt", it.updatedAt) }))
    .put("plays", JSONArray(plays.map { JSONObject().put("path", it.path).put("count", it.count).put("lastPlayedAt", it.lastPlayedAt) }))
    .put("hidden", JSONArray(hidden.sorted())).put("musicFolders", JSONArray(musicFolders.sorted())).put("audiobookFolders", JSONArray(audiobookFolders.sorted()))
    .put("sort", sort)
    .toString(2)

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private fun JSONArray.strings() = List(length()) { getString(it) }

/** Throws on a file that isn't a Legato backup, so a wrong pick never half-restores. */
fun backupFromJson(text: String): BackupData {
    val o = JSONObject(text)
    require(o.optInt("version") == 1) { "Not a Legato backup" }
    return BackupData(
        o.getLong("createdAt"),
        o.getJSONArray("playlists").objects().map { SavedPlaylist(it.getString("name"), it.getLong("createdAt"), it.getJSONArray("paths").strings()) },
        o.getJSONArray("bookmarks").objects().map { SavedBookmark(it.getString("path"), it.getLong("positionMs"), it.getInt("color"), it.getString("caption")) },
        o.getJSONArray("spots").objects().map { SavedSpot(it.getString("path"), it.getLong("positionMs"), it.getLong("updatedAt")) },
        o.getJSONArray("plays").objects().map { SavedPlays(it.getString("path"), it.getInt("count"), it.getLong("lastPlayedAt")) },
        o.getJSONArray("hidden").strings().toSet(), o.getJSONArray("musicFolders").strings().toSet(), o.getJSONArray("audiobookFolders").strings().toSet(),
        o.getString("sort"),
    )
}

/**
 * One backup file in Downloads, outside the app, so it outlives an uninstall. Written once a day, but never over a
 * backup this install didn't write or restore: a fresh install would otherwise replace a full backup with an empty one.
 */
class Backups(
    private val context: Context,
    private val db: LegatoDb,
    private val folders: FolderStore,
    private val progress: ProgressStore,
    private val bookmarks: BookmarkStore,
    val file: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Legato backup.json"),
    /** Every audio file MediaStore knows, id → path. */
    private val audioPaths: suspend () -> Map<Long, String> = { mediaStorePaths(context) },
) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    /**
     * The backup before the current one. A restore made before every song is on the phone drops the missing ones,
     * and the next daily backup would otherwise drop them from the only copy.
     */
    val previous: File get() = File(file.parentFile, "${file.nameWithoutExtension} (previous).json")

    /** When this install last wrote or restored the backup; 0 means never. */
    val last: Long get() = prefs.getLong("last", 0)

    /** A backup from before this install, waiting to be restored (and holding automatic backups off until then). */
    val fromBefore: Boolean get() = last == 0L && file.exists()

    /** Ask about [fromBefore] once per install; Settings still offers it after "Not now". */
    val offerRestore: Boolean get() = fromBefore && !prefs.getBoolean("declined", false)

    fun decline() = prefs.edit { putBoolean("declined", true) }

    suspend fun backUp() = withContext(Dispatchers.IO) {
        val text = collect().toJson()
        // Written beside the old one, then swapped in, so a crash mid-write never leaves half a backup.
        val temp = File(file.parentFile, ".${file.name}.tmp")
        temp.writeText(text)
        if (file.exists()) file.renameTo(previous)
        if (!temp.renameTo(file)) { temp.delete(); error("Couldn't save the backup") }
        prefs.edit { putLong("last", System.currentTimeMillis()) }
    }

    suspend fun daily() {
        if (fromBefore || System.currentTimeMillis() - last < 24 * 3_600_000L) return
        runCatching { backUp() }
    }

    suspend fun collect(): BackupData {
        val paths = audioPaths()
        val dao = db.playlists()
        return BackupData(
            System.currentTimeMillis(),
            dao.playlists().first().map { p -> SavedPlaylist(p.name, p.createdAt, dao.trackIds(p.id).first().mapNotNull { paths[it] }) },
            bookmarks.bookmarks.first().mapNotNull { b -> paths[b.trackId]?.let { SavedBookmark(it, b.positionMs, b.color, b.caption) } },
            progress.spots().first().mapNotNull { (id, s) -> paths[id]?.let { SavedSpot(it, s.positionMs, s.updatedAt) } },
            db.stats().all().mapNotNull { s -> paths[s.mediaStoreId]?.let { SavedPlays(it, s.playCount, s.lastPlayedAt) } },
            folders.hidden.first(), folders.folders(FolderKind.Music).first(), folders.folders(FolderKind.Audiobooks).first(),
            folders.sort.first().name,
        )
    }

    /**
     * Replaces playlists, bookmarks, folders and hidden files with the backup's; adds its listening spots and play counts.
     * Returns how many of its playlists' songs aren't on this phone (yet): those are left out.
     */
    suspend fun restore(data: BackupData): Int = withContext(Dispatchers.IO) {
        val ids = audioPaths().entries.associate { (id, path) -> path to id }
        val dao = db.playlists()
        dao.playlists().first().forEach { dao.delete(it.id) }
        for (p in data.playlists) {
            val id = dao.insert(Playlist(name = p.name, createdAt = p.createdAt))
            dao.edit(id) { it += p.paths.mapNotNull(ids::get) }
        }
        bookmarks.clear()
        data.bookmarks.forEachIndexed { i, b -> ids[b.path]?.let { bookmarks.save(Bookmark(data.createdAt + i, it, b.positionMs, b.color, b.caption)) } }
        data.spots.forEach { s -> ids[s.path]?.let { progress.save(it, s.positionMs, s.updatedAt) } }
        db.stats().put(data.plays.mapNotNull { s -> ids[s.path]?.let { PlayStat(it, s.count, s.lastPlayedAt) } })
        folders.setHidden(data.hidden)
        folders.setFolders(FolderKind.Music, data.musicFolders)
        folders.setFolders(FolderKind.Audiobooks, data.audiobookFolders)
        Sort.entries.firstOrNull { it.name == data.sort }?.let(folders::setSort)
        prefs.edit { putLong("last", System.currentTimeMillis()) }
        data.playlists.flatMap { it.paths }.distinct().count { it !in ids }
    }
}

private suspend fun mediaStorePaths(context: Context): Map<Long, String> = withContext(Dispatchers.IO) {
    buildMap {
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(1)?.let { put(c.getLong(0), it) }
        }
    }
}
