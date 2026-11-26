package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.data.storage.entities.meme.TagEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeTagEntity

data class TagUsage(val id: Long, val name: String, val normalizedName: String, val usageCount: Int)

@Dao
interface TagDao {
    @Query("SELECT tags.id, tags.name, tags.normalizedName, COUNT(meme_tags.memeId) AS usageCount FROM tags LEFT JOIN meme_tags ON tags.id = meme_tags.tagId GROUP BY tags.id")
    fun observeTags(): Flow<List<TagUsage>>

    @Query("SELECT tags.name FROM tags INNER JOIN meme_tags ON tags.id = meme_tags.tagId WHERE meme_tags.memeId = :memeId ORDER BY tags.name")
    suspend fun getMemeTags(memeId: Long): List<String>

    @Query("SELECT * FROM tags WHERE normalizedName = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: MemeTagEntity)
    @Query("DELETE FROM meme_tags WHERE memeId = :memeId")
    suspend fun clearLinks(memeId: Long)
}
