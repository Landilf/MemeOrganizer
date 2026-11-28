package ru.landilf.memeorganizer.data.indexing

internal data class AudioPcmChunk(
    val timestampMs: Long,
    val samples: FloatArray
)

/**
 * Converts interleaved decoded PCM frames to a mono 16 kHz stream. Nearest-neighbour resampling
 * keeps this lightweight; feature extraction and normalisation stay inside sherpa-onnx.
 */
internal class MonoPcmResampler(
    private val sourceSampleRateHz: Int,
    private val channelCount: Int,
    private val targetSampleRateHz: Int = TARGET_SAMPLE_RATE_HZ
) {
    init {
        require(sourceSampleRateHz > 0)
        require(channelCount > 0)
    }

    private var targetAccumulator = 0L

    fun resample(interleavedSamples: FloatArray): FloatArray {
        require(interleavedSamples.size % channelCount == 0) { "Invalid interleaved PCM" }
        val sourceFrameCount = interleavedSamples.size / channelCount
        if (sourceFrameCount == 0) return FloatArray(0)
        val estimatedSize = (sourceFrameCount.toLong() * targetSampleRateHz / sourceSampleRateHz)
            .toInt().coerceAtLeast(1)
        val result = FloatArray(estimatedSize + 1)
        var resultSize = 0
        repeat(sourceFrameCount) { frameIndex ->
            val frameOffset = frameIndex * channelCount
            val mono = (0 until channelCount).sumOf { channelIndex ->
                interleavedSamples[frameOffset + channelIndex].toDouble()
            }.div(channelCount).toFloat().coerceIn(-1f, 1f)
            targetAccumulator += targetSampleRateHz
            while (targetAccumulator >= sourceSampleRateHz) {
                check(resultSize < result.size) { "Resampling buffer is too small" }
                result[resultSize++] = mono
                targetAccumulator -= sourceSampleRateHz
            }
        }
        return result.copyOf(resultSize)
    }

    companion object {
        const val TARGET_SAMPLE_RATE_HZ = 16_000
    }
}

/** Accumulates resampled audio into the fixed non-overlapping ASR windows from the ML contract. */
internal class AudioChunkAccumulator(
    private val sampleRateHz: Int = MonoPcmResampler.TARGET_SAMPLE_RATE_HZ,
    private val durationMs: Long = CHUNK_DURATION_MS
) {
    private val chunkSizeSamples = (sampleRateHz * durationMs / MILLIS_PER_SECOND).toInt()
    private var chunkIndex = 0L
    private var writtenSamples = 0
    private var buffer = FloatArray(chunkSizeSamples)

    init {
        require(chunkSizeSamples > 0)
    }

    suspend fun append(samples: FloatArray, onChunk: suspend (AudioPcmChunk) -> Unit) {
        var sourceOffset = 0
        while (sourceOffset < samples.size) {
            val copiedCount = minOf(chunkSizeSamples - writtenSamples, samples.size - sourceOffset)
            samples.copyInto(buffer, writtenSamples, sourceOffset, sourceOffset + copiedCount)
            writtenSamples += copiedCount
            sourceOffset += copiedCount
            if (writtenSamples == chunkSizeSamples) emitChunk(onChunk)
        }
    }

    suspend fun finish(onChunk: suspend (AudioPcmChunk) -> Unit) {
        if (writtenSamples > 0) emitChunk(onChunk)
    }

    private suspend fun emitChunk(onChunk: suspend (AudioPcmChunk) -> Unit) {
        onChunk(
            AudioPcmChunk(
                timestampMs = chunkIndex * durationMs,
                samples = buffer.copyOf(writtenSamples)
            )
        )
        chunkIndex++
        writtenSamples = 0
        buffer = FloatArray(chunkSizeSamples)
    }

    companion object {
        const val CHUNK_DURATION_MS = 25_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
