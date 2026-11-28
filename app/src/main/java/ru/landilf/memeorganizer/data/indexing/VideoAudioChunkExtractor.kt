package ru.landilf.memeorganizer.data.indexing

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a video's first audio track and forwards non-overlapping mono 16 kHz chunks without
 * keeping the complete soundtrack in memory.
 */
internal class VideoAudioChunkExtractor {
    suspend fun extract(
        file: File,
        onChunk: suspend (AudioPcmChunk) -> Unit
    ): AudioExtractionResult = withContext(Dispatchers.Default) {
        val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
        var callbackNanos = 0L
        var audioChunkCount = 0
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var decoderStarted = false
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith(AUDIO_MIME_PREFIX) == true
            } ?: return@withContext AudioExtractionResult(
                audioTrackFound = false,
                audioChunkCount = 0,
                decodeMs = elapsedMs(startedAt)
            )
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            extractor.selectTrack(trackIndex)
            val configuredDecoder = MediaCodec.createDecoderByType(mimeType).apply {
                configure(inputFormat, null, null, 0)
                start()
                decoderStarted = true
            }
            decoder = configuredDecoder
            decodeTrack(extractor, configuredDecoder, inputFormat) { chunk ->
                val callbackStartedAt = android.os.SystemClock.elapsedRealtimeNanos()
                try {
                    onChunk(chunk)
                    audioChunkCount++
                } finally {
                    callbackNanos += android.os.SystemClock.elapsedRealtimeNanos() - callbackStartedAt
                }
            }
            AudioExtractionResult(
                audioTrackFound = true,
                audioChunkCount = audioChunkCount,
                decodeMs = ((android.os.SystemClock.elapsedRealtimeNanos() - startedAt - callbackNanos) /
                    NANOS_PER_MILLISECOND).coerceAtLeast(0)
            )
        } finally {
            if (decoderStarted) runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }

    private suspend fun decodeTrack(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        inputFormat: MediaFormat,
        onChunk: suspend (AudioPcmChunk) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        val accumulator = AudioChunkAccumulator()
        var resampler = resamplerFor(inputFormat)
        var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmEncoding = inputFormat.pcmEncodingOrDefault()
        var inputEnded = false
        var outputEnded = false
        while (!outputEnded) {
            if (!inputEnded) {
                val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = requireNotNull(decoder.getInputBuffer(inputIndex)).apply { clear() }
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEnded = true
                    } else {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime.coerceAtLeast(0),
                            0
                        )
                        extractor.advance()
                    }
                }
            }
            when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = decoder.outputFormat
                    resampler = resamplerFor(outputFormat)
                    channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmEncoding = outputFormat.pcmEncodingOrDefault()
                }

                MediaCodec.INFO_TRY_AGAIN_LATER,
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                else -> if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = requireNotNull(decoder.getOutputBuffer(outputIndex))
                        accumulator.append(
                            resampler.resample(
                                outputBuffer.toFloatSamples(bufferInfo, pcmEncoding, channelCount)
                            ),
                            onChunk
                        )
                    }
                    outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
        accumulator.finish(onChunk)
    }

    private fun resamplerFor(format: MediaFormat): MonoPcmResampler = MonoPcmResampler(
        sourceSampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    )

    private fun MediaFormat.pcmEncodingOrDefault(): Int = if (containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        getInteger(MediaFormat.KEY_PCM_ENCODING)
    } else {
        AudioFormat.ENCODING_PCM_16BIT
    }

    private fun ByteBuffer.toFloatSamples(
        bufferInfo: MediaCodec.BufferInfo,
        pcmEncoding: Int,
        channelCount: Int
    ): FloatArray {
        val bytesPerSample = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> PCM_16_BYTES
            AudioFormat.ENCODING_PCM_FLOAT -> PCM_FLOAT_BYTES
            else -> error("Unsupported decoded PCM encoding: $pcmEncoding")
        }
        require(bufferInfo.size % (bytesPerSample * channelCount) == 0) { "Invalid decoded PCM buffer" }
        val samples = FloatArray(bufferInfo.size / bytesPerSample)
        val source = duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
            position(bufferInfo.offset)
            limit(bufferInfo.offset + bufferInfo.size)
        }
        samples.indices.forEach { index ->
            samples[index] = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_16BIT -> source.getShort() / PCM_16_SCALE
                AudioFormat.ENCODING_PCM_FLOAT -> source.getFloat()
                else -> error("Unsupported decoded PCM encoding: $pcmEncoding")
            }
        }
        return samples
    }

    private companion object {
        const val AUDIO_MIME_PREFIX = "audio/"
        const val CODEC_TIMEOUT_US = 10_000L
        const val PCM_16_BYTES = 2
        const val PCM_FLOAT_BYTES = 4
        const val PCM_16_SCALE = 32_768f
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }

    private fun elapsedMs(startedAt: Long): Long =
        (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / NANOS_PER_MILLISECOND
}
