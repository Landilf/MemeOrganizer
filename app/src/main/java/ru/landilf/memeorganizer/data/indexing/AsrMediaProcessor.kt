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
import ru.landilf.memeorganizer.domain.model.ml.AsrIndexingMetrics
import ru.landilf.memeorganizer.domain.model.ml.ExtractedTextSource

@Singleton
class AsrMediaProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val extractedTextChunkDao: ExtractedTextChunkDao
) {
    suspend fun process(meme: MemeEntity, modelPackage: TextModelPackage): AsrIndexingMetrics? {
        if (!meme.mimeType.startsWith(VIDEO_MIME_PREFIX)) return null
        val engine = SherpaAsrEngine.open(modelPackage) ?: run {
            AppLogger.ml { "asr.skip meme_id=${meme.id} reason=runtime_unavailable" }
            return AsrIndexingMetrics(runtimeAvailable = false)
        }
        val file = File(context.filesDir, "memes/${meme.storedFileName}")
        val chunks = mutableListOf<ExtractedTextChunkEntity>()
        val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
        var inferenceMs = 0L
        try {
            val extraction = VideoAudioChunkExtractor().extract(file) { audioChunk ->
                val inferenceStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
                val text = engine.transcribe(audioChunk.samples)
                inferenceMs += elapsedMs(inferenceStartedAt)
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
            if (!extraction.audioTrackFound) {
                extractedTextChunkDao.deleteForMemeAndSource(
                    meme.id,
                    ExtractedTextSource.Asr.storageValue
                )
                AppLogger.ml { "asr.skip meme_id=${meme.id} reason=no_audio_track" }
                return AsrIndexingMetrics(
                    runtimeAvailable = true,
                    audioTrackFound = false,
                    audioDecodeMs = extraction.decodeMs,
                    totalMs = elapsedMs(startedAt)
                )
            }
            extractedTextChunkDao.deleteForMemeAndSource(meme.id, ExtractedTextSource.Asr.storageValue)
            if (chunks.isNotEmpty()) extractedTextChunkDao.insertAll(chunks)
            val totalMs = elapsedMs(startedAt)
            AppLogger.ml {
                "asr.done meme_id=${meme.id} audio_chunks=${extraction.audioChunkCount} " +
                    "text_chunks=${chunks.size} elapsed_ms=$totalMs"
            }
            return AsrIndexingMetrics(
                runtimeAvailable = true,
                audioTrackFound = true,
                audioDecodeMs = extraction.decodeMs,
                inferenceMs = inferenceMs,
                audioChunkCount = extraction.audioChunkCount,
                textChunkCount = chunks.size,
                textCharacterCount = chunks.sumOf { it.text.length },
                totalMs = totalMs
            )
        } finally {
            engine.close()
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / NANOS_PER_MILLISECOND

    private companion object {
        const val VIDEO_MIME_PREFIX = "video/"
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
