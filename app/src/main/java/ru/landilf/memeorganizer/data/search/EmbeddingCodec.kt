package ru.landilf.memeorganizer.data.search

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object EmbeddingCodec {
    fun encode(values: FloatArray): ByteArray =
        ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putFloat)
        }.array()

    fun decode(bytes: ByteArray, dimension: Int): FloatArray {
        require(bytes.size == dimension * Float.SIZE_BYTES) { "Unexpected embedding size" }
        return FloatArray(dimension).also { values ->
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values)
        }
    }

    fun cosine(left: FloatArray, right: FloatArray): Float {
        require(left.size == right.size)
        var score = 0f
        for (index in left.indices) score += left[index] * right[index]
        return score.coerceIn(-1f, 1f)
    }
}
