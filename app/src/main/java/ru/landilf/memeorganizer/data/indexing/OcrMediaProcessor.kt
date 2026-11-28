package ru.landilf.memeorganizer.data.indexing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import ru.landilf.memeorganizer.data.ml.OcrImageLoader
import ru.landilf.memeorganizer.data.ml.OnnxOcrEngine
import ru.landilf.memeorganizer.data.ml.TextModelPackage
import ru.landilf.memeorganizer.data.storage.dao.ExtractedTextChunkDao
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.data.storage.entities.search.ExtractedTextChunkEntity
import ru.landilf.memeorganizer.domain.model.ml.ExtractedTextSource

@Singleton
class OcrMediaProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val extractedTextChunkDao: ExtractedTextChunkDao
) {
    suspend fun process(meme: MemeEntity, modelPackage: TextModelPackage): Boolean {
        val file = File(context.filesDir, "memes/${meme.storedFileName}")
        val inputs: List<Pair<ru.landilf.memeorganizer.domain.model.ml.OcrImage, Long?>> = when {
            meme.mimeType.startsWith("image/") && meme.mimeType != "image/gif" -> {
                listOf(OcrImageLoader.load(file) to null)
            }

            meme.mimeType == "image/gif" -> loadGifFrames(file)
            meme.mimeType.startsWith("video/") -> loadVideoFrames(file)

            else -> null
        } ?: return false
        val engine = OnnxOcrEngine.open(modelPackage) ?: return false
        try {
            val blocks = inputs.flatMap { (image, timestampMs) ->
                engine.recognize(image).map { block -> timestampMs to block.text }
            }.distinctBy { (_, text) -> text.normalizedForDeduplication() }
            extractedTextChunkDao.deleteForMemeAndSource(meme.id, ExtractedTextSource.Ocr.storageValue)
            if (blocks.isNotEmpty()) extractedTextChunkDao.insertAll(
                blocks.map { (timestampMs, text) ->
                    ExtractedTextChunkEntity(
                        memeId = meme.id,
                        source = ExtractedTextSource.Ocr.storageValue,
                        timestampMs = timestampMs,
                        text = text,
                        modelReleaseTag = modelPackage.releaseTag,
                        createdAtMillis = System.currentTimeMillis()
                    )
                }
            )
            return true
        } finally {
            engine.close()
        }
    }

    private fun loadVideoFrames(file: File): List<Pair<ru.landilf.memeorganizer.domain.model.ml.OcrImage, Long?>>? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return@runCatching null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return@runCatching null
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val scale = minOf(1f, MAX_VIDEO_FRAME_SIDE / maxOf(width, height).toFloat())
            frameTimestamps(durationMs).mapNotNull { timestampMs ->
                retriever.getScaledFrameAtTime(
                    timestampMs * 1_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )?.let { bitmap ->
                    try { OcrImageLoader.fromBitmap(bitmap) to timestampMs } finally { bitmap.recycle() }
                }
            }
        } finally {
            retriever.release()
        }
    }.getOrNull()

    @Suppress("DEPRECATION") // AnimatedImageDrawable cannot expose individual GIF frames by timestamp.
    private fun loadGifFrames(file: File): List<Pair<ru.landilf.memeorganizer.domain.model.ml.OcrImage, Long?>>? = runCatching {
        val movie = Movie.decodeFile(file.absolutePath) ?: return@runCatching null
        val durationMs = movie.duration().takeIf { it > 0 } ?: 1
        frameTimestamps(durationMs.toLong()).map { timestampMs ->
            movie.setTime(timestampMs.toInt())
            val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
            movie.draw(Canvas(bitmap), 0f, 0f)
            try { OcrImageLoader.fromBitmap(bitmap) to timestampMs } finally { bitmap.recycle() }
        }
    }.getOrNull()

    private fun frameTimestamps(durationMs: Long): List<Long> = listOf(0L, durationMs / 2, (durationMs - 1).coerceAtLeast(0L)).distinct()

    private fun String.normalizedForDeduplication(): String = lowercase().replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val MAX_VIDEO_FRAME_SIDE = 2_048
    }
}
