package ru.landilf.memeorganizer.data.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import ru.landilf.memeorganizer.domain.model.ml.OcrImage

internal object OcrImageLoader {
    fun load(file: File): OcrImage {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Cannot decode image")
        val oriented = bitmap.withExifOrientation(file)
        try {
            val pixels = IntArray(oriented.width * oriented.height)
            oriented.getPixels(pixels, 0, oriented.width, 0, 0, oriented.width, oriented.height)
            return OcrImage(
                width = oriented.width,
                height = oriented.height,
                bgrPixels = ByteArray(pixels.size * 3).also { bgr ->
                    pixels.forEachIndexed { index, pixel ->
                        bgr[index * 3] = (pixel and 0xff).toByte()
                        bgr[index * 3 + 1] = (pixel shr 8 and 0xff).toByte()
                        bgr[index * 3 + 2] = (pixel shr 16 and 0xff).toByte()
                    }
                }
            )
        } finally {
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()
        }
    }

    private fun Bitmap.withExifOrientation(file: File): Bitmap {
        val degrees = when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)
    }
}
