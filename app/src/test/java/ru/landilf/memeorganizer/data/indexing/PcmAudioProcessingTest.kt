package ru.landilf.memeorganizer.data.indexing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmAudioProcessingTest {
    @Test
    fun downmixesStereoAndKeeps16KhzRate() {
        val result = MonoPcmResampler(
            sourceSampleRateHz = 16_000,
            channelCount = 2
        ).resample(floatArrayOf(1f, -1f, 0.5f, 0.5f))

        assertArrayEquals(floatArrayOf(0f, 0.5f), result, 0f)
    }

    @Test
    fun emitsNonOverlappingChunksWithAbsoluteTimestamps() = kotlinx.coroutines.test.runTest {
        val accumulator = AudioChunkAccumulator(sampleRateHz = 4, durationMs = 1_000)
        val chunks = mutableListOf<AudioPcmChunk>()

        accumulator.append(floatArrayOf(1f, 2f, 3f)) { chunks += it }
        accumulator.append(floatArrayOf(4f, 5f, 6f, 7f, 8f, 9f)) { chunks += it }
        accumulator.finish { chunks += it }

        assertEquals(listOf(0L, 1_000L, 2_000L), chunks.map(AudioPcmChunk::timestampMs))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), chunks[0].samples, 0f)
        assertArrayEquals(floatArrayOf(5f, 6f, 7f, 8f), chunks[1].samples, 0f)
        assertArrayEquals(floatArrayOf(9f), chunks[2].samples, 0f)
    }
}
