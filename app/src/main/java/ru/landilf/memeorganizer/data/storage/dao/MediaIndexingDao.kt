package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.landilf.memeorganizer.data.storage.entities.search.MediaIndexingEntity

@Dao
interface MediaIndexingDao {
    @Query("SELECT * FROM media_indexing WHERE memeId = :memeId")
    suspend fun getByMemeId(memeId: Long): MediaIndexingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaIndexingEntity)

    @Query("SELECT memeId FROM media_indexing WHERE status = :status ORDER BY updatedAtMillis ASC")
    suspend fun getMemeIdsByStatus(status: String): List<Long>

    @Query("DELETE FROM media_indexing WHERE memeId = :memeId")
    suspend fun deleteByMemeId(memeId: Long)
}
