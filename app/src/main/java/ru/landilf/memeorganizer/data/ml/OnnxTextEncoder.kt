package ru.landilf.memeorganizer.data.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.domain.model.ml.TextEncoder
import java.nio.LongBuffer

/** Owns its ONNX session. The caller must close it after use. */
class OnnxTextEncoder private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    override val embeddingDimension: Int,
    override val maxSequenceLength: Int
) : TextEncoder {
    private val lock = Mutex()
    @Volatile private var closed = false

    override suspend fun encode(inputIds: LongArray, attentionMask: LongArray): FloatArray = withContext(Dispatchers.Default) {
        require(inputIds.size == maxSequenceLength && attentionMask.size == maxSequenceLength)
        lock.withLock {
            check(!closed) { "Encoder is closed" }
            val shape = longArrayOf(1, maxSequenceLength.toLong())
            OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), shape).use { ids ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), shape).use { mask ->
                    session.run(mapOf("input_ids" to ids, "attention_mask" to mask)).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val values = (result.get("sentence_embedding").orElseThrow().value as Array<FloatArray>).single()
                        require(values.size == embeddingDimension && values.all { it.isFinite() })
                        values.copyOf()
                    }
                }
            }
        }
    }

    override fun close() {
        kotlinx.coroutines.runBlocking {
            lock.withLock {
                if (!closed) { closed = true; session.close() }
            }
        }
    }

    companion object {
        suspend fun open(modelPackage: TextModelPackage): OnnxTextEncoder = withContext(Dispatchers.IO) {
            val environment = OrtEnvironment.getEnvironment()
            val session = OrtSession.SessionOptions().use { options -> environment.createSession(modelPackage.modelFile.absolutePath, options) }
            OnnxTextEncoder(environment, session, modelPackage.dimension, modelPackage.maxLength)
        }
    }
}
