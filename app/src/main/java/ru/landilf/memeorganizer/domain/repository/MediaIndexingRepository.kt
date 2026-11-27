package ru.landilf.memeorganizer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus

interface MediaIndexingRepository {
    /** Schedules or replaces background processing for one meme. */
    suspend fun enqueue(memeId: Long)

    /** Re-enqueues items that were waiting for a model package to become available. */
    suspend fun resumePending()

    /** Observes persistent processing states by meme ID for displaying them in the library. */
    fun observeStatuses(): Flow<Map<Long, MediaIndexingStatus>>
}
