package ru.landilf.memeorganizer.data.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class WavPcmDecoderTest {
    @Test
    fun decodesMono16KhzPcmSamples() {
        val wave = WavPcmDecoder.decode(wav(samples = shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE)))

        assertEquals(16_000, wave.sampleRateHz)
        assertEquals(-1f, wave.samples[0], 0f)
        assertEquals(Short.MAX_VALUE / 32_768f, wave.samples[1], 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSampleRate() {
        WavPcmDecoder.decode(wav(sampleRateHz = 8_000, samples = shortArrayOf(0)))
    }

    @Test
    fun decodesPcmWithUnknownDataChunkSize() {
        val wave = WavPcmDecoder.decode(
            wav(samples = shortArrayOf(100, -100), unknownDataChunkSize = true)
        )

        assertEquals(2, wave.samples.size)
        assertEquals(100 / 32_768f, wave.samples[0], 0f)
        assertEquals(-100 / 32_768f, wave.samples[1], 0f)
    }

    private fun wav(
        sampleRateHz: Int = 16_000,
        samples: ShortArray,
        unknownDataChunkSize: Boolean = false
    ): ByteArray =
        ByteBuffer.allocate(WAV_HEADER_BYTES + samples.size * PCM_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray())
                putInt(PCM_HEADER_BYTES + samples.size * PCM_BYTES)
                put("WAVEfmt ".toByteArray())
                putInt(FORMAT_CHUNK_BYTES)
                putShort(PCM_FORMAT.toShort())
                putShort(MONO_CHANNEL_COUNT.toShort())
                putInt(sampleRateHz)
                putInt(sampleRateHz * PCM_BYTES)
                putShort(PCM_BYTES.toShort())
                putShort(PCM_BITS.toShort())
                put("data".toByteArray())
                putInt(if (unknownDataChunkSize) UNKNOWN_SIZE else samples.size * PCM_BYTES)
                samples.forEach { sample -> putShort(sample) }
            }
            .array()

    private companion object {
        const val WAV_HEADER_BYTES = 44
        const val PCM_HEADER_BYTES = 36
        const val FORMAT_CHUNK_BYTES = 16
        const val PCM_FORMAT = 1
        const val MONO_CHANNEL_COUNT = 1
        const val PCM_BYTES = 2
        const val PCM_BITS = 16
        const val UNKNOWN_SIZE = -1
    }
}
