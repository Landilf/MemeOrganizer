package ru.landilf.memeorganizer.data.ml

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class OnnxTextEncoderTest {
    @Test
    fun tokenizerMatchesSmokeTokens() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "models/text-champion")
        assumeTrue(
            "Place the extracted model bundle in ${directory.absolutePath}",
            File(directory, "preset_manifest.json").isFile
        )
        val modelPackage = TextModelPackage.load(directory)
        val samples =
            JSONObject(File(directory, "smoke_test_pack.json").readText()).getJSONArray("samples")
        val tokenizer = HuggingFaceTextTokenizer.open(modelPackage)
        try {
            for (index in 0 until samples.length()) {
                val sample = samples.getJSONObject(index)
                val input = sample.getJSONArray("input_ids")
                val mask = sample.getJSONArray("attention_mask")
                val text = sample.getString("raw_text")
                val tokens =
                    if (sample.getString("text_type") == "query") tokenizer.encodeQuery(text) else tokenizer.encodePassage(
                        text
                    )
                assertArrayEquals(
                    "Token IDs: ${sample.getString("sample_id")}",
                    LongArray(input.length()) { input.getLong(it) },
                    tokens.inputIds
                )
                assertArrayEquals(
                    "Attention mask: ${sample.getString("sample_id")}",
                    LongArray(mask.length()) { mask.getLong(it) },
                    tokens.attentionMask
                )
            }
        } finally {
            tokenizer.close()
        }
    }

    @Test
    fun matchesOnnxSmokeVectors() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "models/text-champion")
        assumeTrue(
            "Place the extracted model bundle in ${directory.absolutePath}",
            File(directory, "preset_manifest.json").isFile
        )
        val modelPackage = TextModelPackage.load(directory)
        val smoke = JSONObject(File(directory, "smoke_test_pack.json").readText())
        val samples = smoke.getJSONArray("samples")
        val encoder = TextEmbeddingEngine.open(modelPackage)
        val tokenizer = HuggingFaceTextTokenizer.open(modelPackage)
        val mismatches = mutableListOf<String>()
        try {
            for (index in 0 until samples.length()) {
                val sample = samples.getJSONObject(index)
                val input = sample.getJSONArray("input_ids")
                val mask = sample.getJSONArray("attention_mask")
                val expected = sample.getJSONArray("full_embedding")
                val text = sample.getString("raw_text")
                val tokens =
                    if (sample.getString("text_type") == "query") tokenizer.encodeQuery(text) else tokenizer.encodePassage(
                        text
                    )
                assertArrayEquals(
                    "Token IDs: ${sample.getString("sample_id")}",
                    LongArray(input.length()) { input.getLong(it) },
                    tokens.inputIds
                )
                assertArrayEquals(
                    "Attention mask: ${sample.getString("sample_id")}",
                    LongArray(mask.length()) { mask.getLong(it) },
                    tokens.attentionMask
                )
                val result =
                    if (sample.getString("text_type") == "query") encoder.encodeQuery(text) else encoder.encodePassage(
                        text
                    )
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
                assertEquals(1.0, sqrt(actualNorm), 0.0001)
                val cosine = dot / sqrt(actualNorm * expectedNorm)
                if (cosine < 0.98) mismatches.add("${sample.getString("sample_id")}: $cosine")
            }
        } finally {
            tokenizer.close()
            encoder.close()
        }
        assertTrue("Embedding mismatches: ${mismatches.joinToString()}", mismatches.isEmpty())
    }

    @Test
    fun preservesSmokeRankingWithReferenceBm25Scores() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "models/text-champion")
        assumeTrue(
            "Place the extracted model bundle in ${directory.absolutePath}",
            File(directory, "preset_manifest.json").isFile
        )
        val smoke = JSONObject(File(directory, "smoke_test_pack.json").readText())
        val encoder = TextEmbeddingEngine.open(TextModelPackage.load(directory))
        val vectors = mutableMapOf<String, FloatArray>()
        try {
            val samples = smoke.getJSONArray("samples")
            for (index in 0 until samples.length()) {
                val sample = samples.getJSONObject(index)
                val text = sample.getString("raw_text")
                vectors[sample.getString("sample_id")] =
                    if (sample.getString("text_type") == "query") encoder.encodeQuery(text) else encoder.encodePassage(
                        text
                    )
            }
        } finally {
            encoder.close()
        }

        fun similarity(a: FloatArray, b: FloatArray): Double =
            a.indices.sumOf { a[it].toDouble() * b[it] }

        fun normalize(values: List<Double>): List<Double> {
            val min = values.min()
            val range = values.max() - min
            return if (range == 0.0) values.map { 0.0 } else values.map { (it - min) / range }
        }

        val pairs = smoke.getJSONArray("similarity_tests")
        val expectedPairs = (0 until pairs.length()).sortedByDescending {
            pairs.getJSONObject(it).getDouble("expected_cosine_similarity")
        }
        val actualPairs = (0 until pairs.length()).sortedByDescending {
            val pair = pairs.getJSONObject(it)
            similarity(
                vectors.getValue(pair.getString("query_id")),
                vectors.getValue(pair.getString("passage_id"))
            )
        }
        assertEquals("Similarity pair ordering", expectedPairs, actualPairs)

        val rankings = smoke.getJSONArray("hybrid_ranking_tests")
        for (index in 0 until rankings.length()) {
            val test = rankings.getJSONObject(index)
            val ids = test.getJSONArray("passage_ids")
            val bm25 = test.getJSONArray("bm25_raw_scores")
            val query = vectors.getValue(test.getString("query_id"))
            val names = (0 until ids.length()).map { ids.getString(it) }
            val dense = normalize(names.map { similarity(query, vectors.getValue(it)) })
            val lexical = normalize((0 until bm25.length()).map { bm25.getDouble(it) })
            val actual = names.indices.sortedByDescending {
                test.getDouble("dense_weight") * dense[it] + test.getDouble("bm25_weight") * lexical[it]
            }.map { names[it] }
            val expected = test.getJSONArray("expected_ranking")
            assertEquals(
                "Hybrid ranking: ${test.getString("query_id")}",
                (0 until expected.length()).map { expected.getString(it) },
                actual
            )
        }
    }
}
