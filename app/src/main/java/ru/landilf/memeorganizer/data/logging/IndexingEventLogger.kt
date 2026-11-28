package ru.landilf.memeorganizer.data.logging

import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import ru.landilf.memeorganizer.core.logging.LightLogStore
import ru.landilf.memeorganizer.data.ml.TextModelPackage
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.domain.model.ml.AsrIndexingMetrics

/** Writes one exportable diagnostic event for a single media-indexing attempt. */
@Singleton
class IndexingEventLogger @Inject constructor(
    private val memeDao: MemeDao,
    private val lightLog: LightLogStore
) {
    suspend fun record(
        memeId: Long,
        modelPackage: TextModelPackage,
        textEncoderMs: Long?,
        databaseWriteMs: Long?,
        totalMs: Long,
        status: String,
        errorType: String? = null,
        asrMetrics: AsrIndexingMetrics? = null
    ) {
        val meme = memeDao.getById(memeId) ?: return
        lightLog.record(
            "indexing_event",
            JSONObject()
                .put("file_hash", meme.contentHash)
                .put("model_release_tag", modelPackage.releaseTag)
                .put("media_type", meme.mediaType)
                .put(
                    "stats", JSONObject()
                        .put("asr_audio_chunks", asrMetrics?.audioChunkCount ?: JSONObject.NULL)
                        .put("asr_text_chunks", asrMetrics?.textChunkCount ?: JSONObject.NULL)
                        .put("asr_characters", asrMetrics?.textCharacterCount ?: JSONObject.NULL)
                )
                .put(
                    "step_latency_ms", JSONObject()
                        .put("extract_media", asrMetrics?.audioDecodeMs ?: JSONObject.NULL)
                        .put("asr", asrMetrics?.inferenceMs ?: JSONObject.NULL)
                        .put("text_encode", textEncoderMs ?: JSONObject.NULL)
                )
                .put(
                    "model_latency_ms", JSONObject()
                        .put("text_encoder", textEncoderMs ?: JSONObject.NULL)
                        .put("visual_encoder", JSONObject.NULL)
                        .put("ocr_engine", JSONObject.NULL)
                        .put("asr_engine", asrMetrics?.inferenceMs ?: JSONObject.NULL)
                        .put("reranker", JSONObject.NULL)
                )
                .put(
                    "asr", JSONObject()
                        .put("runtime_available", asrMetrics?.runtimeAvailable ?: JSONObject.NULL)
                        .put("audio_track_found", asrMetrics?.audioTrackFound ?: JSONObject.NULL)
                        .put("audio_decode_ms", asrMetrics?.audioDecodeMs ?: JSONObject.NULL)
                        .put("inference_ms", asrMetrics?.inferenceMs ?: JSONObject.NULL)
                        .put("audio_chunk_count", asrMetrics?.audioChunkCount ?: JSONObject.NULL)
                        .put("text_chunk_count", asrMetrics?.textChunkCount ?: JSONObject.NULL)
                        .put("text_character_count", asrMetrics?.textCharacterCount ?: JSONObject.NULL)
                        .put("total_ms", asrMetrics?.totalMs ?: JSONObject.NULL)
                )
                .put("database_write_ms", databaseWriteMs ?: JSONObject.NULL)
                .put("total_ms", totalMs)
                .put("status", status)
                .put("error_type", errorType ?: JSONObject.NULL)
        )
    }
}
