package ru.landilf.memeorganizer.data.indexing

import android.content.Context
import android.graphics.Bitmap
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
        val input = when {
            meme.mimeType.startsWith("image/") && meme.mimeType != "image/gif" -> {
                OcrImageLoader.load(file) to null
            }

            meme.mimeType.startsWith("video/") -> loadFirstVideoFrame(file)?.let {
                try {
                    OcrImageLoader.fromBitmap(it) to 0L
                } finally {
                    it.recycle()
                }
            }

            else -> null
        } ?: return false
        val engine = OnnxOcrEngine.open(modelPackage) ?: return false
        try {
            val blocks = engine.recognize(input.first)
            extractedTextChunkDao.deleteForMemeAndSource(meme.id, ExtractedTextSource.Ocr.storageValue)
            if (blocks.isNotEmpty()) extractedTextChunkDao.insertAll(
                blocks.map { block ->
                    ExtractedTextChunkEntity(
                        memeId = meme.id,
                        source = ExtractedTextSource.Ocr.storageValue,
                        timestampMs = input.second,
                        text = block.text,
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

    private fun loadFirstVideoFrame(file: File): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return@runCatching null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return@runCatching null
            val scale = minOf(1f, MAX_VIDEO_FRAME_SIDE / maxOf(width, height).toFloat())
            retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1)
            )
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private companion object {
        const val MAX_VIDEO_FRAME_SIDE = 2_048
    }
}
