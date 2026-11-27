package ru.landilf.memeorganizer.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRelevanceTest {
    @Test
    fun `scales bm25 threshold with indexed corpus size`() {
        assertEquals(1.0013f, SearchRelevance.bm25Threshold(6), 0.0001f)
        assertEquals(2.2921f, SearchRelevance.bm25Threshold(50), 0.0001f)
        assertEquals(3.4838f, SearchRelevance.bm25Threshold(318), 0.0001f)
        assertEquals(3.5f, SearchRelevance.bm25Threshold(500), 0f)
    }

    @Test
    fun `passes exact full and single word tag matches`() {
        val fullTag = SearchRelevance.evaluate(
            query = "  ТИМ ФОРТРЕС ",
            tags = "смех\u001fтим фортрес",
            corpusSize = 6,
            denseScore = 0f,
            bm25Score = 0f
        )
        val singleWordTag = SearchRelevance.evaluate(
            query = "мем с Нагиев",
            tags = "нагиев\u001fабсурд",
            corpusSize = 6,
            denseScore = 0f,
            bm25Score = 0f
        )

        assertTrue(fullTag.passedByExactTag)
        assertTrue(singleWordTag.passedByExactTag)
    }

    @Test
    fun `does not treat a partial multi word tag as exact`() {
        val decision = SearchRelevance.evaluate(
            query = "тим",
            tags = "тим фортрес",
            corpusSize = 6,
            denseScore = 0f,
            bm25Score = 0f
        )

        assertFalse(decision.exactTagMatch)
        assertFalse(decision.passed)
    }
}
