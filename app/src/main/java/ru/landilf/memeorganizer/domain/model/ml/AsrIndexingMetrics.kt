package ru.landilf.memeorganizer.domain.model.ml

/** Measurements for one video's ASR stage, recorded in the matching indexing event. */
data class AsrIndexingMetrics(
    val runtimeAvailable: Boolean,
    val audioTrackFound: Boolean? = null,
    val audioDecodeMs: Long? = null,
    val inferenceMs: Long? = null,
    val audioChunkCount: Int = 0,
    val textChunkCount: Int = 0,
    val textCharacterCount: Int = 0,
    val totalMs: Long? = null
)
