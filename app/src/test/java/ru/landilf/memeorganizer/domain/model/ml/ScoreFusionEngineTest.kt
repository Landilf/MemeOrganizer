package ru.landilf.memeorganizer.domain.model.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScoreFusionEngineTest {
    @Test
    fun `input accepts four scores and mask values per document`() {
        val input = FusionInput(
            queryEmbedding = FloatArray(384),
            channelScores = listOf(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)),
            channelMask = listOf(booleanArrayOf(true, true, false, false))
        )

        assertEquals(1, input.documentCount)
    }

    @Test
    fun `input rejects score rows with an unexpected channel count`() {
        assertThrows(IllegalArgumentException::class.java) {
            FusionInput(
                queryEmbedding = FloatArray(384),
                channelScores = listOf(floatArrayOf(0.1f, 0.2f, 0.3f)),
                channelMask = listOf(booleanArrayOf(true, true, true, false))
            )
        }
    }
}
