package ru.landilf.memeorganizer.domain.model.ml

/** Tokenization is supplied separately and must match the model's tokenizer. */
interface TextEncoder : AutoCloseable {
    val embeddingDimension: Int
    val maxSequenceLength: Int
    suspend fun encode(inputIds: LongArray, attentionMask: LongArray): FloatArray
}
