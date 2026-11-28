package ru.landilf.memeorganizer.data.ml

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class OcrDetectorSize(val width: Int, val height: Int, val scale: Float)

/** RapidOCR-compatible detector sizing with an Android memory guard. */
internal object OcrPreprocessor {
    private const val LIMIT_SIDE_LENGTH = 736
    private const val MAX_SIDE_LENGTH = 2048
    private const val SIZE_MULTIPLE = 32

    fun detectorSize(sourceWidth: Int, sourceHeight: Int): OcrDetectorSize {
        require(sourceWidth > 0 && sourceHeight > 0)
        var scale = if (min(sourceWidth, sourceHeight) < LIMIT_SIDE_LENGTH) {
            LIMIT_SIDE_LENGTH.toFloat() / min(sourceWidth, sourceHeight)
        } else {
            1f
        }
        val scaledLargestSide = max(sourceWidth, sourceHeight) * scale
        if (scaledLargestSide > MAX_SIDE_LENGTH) {
            scale *= MAX_SIDE_LENGTH / scaledLargestSide
        }
        return OcrDetectorSize(
            width = roundToMultiple(sourceWidth * scale),
            height = roundToMultiple(sourceHeight * scale),
            scale = scale
        )
    }

    private fun roundToMultiple(value: Float): Int =
        max((value / SIZE_MULTIPLE).roundToInt() * SIZE_MULTIPLE, SIZE_MULTIPLE)
}
