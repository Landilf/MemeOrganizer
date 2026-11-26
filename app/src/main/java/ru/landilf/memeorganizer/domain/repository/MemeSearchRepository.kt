package ru.landilf.memeorganizer.domain.repository

import ru.landilf.memeorganizer.domain.model.search.MemeSearchResult
import ru.landilf.memeorganizer.domain.model.search.SearchPreparation

interface MemeSearchRepository {
    /** Indexes descriptions and tags whose content or model version changed. */
    suspend fun prepare(): SearchPreparation
    suspend fun indexMeme(memeId: Long)
    suspend fun search(query: String): List<MemeSearchResult>
}
