package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.data.storage.entities.search.MediaIndexingRunEntity

@Dao
interface MediaIndexingRunDao {
    @Insert
    suspend fun insert(run: MediaIndexingRunEntity): Long

    @Query(
        "SELECT * FROM media_indexing_runs ORDER BY startedAtMillis DESC, id DESC LIMIT :limit"
    )
    fun observeRecent(limit: Int): Flow<List<MediaIndexingRunEntity>>

    @Query("SELECT * FROM media_indexing_runs WHERE id = :runId")
    suspend fun getById(runId: Long): MediaIndexingRunEntity?

    @Query(
        """
        UPDATE media_indexing_runs
        SET completedCount = completedCount + :completedIncrement,
            failedCount = failedCount + :failedIncrement,
            totalDurationMillis = totalDurationMillis + :durationMillis,
            finishedAtMillis = CASE
                WHEN finishedAtMillis IS NULL
                    AND completedCount + failedCount + :completedIncrement + :failedIncrement >= totalCount
                THEN :finishedAtMillis
                ELSE finishedAtMillis
            END
        WHERE id = :runId
        """
    )
    suspend fun recordTerminalResult(
        runId: Long,
        completedIncrement: Int,
        failedIncrement: Int,
        durationMillis: Long,
        finishedAtMillis: Long
    )

    @Query(
        "UPDATE media_indexing_runs SET pausedAtMillis = :pausedAtMillis WHERE finishedAtMillis IS NULL AND pausedAtMillis IS NULL"
    )
    suspend fun pauseActive(pausedAtMillis: Long)

    @Query(
        """
        UPDATE media_indexing_runs
        SET totalPausedDurationMillis = totalPausedDurationMillis + (:resumedAtMillis - pausedAtMillis),
            pausedAtMillis = NULL
        WHERE finishedAtMillis IS NULL AND pausedAtMillis IS NOT NULL
        """
    )
    suspend fun resumeActive(resumedAtMillis: Long)

    @Query(
        """
        UPDATE media_indexing_runs
        SET totalPausedDurationMillis = totalPausedDurationMillis + CASE
                WHEN pausedAtMillis IS NULL THEN 0
                ELSE :cancelledAtMillis - pausedAtMillis
            END,
            pausedAtMillis = NULL,
            finishedAtMillis = :cancelledAtMillis,
            cancelledAtMillis = :cancelledAtMillis
        WHERE finishedAtMillis IS NULL
        """
    )
    suspend fun cancelActive(cancelledAtMillis: Long)

    @Query(
        """
        DELETE FROM media_indexing_runs
        WHERE finishedAtMillis IS NOT NULL
            AND id NOT IN (
                SELECT id FROM media_indexing_runs
                ORDER BY startedAtMillis DESC, id DESC
                LIMIT :limit
            )
        """
    )
    suspend fun pruneFinished(limit: Int)
}
