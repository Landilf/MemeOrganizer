package ru.landilf.memeorganizer.data.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPreprocessorTest {
    @Test
    fun upscalesSmallImageAndAppliesMemoryGuardBeforeRounding() {
        val size = OcrPreprocessor.detectorSize(sourceWidth = 200, sourceHeight = 60)

        assertEquals(2_048, size.width)
        assertEquals(608, size.height)
        assertEquals(10.24f, size.scale, 0.0001f)
    }

    @Test
    fun limitsVeryLargeImageBeforeRounding() {
        val size = OcrPreprocessor.detectorSize(sourceWidth = 4_000, sourceHeight = 3_000)

        assertEquals(2_048, size.width)
        assertEquals(1_536, size.height)
        assertTrue(size.width <= 2_048 && size.height <= 2_048)
    }
}
