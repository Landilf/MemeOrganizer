package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumEntity
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumMemeEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAtMillis DESC, id DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemeLinks(links: List<AlbumMemeEntity>)

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
