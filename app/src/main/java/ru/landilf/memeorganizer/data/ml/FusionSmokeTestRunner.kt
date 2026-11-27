package ru.landilf.memeorganizer.data.ml

import java.io.File
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject
import ru.landilf.memeorganizer.domain.model.ml.FusionInput
import kotlin.math.abs

sealed interface FusionSmokeTestResult {
    data object Unavailable : FusionSmokeTestResult
    data class Passed(val testCount: Int) : FusionSmokeTestResult
}

/** Runs the package-provided fusion fixtures on-device without affecting the library index. */
class FusionSmokeTestRunner @Inject constructor() {
    suspend fun run(modelPackage: TextModelPackage): FusionSmokeTestResult {
        if (modelPackage.components[FUSION_COMPONENT] == null) return FusionSmokeTestResult.Unavailable
        val smokeTests = JSONObject(File(modelPackage.modelFile.parentFile, SMOKE_TEST_FILE).readText())
            .optJSONArray(FUSION_TESTS) ?: return FusionSmokeTestResult.Unavailable
        require(smokeTests.length() > 0) { "Fusion smoke tests are empty" }
        OnnxFusionEngine.open(modelPackage)?.use { engine ->
            repeat(smokeTests.length()) { testIndex ->
                val test = smokeTests.getJSONObject(testIndex)
                val input = test.toFusionInput()
                val expected = test.getJSONArray(EXPECTED_FUSED_SCORES).singleRow().toFloatArray()
                val actual = engine.fuse(input)
                val tolerance = test.getDouble(ABSOLUTE_TOLERANCE).toFloat()
                require(expected.size == actual.size)
                expected.indices.forEach { index ->
                    require(abs(expected[index] - actual[index]) <= tolerance) {
                        "Fusion smoke test $testIndex failed at score $index"
                    }
                }
            }
            return FusionSmokeTestResult.Passed(smokeTests.length())
        }
        return FusionSmokeTestResult.Unavailable
    }

    private fun JSONObject.toFusionInput(): FusionInput = FusionInput(
        queryEmbedding = getJSONArray(QUERY_EMBEDDING).toFloatArray(),
        channelScores = getJSONArray(CHANNEL_SCORES).singleRow().rowsToFloatArrays(),
        channelMask = getJSONArray(CHANNEL_MASK).rowsToBooleanArrays()
    )

    private fun JSONArray.singleRow(): JSONArray {
        require(length() == 1) { "Only one query is supported by the smoke test runner" }
        return getJSONArray(0)
    }

    private fun JSONArray.rowsToFloatArrays(): List<FloatArray> = List(length()) { index ->
        getJSONArray(index).toFloatArray()
    }

    private fun JSONArray.rowsToBooleanArrays(): List<BooleanArray> = List(length()) { index ->
        getJSONArray(index).let { row -> BooleanArray(row.length()) { row.getBoolean(it) } }
    }

    private fun JSONArray.toFloatArray(): FloatArray = FloatArray(length()) { index ->
        getDouble(index).toFloat()
    }

    private companion object {
        const val FUSION_COMPONENT = "fusion"
        const val SMOKE_TEST_FILE = "smoke_test_pack.json"
        const val FUSION_TESTS = "fusion_tests"
        const val QUERY_EMBEDDING = "query_emb"
        const val CHANNEL_SCORES = "channel_scores"
        const val CHANNEL_MASK = "channel_mask"
        const val EXPECTED_FUSED_SCORES = "expected_fused_scores"
        const val ABSOLUTE_TOLERANCE = "atol"
    }
}
