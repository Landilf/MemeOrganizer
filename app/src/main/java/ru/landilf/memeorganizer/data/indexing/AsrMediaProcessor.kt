package ru.landilf.memeorganizer.data.indexing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.data.ml.SherpaAsrEngine
import ru.landilf.memeorganizer.data.ml.TextModelPackage
import ru.landilf.memeorganizer.data.storage.dao.ExtractedTextChunkDao
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.data.storage.entities.search.ExtractedTextChunkEntity
import ru.landilf.memeorganizer.domain.model.ml.ExtractedTextSource

@Singleton
class AsrMediaProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val extractedTextChunkDao: ExtractedTextChunkDao
) {
    suspend fun process(meme: MemeEntity, modelPackage: TextModelPackage): Boolean {
        if (!meme.mimeType.startsWith(VIDEO_MIME_PREFIX)) return false
        val engine = SherpaAsrEngine.open(modelPackage) ?: run {
            AppLogger.ml { "asr.skip meme_id=${meme.id} reason=runtime_unavailable" }
            return false
        }
        val file = File(context.filesDir, "memes/${meme.storedFileName}")
        val chunks = mutableListOf<ExtractedTextChunkEntity>()
        val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
        try {
            val audioFound = VideoAudioChunkExtractor().extract(file) { audioChunk ->
                val text = engine.transcribe(audioChunk.samples)
                if (text.isNotBlank()) {
                    chunks += ExtractedTextChunkEntity(
                        memeId = meme.id,
                        source = ExtractedTextSource.Asr.storageValue,
                        timestampMs = audioChunk.timestampMs,
                        text = text,
                        modelReleaseTag = modelPackage.releaseTag,
                        createdAtMillis = System.currentTimeMillis()
                    )
                }
            }
            if (!audioFound) {
                extractedTextChunkDao.deleteForMemeAndSource(
                    meme.id,
                    ExtractedTextSource.Asr.storageValue
                )
                AppLogger.ml { "asr.skip meme_id=${meme.id} reason=no_audio_track" }
                return false
            }
            extractedTextChunkDao.deleteForMemeAndSource(meme.id, ExtractedTextSource.Asr.storageValue)
            if (chunks.isNotEmpty()) extractedTextChunkDao.insertAll(chunks)
            val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
            AppLogger.ml {
                "asr.done meme_id=${meme.id} text_chunks=${chunks.size} elapsed_ms=%.2f"
                    .format(java.util.Locale.ROOT, elapsedMs)
            }
            return true
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val VIDEO_MIME_PREFIX = "video/"
    }
}
