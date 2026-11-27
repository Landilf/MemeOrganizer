package ru.landilf.memeorganizer.domain.model.ml

/** The four channels defined by the multimodal model package. */
data class FusionInput(
    val queryEmbedding: FloatArray,
    val channelScores: List<FloatArray>,
    val channelMask: List<BooleanArray>
) {
    init {
        require(queryEmbedding.size == EMBEDDING_DIMENSION)
        require(channelScores.isNotEmpty())
        require(channelScores.size == channelMask.size)
        require(channelScores.all { it.size == CHANNEL_COUNT })
        require(channelMask.all { it.size == CHANNEL_COUNT })
        require(channelScores.all { scores -> scores.all { score -> score.isFinite() } })
    }

    val documentCount: Int get() = channelScores.size

    private companion object {
        const val EMBEDDING_DIMENSION = 384
        const val CHANNEL_COUNT = 4
    }
}

/** Owns no application state; callers close the implementation after a search batch. */
interface ScoreFusionEngine : AutoCloseable {
    suspend fun fuse(input: FusionInput): FloatArray
}
