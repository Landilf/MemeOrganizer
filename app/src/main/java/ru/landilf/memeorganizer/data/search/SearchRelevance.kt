package ru.landilf.memeorganizer.data.search

import kotlin.math.ln
import ru.landilf.memeorganizer.domain.model.meme.MemeTextRules

internal data class SearchRelevanceDecision(
    val corpusSize: Int,
    val bm25Threshold: Float,
    val exactTagMatch: Boolean,
    val passedByDense: Boolean,
    val passedByBm25: Boolean,
    val passedByExactTag: Boolean
) {
    val passed: Boolean
        get() = passedByDense || passedByBm25 || passedByExactTag
}

internal object SearchRelevance {
    const val DENSE_THRESHOLD = 0.25f
    const val MAX_BM25_THRESHOLD = 3.5f
    private const val MIN_CORPUS_SIZE = 3
    private const val BM25_SCALE = 0.65
    private const val BM25_NUMERATOR_OFFSET = 0.5
    private const val BM25_DENOMINATOR = 1.5

    fun bm25Threshold(corpusSize: Int): Float = minOf(
        MAX_BM25_THRESHOLD,
        (
            BM25_SCALE * ln(
                (maxOf(corpusSize, MIN_CORPUS_SIZE) - BM25_NUMERATOR_OFFSET) /
                    BM25_DENOMINATOR + 1.0
            )
        ).toFloat()
    )

    fun evaluate(
        query: String,
        tags: String,
        corpusSize: Int,
        denseScore: Float,
        bm25Score: Float
    ): SearchRelevanceDecision {
        val bm25Threshold = bm25Threshold(corpusSize)
        val exactTagMatch = hasExactTagMatch(query, tags)
        return SearchRelevanceDecision(
            corpusSize = corpusSize,
            bm25Threshold = bm25Threshold,
            exactTagMatch = exactTagMatch,
            passedByDense = denseScore >= DENSE_THRESHOLD,
            passedByBm25 = bm25Score >= bm25Threshold,
            passedByExactTag = exactTagMatch
        )
    }

    private fun hasExactTagMatch(query: String, tags: String): Boolean {
        val normalizedQuery = MemeTextRules.normalizeTag(query)
        if (normalizedQuery.isEmpty()) return false
        val queryTokens = SimpleBm25.tokenize(normalizedQuery).toSet()
        return tags.split(TAG_SEPARATOR)
            .map(MemeTextRules::normalizeTag)
            .any { normalizedTag ->
                val singleTagToken = SimpleBm25.tokenize(normalizedTag).singleOrNull()
                normalizedTag == normalizedQuery ||
                    (singleTagToken != null && singleTagToken in queryTokens)
            }
    }

    private const val TAG_SEPARATOR = '\u001f'
}
