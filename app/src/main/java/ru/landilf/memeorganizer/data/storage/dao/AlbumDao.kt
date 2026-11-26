package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumEntity
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumMemeEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity

data class AlbumMemeSnapshot(val albumId: Long, val memeId: Long)

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAtMillis DESC, id DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY id")
    suspend fun getAll(): List<AlbumEntity>

    @Query("SELECT albumId, memeId FROM album_memes ORDER BY albumId, memeId")
    suspend fun getAllMemeLinks(): List<AlbumMemeSnapshot>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemeLinks(links: List<AlbumMemeEntity>)

    @Query(
        "UPDATE albums SET name = :name, normalizedName = :normalizedName WHERE id = :albumId"
    )
    suspend fun rename(albumId: Long, name: String, normalizedName: String): Int

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun delete(albumId: Long)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()

    @Query("DELETE FROM album_memes WHERE albumId = :albumId AND memeId IN (:memeIds)")
    suspend fun deleteMemeLinks(albumId: Long, memeIds: Set<Long>)

    @Query(
        """
        SELECT memes.*
        FROM memes
        INNER JOIN album_memes ON memes.id = album_memes.memeId
        WHERE album_memes.albumId = :albumId
        ORDER BY memes.addedAtMillis DESC, memes.id DESC
        """
    )
    fun observeMemes(albumId: Long): Flow<List<MemeEntity>>
}
