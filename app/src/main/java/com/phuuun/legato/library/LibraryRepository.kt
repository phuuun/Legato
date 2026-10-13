package com.phuuun.legato.library

import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    val tracks: Flow<List<Track>>
    suspend fun track(id: Long): Track?
}
