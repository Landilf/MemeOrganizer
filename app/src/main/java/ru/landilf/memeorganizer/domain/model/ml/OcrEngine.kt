package ru.landilf.memeorganizer.domain.model.ml

enum class ExtractedTextSource(val storageValue: String) {
    Ocr("ocr"),
    Asr("asr")
}

/**
 * Platform-neutral OCR input. Bitmap decoding and colour conversion belong to the data layer.
 * Pixels are packed BGR bytes in row-major order.
 */
data class OcrImage(
    val width: Int,
    val height: Int,
    val bgrPixels: ByteArray
) {
    init {
        require(width > 0 && height > 0)
        require(bgrPixels.size.toLong() == width.toLong() * height * CHANNEL_COUNT) {
            "Invalid BGR image size"
        }
    }

    private companion object {
        const val CHANNEL_COUNT = 3
    }
}

/** Coordinates are normalised to the [0, 1] range of the source image. */
data class OcrTextBlock(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
) {
    init {
        require(text.isNotBlank())
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(left <= right && top <= bottom)
        require(confidence in 0f..1f)
    }
}

/**
 * Isolates OCR implementation details from indexing. Instances are worker-scoped and must be
 * closed after a batch so detector and recognizer sessions do not remain in memory.
 */
interface OcrEngine : AutoCloseable {
    suspend fun recognize(image: OcrImage): List<OcrTextBlock>
}
