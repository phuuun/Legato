package com.phuuun.legato.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadQueueTest {
    private fun job(id: Long) = DownloadJob(id, "u$id", "L$id", "Music/", "f$id.m4a", emptyMap(), null)

    @Test
    fun next_is_the_first_queued() {
        val q = DownloadQueue()
        q.add(listOf(job(1), job(2)))
        q.set(1, JobState.Done)
        assertEquals(2L, q.next()?.id)
    }

    @Test
    fun failed_job_retries_as_queued() {
        val q = DownloadQueue()
        q.add(listOf(job(1)))
        q.set(1, JobState.Failed("403"))
        assertNull(q.next())
        q.retry(1)
        assertEquals(JobState.Queued, q.jobs.value.single().state)
    }

    @Test
    fun clear_finished_keeps_active_and_failed() {
        val q = DownloadQueue()
        q.add(listOf(job(1), job(2), job(3)))
        q.set(1, JobState.Done)
        q.set(2, JobState.Failed("x"))
        q.clearFinished()
        assertEquals(listOf(2L, 3L), q.jobs.value.map { it.id })
    }
}
