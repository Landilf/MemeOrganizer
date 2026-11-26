package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.landilf.memeorganizer.data.storage.entities.search.TextEmbeddingEntity

data class SearchDocumentRow(
    val memeId: Long,
    val description: String,
    val tags: String
)

@Dao
interface TextEmbeddingDao {
    @Query(
        """
        SELECT memes.id AS memeId, memes.description AS description,
               COALESCE(GROUP_CONCAT(tags.name, ' '), '') AS tags
        FROM memes
        LEFT JOIN meme_tags ON memes.id = meme_tags.memeId
        LEFT JOIN tags ON tags.id = meme_tags.tagId
        GROUP BY memes.id
        ORDER BY memes.id
        """
    )
    suspend fun searchDocuments(): List<SearchDocumentRow>

    @Query("SELECT * FROM text_embeddings WHERE memeId = :memeId")
    suspend fun getByMemeId(memeId: Long): TextEmbeddingEntity?

    @Query("SELECT * FROM text_embeddings WHERE modelSha256 = :modelSha256")
    suspend fun getByModel(modelSha256: String): List<TextEmbeddingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TextEmbeddingEntity)

    @Query("DELETE FROM text_embeddings WHERE memeId = :memeId")
    suspend fun deleteByMemeId(memeId: Long)
}
