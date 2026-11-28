package ru.landilf.memeorganizer.data.ml

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.domain.model.ml.FusionInput
import ru.landilf.memeorganizer.domain.model.ml.ScoreFusionEngine

/** ONNX implementation of the package's query-conditioned four-channel score fusion. */
class OnnxFusionEngine private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession
) : ScoreFusionEngine {
    private val lock = Mutex()

    @Volatile
    private var closed = false

    override suspend fun fuse(input: FusionInput): FloatArray = withContext(Dispatchers.Default) {
        lock.withLock {
            check(!closed) { "Fusion engine is closed" }
            val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
            val documentCount = input.documentCount
            val flattenedScores = FloatArray(documentCount * CHANNEL_COUNT)
            val flattenedMask = ByteArray(documentCount * CHANNEL_COUNT)
            input.channelScores.forEachIndexed { documentIndex, scores ->
                scores.copyInto(flattenedScores, destinationOffset = documentIndex * CHANNEL_COUNT)
                input.channelMask[documentIndex].forEachIndexed { channelIndex, enabled ->
                    flattenedMask[documentIndex * CHANNEL_COUNT + channelIndex] = if (enabled) 1 else 0
                }
            }
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input.queryEmbedding),
                longArrayOf(1, EMBEDDING_DIMENSION.toLong())
            ).use { queryEmbedding ->
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(flattenedScores),
                    longArrayOf(1, documentCount.toLong(), CHANNEL_COUNT.toLong())
                ).use { channelScores ->
                    OnnxTensor.createTensor(
                        environment,
                        ByteBuffer.wrap(flattenedMask),
                        longArrayOf(documentCount.toLong(), CHANNEL_COUNT.toLong()),
                        OnnxJavaType.BOOL
                    ).use { channelMask ->
                        session.run(
                            mapOf(
                                QUERY_EMBEDDING_INPUT to queryEmbedding,
                                CHANNEL_SCORES_INPUT to channelScores,
                                CHANNEL_MASK_INPUT to channelMask
                            )
                        ).use { result ->
                            @Suppress("UNCHECKED_CAST")
                            val scores = (result.get(FUSED_SCORES_OUTPUT).orElseThrow().value as Array<FloatArray>)
                                .single()
                            require(scores.size == documentCount && scores.all { score -> score.isFinite() })
                            scores.copyOf().also {
                                val elapsedMs =
                                    (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                                AppLogger.ml {
                                    "fusion.inference documents=$documentCount elapsed_ms=%.2f".format(
                                        java.util.Locale.ROOT,
                                        elapsedMs
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        kotlinx.coroutines.runBlocking {
            lock.withLock {
                if (!closed) {
                    closed = true
                    session.close()
                }
            }
        }
    }

    companion object {
        suspend fun open(modelPackage: TextModelPackage): OnnxFusionEngine? =
            withContext(Dispatchers.IO) {
                val component = modelPackage.components[FUSION_COMPONENT] ?: return@withContext null
                val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
                val environment = OrtEnvironment.getEnvironment()
                val session = OrtSession.SessionOptions().use { options ->
                    environment.createSession(component.modelFile.absolutePath, options)
                }
                OnnxFusionEngine(environment, session).also {
                    val elapsedMs =
                        (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                    AppLogger.ml {
                        "fusion.open elapsed_ms=%.2f".format(java.util.Locale.ROOT, elapsedMs)
                    }
                }
            }

        private const val FUSION_COMPONENT = "fusion"
        private const val QUERY_EMBEDDING_INPUT = "query_emb"
        private const val CHANNEL_SCORES_INPUT = "channel_scores"
        private const val CHANNEL_MASK_INPUT = "channel_mask"
        private const val FUSED_SCORES_OUTPUT = "fused_scores"
        private const val EMBEDDING_DIMENSION = 384
        private const val CHANNEL_COUNT = 4
    }
}
