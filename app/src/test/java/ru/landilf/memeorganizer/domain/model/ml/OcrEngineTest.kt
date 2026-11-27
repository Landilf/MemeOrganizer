package ru.landilf.memeorganizer.domain.model.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OcrEngineTest {
    @Test
    fun `ocr image accepts exactly one BGR triplet per pixel`() {
        val image = OcrImage(width = 2, height = 1, bgrPixels = ByteArray(6))

        assertEquals(6, image.bgrPixels.size)
    }

    @Test
    fun `ocr image rejects invalid BGR size`() {
        assertThrows(IllegalArgumentException::class.java) {
            OcrImage(width = 2, height = 1, bgrPixels = ByteArray(5))
        }
    }

    @Test
    fun `ocr block rejects coordinates outside source image`() {
        assertThrows(IllegalArgumentException::class.java) {
            OcrTextBlock(
                text = "текст",
                left = -0.1f,
                top = 0f,
                right = 1f,
                bottom = 1f,
                confidence = 1f
            )
        }
    }
}
