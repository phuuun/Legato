package com.phuuun.legato.library

import com.phuuun.legato.library.db.LegatoDb
import com.phuuun.legato.library.db.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

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

    suspend fun move(playlistId: Long, from: Int, to: Int) = edit(playlistId) {
        if (from in it.indices) {
            val t = it.removeAt(from)
            it.add(to.coerceIn(0, it.size), t)
        }
    }

    suspend fun recordPlay(trackId: Long, now: Long) = db.stats().recordPlay(trackId, now)

    private suspend fun edit(playlistId: Long, change: (MutableList<Long>) -> Unit) {
        val live = library.tracks.first().mapTo(HashSet()) { it.id }
        dao.edit(playlistId) { ids -> ids.retainAll(live); change(ids) }
    }
}
