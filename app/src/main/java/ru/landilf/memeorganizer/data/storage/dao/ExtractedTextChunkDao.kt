package ru.landilf.memeorganizer.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.landilf.memeorganizer.data.storage.entities.search.ExtractedTextChunkEntity

@Dao
interface ExtractedTextChunkDao {
    @Query(
        """
        SELECT * FROM extracted_text_chunks
        WHERE memeId = :memeId
        ORDER BY source, timestampMs ASC, id ASC
        """
    )
    suspend fun getForMeme(memeId: Long): List<ExtractedTextChunkEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(chunks: List<ExtractedTextChunkEntity>)

    @Query("DELETE FROM extracted_text_chunks WHERE memeId = :memeId AND source = :source")
    suspend fun deleteForMemeAndSource(memeId: Long, source: String)

    @Query("DELETE FROM extracted_text_chunks")
    suspend fun deleteAll()
}
