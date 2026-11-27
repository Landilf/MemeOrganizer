package ru.landilf.memeorganizer.domain.repository

interface MediaIndexingRepository {
    /** Schedules or replaces background processing for one meme. */
    suspend fun enqueue(memeId: Long)

    /** Re-enqueues items that were waiting for a model package to become available. */
    suspend fun resumePending()
}
