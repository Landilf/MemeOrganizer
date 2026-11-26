package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.landilf.memeorganizer.data.storage.entities.logging.LightLogEventEntity

@Dao
interface LightLogDao {
    @Insert
    suspend fun insert(event: LightLogEventEntity)

    @Query("SELECT * FROM light_log_events WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun getAfterId(afterId: Long, limit: Int): List<LightLogEventEntity>

    @Query("DELETE FROM light_log_events WHERE id NOT IN (SELECT id FROM light_log_events ORDER BY id DESC LIMIT :maxEvents)")
    suspend fun trimTo(maxEvents: Int): Int

    @Query("DELETE FROM light_log_events")
    suspend fun clear()
}
