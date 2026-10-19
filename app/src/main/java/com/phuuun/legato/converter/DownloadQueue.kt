package com.phuuun.legato.converter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

sealed interface JobState {
    data object Queued : JobState
    data class Downloading(val percent: Int) : JobState
    data object Tagging : JobState
    data object Done : JobState
    data class Failed(val reason: String) : JobState
}

/** One file to make: which video, where it goes, what it's called, and the tags it gets. */
data class DownloadJob(
    val id: Long,
    val videoUrl: String,
    val label: String,
    val folder: String,
    val fileName: String,
    val properties: Map<String, String>,
    val thumbnail: String?,
    val state: JobState = JobState.Queued,
)

/** App-wide list of downloads: the Converter screen adds and shows them, DownloadService works through them in order. */
class DownloadQueue {
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs

    /** First id for a batch of new jobs. */
    fun nextId(): Long = (_jobs.value.maxOfOrNull { it.id } ?: 0) + 1

    fun add(jobs: List<DownloadJob>) = _jobs.update { it + jobs }

    fun next(): DownloadJob? = _jobs.value.firstOrNull { it.state == JobState.Queued }

    fun set(id: Long, state: JobState) = _jobs.update { list -> list.map { if (it.id == id) it.copy(state = state) else it } }

    fun retry(id: Long) = _jobs.update { list -> list.map { if (it.id == id && it.state is JobState.Failed) it.copy(state = JobState.Queued) else it } }

    fun clearFinished() = _jobs.update { list -> list.filter { it.state != JobState.Done } }
}
