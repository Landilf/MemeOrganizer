package ru.landilf.memeorganizer.domain.repository

import ru.landilf.memeorganizer.domain.model.search.MemeSearchResult
import ru.landilf.memeorganizer.domain.model.search.SearchPreparation
import ru.landilf.memeorganizer.domain.model.ml.AsrIndexingMetrics

interface MemeSearchRepository {
    /** Checks whether the text-search model is available without starting foreground indexing. */
    suspend fun prepare(): SearchPreparation

    /** Rebuilds every text embedding using the currently installed model. */
    suspend fun reindexAll(): SearchPreparation

    suspend fun indexMeme(memeId: Long, asrMetrics: AsrIndexingMetrics? = null)
    suspend fun search(query: String): List<MemeSearchResult>
}
