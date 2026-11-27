package ru.landilf.memeorganizer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingRun
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus

interface MediaIndexingRepository {
    /** Schedules or replaces background processing for one meme. */
    suspend fun enqueue(memeId: Long)

    /** Schedules several newly imported or manually selected memes as one visible run. */
    suspend fun enqueueAll(memeIds: List<Long>): Int

    /** Re-enqueues items that were waiting for a model package to become available. */
    suspend fun resumePending()

    /** Schedules only items whose last background attempt failed. */
    suspend fun retryFailed(): Int

    /** Schedules every meme in the library for a new background processing pass. */
    suspend fun reprocessAll(): Int

    /** Observes persistent processing states by meme ID for displaying them in the library. */
    fun observeStatuses(): Flow<Map<Long, MediaIndexingStatus>>

    /** Observes the current and recent background-processing runs. */
    fun observeRuns(): Flow<List<MediaIndexingRun>>
}
