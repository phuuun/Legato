package com.phuuun.legato.library.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long)

/** Tracks are referenced by MediaStore id; positions run 0..n-1 within a playlist. */
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "position"])
data class PlaylistTrack(val playlistId: Long, val position: Int, val mediaStoreId: Long)

@Entity(tableName = "play_stats")
data class PlayStat(@PrimaryKey val mediaStoreId: Long, val playCount: Int, val lastPlayedAt: Long)

@Dao
abstract class PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    abstract fun playlists(): Flow<List<Playlist>>

    @Query("SELECT mediaStoreId FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    abstract fun trackIds(id: Long): Flow<List<Long>>

    @Query("SELECT mediaStoreId FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    protected abstract suspend fun trackIdsNow(id: Long): List<Long>

    @Insert
    abstract suspend fun insert(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    protected abstract suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    protected abstract suspend fun clear(id: Long)

    @Insert
    protected abstract suspend fun insertTracks(rows: List<PlaylistTrack>)

    // ponytail: every edit rewrites the playlist's rows, O(n) per change; fine for hand-made playlists, use position updates if one grows huge.
    /** Applies [change] to the ordered track ids and stores the result with contiguous positions, atomically. */
    @Transaction
    open suspend fun edit(id: Long, change: (MutableList<Long>) -> Unit) {
        val ids = trackIdsNow(id).toMutableList()
        change(ids)
        clear(id)
        insertTracks(ids.mapIndexed { i, t -> PlaylistTrack(id, i, t) })
    }

    @Transaction
    open suspend fun delete(id: Long) {
        clear(id)
        deletePlaylist(id)
    }
}

@Dao
abstract class StatsDao {
    // Two statements instead of an upsert: SQLite on API 24-29 predates ON CONFLICT DO UPDATE.
    @Query("INSERT OR IGNORE INTO play_stats VALUES (:id, 0, :now)")
    protected abstract suspend fun ensure(id: Long, now: Long)

    @Query("UPDATE play_stats SET playCount = playCount + 1, lastPlayedAt = :now WHERE mediaStoreId = :id")
    protected abstract suspend fun bump(id: Long, now: Long)

    @Transaction
    open suspend fun recordPlay(id: Long, now: Long) {
        ensure(id, now)
        bump(id, now)
    }

    @Query("SELECT * FROM play_stats WHERE mediaStoreId = :id")
    abstract suspend fun get(id: Long): PlayStat?

    @Query("SELECT * FROM play_stats")
    abstract suspend fun all(): List<PlayStat>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(stats: List<PlayStat>)

    @Query("SELECT * FROM play_stats ORDER BY playCount DESC, lastPlayedAt DESC")
    abstract fun byPlays(): Flow<List<PlayStat>>

    @Query("SELECT * FROM play_stats ORDER BY lastPlayedAt DESC")
    abstract fun byRecent(): Flow<List<PlayStat>>
}

@Database(entities = [Playlist::class, PlaylistTrack::class, PlayStat::class], version = 1, exportSchema = false)
abstract class LegatoDb : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun stats(): StatsDao

    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, LegatoDb::class.java, "legato.db").build()
    }
}
