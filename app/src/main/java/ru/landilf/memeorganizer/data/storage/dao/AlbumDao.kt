package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumEntity

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAtMillis DESC, id DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: AlbumEntity): Long
}
