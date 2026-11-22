package ru.landilf.memeorganaizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganaizer.data.storage.entities.meme.MemeEntity

@Dao
interface MemeDao {
    @Query("SELECT * FROM memes ORDER BY addedAtMillis DESC, id DESC")
    fun observeMemes(): Flow<List<MemeEntity>>

    @Query("SELECT * FROM memes WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): MemeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(meme: MemeEntity): Long
}
