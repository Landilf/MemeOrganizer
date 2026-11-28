package ru.landilf.memeorganizer.data.ml

import android.util.Base64
import java.io.File
import javax.inject.Inject
import org.json.JSONObject

sealed interface AsrSmokeTestResult {
    data object Unavailable : AsrSmokeTestResult
    data class Passed(val testCount: Int) : AsrSmokeTestResult
}

/** Runs the package-provided silence and speech fixtures without modifying the media library. */
class AsrSmokeTestRunner @Inject constructor() {
    suspend fun run(modelPackage: TextModelPackage): AsrSmokeTestResult {
        if (modelPackage.components[ASR_COMPONENT] == null) return AsrSmokeTestResult.Unavailable
        val tests = JSONObject(File(modelPackage.modelFile.parentFile, SMOKE_TEST_FILE).readText())
            .optJSONArray(ASR_TESTS) ?: return AsrSmokeTestResult.Unavailable
        require(tests.length() > 0) { "ASR smoke tests are empty" }
        val engine = SherpaAsrEngine.open(modelPackage) ?: return AsrSmokeTestResult.Unavailable
        try {
            repeat(tests.length()) { index ->
                val test = tests.getJSONObject(index)
                val pcm = WavPcmDecoder.decode(
                    Base64.decode(test.getString(WAV_BASE64), Base64.DEFAULT)
                )
                val actual = engine.transcribe(pcm.samples)
                val expected = test.getString(EXPECTED_TEXT)
                require(actual.normalizedForComparison() == expected.normalizedForComparison()) {
                    "ASR smoke test $index failed: $actual"
                }
            }
            return AsrSmokeTestResult.Passed(tests.length())
        } finally {
            engine.close()
        }
    }

    private fun String.normalizedForComparison(): String =
        lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private companion object {
        const val ASR_COMPONENT = "asr"
        const val SMOKE_TEST_FILE = "smoke_test_pack.json"
        const val ASR_TESTS = "asr_tests"
        const val WAV_BASE64 = "wav_base64"
        const val EXPECTED_TEXT = "expected_text"
    }
}

internal data class PcmWave(val sampleRateHz: Int, val samples: FloatArray)

/** Decodes the 16-bit mono PCM WAV fixtures supplied with the ASR Smoke Test Pack. */
internal object WavPcmDecoder {
    fun decode(bytes: ByteArray): PcmWave {
        require(bytes.size >= RIFF_HEADER_SIZE) { "WAV is too short" }
        require(bytes.asAscii(0, 4) == RIFF && bytes.asAscii(8, 4) == WAVE) { "Invalid WAV header" }
        var offset = RIFF_HEADER_SIZE
        var format: Int? = null
        var channels: Int? = null
        var sampleRate: Int? = null
        var bitsPerSample: Int? = null
        var dataOffset = -1
        var dataSize = -1
        while (offset + CHUNK_HEADER_SIZE <= bytes.size) {
            val chunkId = bytes.asAscii(offset, 4)
            val declaredChunkSize = bytes.littleEndianInt(offset + 4)
            val contentOffset = offset + CHUNK_HEADER_SIZE
            val chunkSize = if (chunkId == DATA_CHUNK && declaredChunkSize == UNKNOWN_SIZE) {
                bytes.size - contentOffset
            } else {
                declaredChunkSize
            }
            require(chunkSize >= 0 && chunkSize <= bytes.size - contentOffset) { "Invalid WAV chunk" }
            when (chunkId) {
                FORMAT_CHUNK -> {
                    require(chunkSize >= FORMAT_MIN_SIZE) { "Invalid WAV format chunk" }
                    format = bytes.littleEndianShort(contentOffset)
                    channels = bytes.littleEndianShort(contentOffset + 2)
                    sampleRate = bytes.littleEndianInt(contentOffset + 4)
                    bitsPerSample = bytes.littleEndianShort(contentOffset + 14)
                }

                DATA_CHUNK -> {
                    dataOffset = contentOffset
                    dataSize = chunkSize
                }
            }
            offset = contentOffset + chunkSize + chunkSize % 2
        }
        require(format == PCM_FORMAT && channels == MONO_CHANNEL_COUNT) { "Expected mono PCM WAV" }
        require(sampleRate == SherpaAsrEngine.SAMPLE_RATE_HZ && bitsPerSample == PCM_BITS) {
            "Expected 16 kHz 16-bit PCM WAV"
        }
        require(dataOffset >= 0 && dataSize >= 0 && dataSize % PCM_BYTES == 0) { "Missing WAV data" }
        return PcmWave(
            sampleRateHz = requireNotNull(sampleRate),
            samples = FloatArray(dataSize / PCM_BYTES) { index ->
                bytes.littleEndianShort(dataOffset + index * PCM_BYTES) / PCM_SCALE
            }
        )
    }

    private fun ByteArray.asAscii(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.littleEndianInt(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            (this[offset + 3].toInt() shl 24)

    private fun ByteArray.littleEndianShort(offset: Int): Int =
        (this[offset].toInt() and 0xff) or (this[offset + 1].toInt() shl 8)

    private const val RIFF_HEADER_SIZE = 12
    private const val CHUNK_HEADER_SIZE = 8
    private const val FORMAT_MIN_SIZE = 16
    private const val PCM_FORMAT = 1
    private const val MONO_CHANNEL_COUNT = 1
    private const val PCM_BITS = 16
    private const val PCM_BYTES = 2
    private const val PCM_SCALE = 32_768f
    private const val UNKNOWN_SIZE = -1
    private const val RIFF = "RIFF"
    private const val WAVE = "WAVE"
    private const val FORMAT_CHUNK = "fmt "
    private const val DATA_CHUNK = "data"
}
