package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.data.storage.entities.search.MediaIndexingEntity

@Dao
interface MediaIndexingDao {
    @Query("SELECT * FROM media_indexing")
    fun observeAll(): Flow<List<MediaIndexingEntity>>

    @Query("SELECT * FROM media_indexing WHERE memeId = :memeId")
    suspend fun getByMemeId(memeId: Long): MediaIndexingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaIndexingEntity)

    @Query("SELECT memeId FROM media_indexing WHERE status = :status ORDER BY updatedAtMillis ASC")
    suspend fun getMemeIdsByStatus(status: String): List<Long>

    @Query("SELECT * FROM media_indexing WHERE status = :status ORDER BY updatedAtMillis ASC")
    suspend fun getByStatus(status: String): List<MediaIndexingEntity>

    @Query(
        """
        UPDATE media_indexing
        SET status = :pendingStatus,
            lastError = :reason,
            processingStartedAtMillis = NULL,
            updatedAtMillis = :updatedAtMillis
        WHERE status = :runningStatus
        """
    )
    suspend fun moveRunningToPending(
        runningStatus: String,
        pendingStatus: String,
        reason: String,
        updatedAtMillis: Long
    )

    @Query(
        """
        UPDATE media_indexing
        SET status = :pendingStatus,
            lastError = :reason,
            runId = NULL,
            processingStartedAtMillis = NULL,
            updatedAtMillis = :updatedAtMillis
        WHERE runId IN (
            SELECT id FROM media_indexing_runs WHERE finishedAtMillis IS NULL
        )
        """
    )
    suspend fun cancelActiveRuns(
        pendingStatus: String,
        reason: String,
        updatedAtMillis: Long
    )

    @Query("DELETE FROM media_indexing WHERE memeId = :memeId")
    suspend fun deleteByMemeId(memeId: Long)
}
