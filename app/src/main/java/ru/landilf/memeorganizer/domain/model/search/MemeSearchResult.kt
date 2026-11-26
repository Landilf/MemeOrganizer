package ru.landilf.memeorganizer.domain.model.search

import ru.landilf.memeorganizer.domain.model.meme.Meme

data class MemeSearchResult(
    val meme: Meme,
    val score: Float,
    val textScore: Float,
    val bm25Score: Float
)

data class SearchPreparation(
    val modelAvailable: Boolean,
    val indexedCount: Int
)
