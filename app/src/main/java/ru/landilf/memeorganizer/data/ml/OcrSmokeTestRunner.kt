package ru.landilf.memeorganizer.data.ml

import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import javax.inject.Inject
import org.json.JSONObject

sealed interface OcrSmokeTestResult {
    data object Unavailable : OcrSmokeTestResult
    data class Passed(val testCount: Int) : OcrSmokeTestResult
}

class OcrSmokeTestRunner @Inject constructor() {
    suspend fun run(modelPackage: TextModelPackage): OcrSmokeTestResult {
        if (modelPackage.components["ocr_det"] == null) return OcrSmokeTestResult.Unavailable
        val tests = JSONObject(File(modelPackage.modelFile.parentFile, "smoke_test_pack.json").readText())
            .optJSONArray("ocr_tests") ?: return OcrSmokeTestResult.Unavailable
        val engine = OnnxOcrEngine.open(modelPackage) ?: return OcrSmokeTestResult.Unavailable
        try {
            repeat(tests.length()) { index ->
                val test = tests.getJSONObject(index)
                val bytes = Base64.decode(test.getString("image_base64_png"), Base64.DEFAULT)
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                val actual = try { engine.recognize(OcrImageLoader.fromBitmap(bitmap)).joinToString(" ") { it.text } } finally { bitmap.recycle() }
                require(actual == test.getString("expected_text")) { "OCR smoke test $index failed: $actual" }
            }
            return OcrSmokeTestResult.Passed(tests.length())
        } finally { engine.close() }
    }
}
