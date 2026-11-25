package ru.landilf.memeorganizer.data.ml

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class OnnxTextEncoderTest {
    @Test
    fun matchesOnnxSmokeVectors() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "models/text-champion")
        assumeTrue("Place the extracted model bundle in ${directory.absolutePath}", File(directory, "preset_manifest.json").isFile)
        val modelPackage = TextModelPackage.load(directory)
        val smoke = JSONObject(File(directory, "smoke_test_pack.json").readText())
        val samples = smoke.getJSONArray("samples")
        val encoder = OnnxTextEncoder.open(modelPackage)
        try {
            for (index in 0 until samples.length()) {
                val sample = samples.getJSONObject(index)
                val input = sample.getJSONArray("input_ids")
                val mask = sample.getJSONArray("attention_mask")
                val expected = sample.getJSONArray("full_embedding")
                val result = encoder.encode(LongArray(input.length()) { input.getLong(it) }, LongArray(mask.length()) { mask.getLong(it) })
                assertEquals(modelPackage.dimension, result.size)
                var dot = 0.0
                var actualNorm = 0.0
                var expectedNorm = 0.0
                for (i in result.indices) {
                    val value = expected.getDouble(i)
                    dot += result[i] * value
                    actualNorm += result[i] * result[i]
                    expectedNorm += value * value
                }
                assertEquals(1.0, sqrt(actualNorm), 0.001)
                assertTrue("Embedding mismatch: ${sample.getString("sample_id")}", dot / sqrt(actualNorm * expectedNorm) > 0.995)
            }
        } finally {
            encoder.close()
        }
    }
}
