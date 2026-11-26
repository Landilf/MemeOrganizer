package ru.landilf.memeorganizer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import ru.landilf.memeorganizer.domain.model.meme.TagSuggestion

interface MemeDetailsRepository {
    fun observeTags(): Flow<List<TagSuggestion>>
    suspend fun load(memeId: Long): MemeDetails
    suspend fun save(memeId: Long, details: MemeDetails)
    suspend fun setFavorite(memeId: Long, isFavorite: Boolean)
    suspend fun delete(memeIds: Set<Long>)
}
