package ru.landilf.memeorganizer.data.ml

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.domain.model.ml.AsrEngine

/** Offline NeMo CTC implementation backed by the pinned sherpa-onnx runtime. */
class SherpaAsrEngine private constructor(
    private val recognizer: OfflineRecognizer
) : AsrEngine {
    private val lock = Mutex()

    @Volatile
    private var closed = false

    override suspend fun transcribe(pcm16kMono: FloatArray): String = withContext(Dispatchers.Default) {
        require(pcm16kMono.all { it in -1f..1f }) { "PCM samples must be in [-1, 1]" }
        lock.withLock {
            check(!closed) { "ASR engine is closed" }
            val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(pcm16kMono, SAMPLE_RATE_HZ)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim().also { text ->
                    val elapsedMs =
                        (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                    AppLogger.ml {
                        "asr.inference samples=${pcm16kMono.size} chars=${text.length} elapsed_ms=%.2f"
                            .format(java.util.Locale.ROOT, elapsedMs)
                    }
                }
            } finally {
                stream.release()
            }
        }
    }

    override fun close() {
        runBlocking {
            lock.withLock {
                if (!closed) {
                    closed = true
                    recognizer.release()
                }
            }
        }
    }

    companion object {
        suspend fun open(modelPackage: TextModelPackage): SherpaAsrEngine? =
            withContext(Dispatchers.Default) {
                val component = modelPackage.components[ASR_COMPONENT] ?: return@withContext null
                if (component.runtime != SHERPA_RUNTIME) return@withContext null
                val tokens = component.auxiliaryFiles[TOKENS_PATH] ?: return@withContext null
                val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
                val recognizer = OfflineRecognizer(
                    config = OfflineRecognizerConfig(
                        modelConfig = OfflineModelConfig(
                            nemo = OfflineNemoEncDecCtcModelConfig(
                                model = component.modelFile.absolutePath
                            ),
                            tokens = tokens.absolutePath,
                            numThreads = THREAD_COUNT,
                            provider = "cpu"
                        )
                    )
                )
                SherpaAsrEngine(recognizer).also {
                    val elapsedMs =
                        (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                    AppLogger.ml {
                        "asr.open elapsed_ms=%.2f".format(java.util.Locale.ROOT, elapsedMs)
                    }
                }
            }

        const val SAMPLE_RATE_HZ = 16_000
        private const val ASR_COMPONENT = "asr"
        private const val SHERPA_RUNTIME = "sherpa-onnx:1.10.37"
        private const val TOKENS_PATH = "asr/tokens.txt"
        private const val THREAD_COUNT = 2
    }
}
