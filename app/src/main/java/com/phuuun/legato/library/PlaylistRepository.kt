package com.phuuun.legato.library

import com.phuuun.legato.library.db.LegatoDb
import com.phuuun.legato.library.db.PlayStat
import com.phuuun.legato.library.db.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Playlists hold MediaStore ids. A row whose file is gone is skipped on load and pruned on the next write,
 * so positions on screen and positions in the table always agree when an edit lands.
 */
class PlaylistRepository(private val db: LegatoDb, private val library: LibraryRepository) {
    private val dao = db.playlists()

    val playlists: Flow<List<Playlist>> = dao.playlists()

    fun tracksOf(playlistId: Long): Flow<List<Track>> = combine(dao.trackIds(playlistId), library.tracks) { ids, tracks ->
        val byId = tracks.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    suspend fun create(name: String): Long = dao.insert(Playlist(name = name, createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun addTracks(playlistId: Long, trackIds: List<Long>) = edit(playlistId) { it += trackIds }

    suspend fun removeAt(playlistId: Long, position: Int) = edit(playlistId) { if (position in it.indices) it.removeAt(position) }

    /** Undoes [removeAt]. */
    suspend fun insertAt(playlistId: Long, position: Int, trackId: Long) = edit(playlistId) { it.add(position.coerceIn(0, it.size), trackId) }

    suspend fun trackIds(playlistId: Long): List<Long> = dao.trackIds(playlistId).first()

    /** Undoes [delete]: same id and creation time, so it lands back in its old place in the list. */
    suspend fun restore(playlist: Playlist, trackIds: List<Long>) {
        dao.insert(playlist)
        addTracks(playlist.id, trackIds)
    }

    suspend fun move(playlistId: Long, from: Int, to: Int) = edit(playlistId) {
        if (from in it.indices) {
            val t = it.removeAt(from)
            it.add(to.coerceIn(0, it.size), t)
        }
    }

    suspend fun recordPlay(trackId: Long, now: Long) = db.stats().recordPlay(trackId, now)

    /** Ids of tracks played to the end: most often first, or most recent first. */
    /** Every track's play count and last play, by MediaStore id, for the play sorts. */
    val stats: Flow<Map<Long, PlayStat>> = db.stats().byPlays().map { s -> s.associateBy { it.mediaStoreId } }

    fun playedIds(byRecent: Boolean): Flow<List<Long>> =
        (if (byRecent) db.stats().byRecent() else db.stats().byPlays()).map { stats -> stats.map { it.mediaStoreId } }

    private suspend fun edit(playlistId: Long, change: (MutableList<Long>) -> Unit) {
        val live = library.tracks.first().mapTo(HashSet()) { it.id }
        dao.edit(playlistId) { ids -> ids.retainAll(live); change(ids) }
    }
}
