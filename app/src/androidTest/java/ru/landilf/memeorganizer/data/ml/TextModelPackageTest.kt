package ru.landilf.memeorganizer.data.ml

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class TextModelPackageTest {
    private lateinit var directory: File

    @Before fun setUp() {
        directory = kotlin.io.path.createTempDirectory(ApplicationProvider.getApplicationContext<Context>().cacheDir.toPath(), "model-test-").toFile()
        File(directory, "model.onnx").writeText("model")
        File(directory, "tokenizer.json").writeText("{}")
    }

    @After fun tearDown() { directory.deleteRecursively() }

    private fun writeManifest(modelPath: String = "model.onnx", hash: String = sha256() ) {
        File(directory, "preset_manifest.json").writeText(
            """{"schema_version":"1.1.0","component":"text_encoder","model_file":"$modelPath","embedding_dimension":384,"max_sequence_length":64,"tokenizer":{"recommended_asset":"tokenizer.json","max_length":64,"padding":"max_length","truncation":true,"special_token_ids":{"pad":1}},"prefixes":{"query":"query: ","passage":"passage: "},"hybrid_scoring":{"dense_weight":0.501,"bm25_weight":0.499},"provenance":{"onnx_sha256":"$hash"}}"""
        )
    }

    private fun sha256(): String = MessageDigest.getInstance("SHA-256").digest("model".toByteArray()).joinToString("") { "%02x".format(it) }

    @Test fun readsValidManifest() {
        writeManifest()
        val model = TextModelPackage.load(directory)
        assertEquals(384, model.dimension)
        assertEquals(64, model.maxLength)
        assertEquals("query: ", model.queryPrefix)
    }

    @Test fun rejectsIncorrectHashAndPathOutsidePackage() {
        writeManifest(hash = "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { TextModelPackage.load(directory) }
        writeManifest(modelPath = "../outside.onnx")
        assertThrows(IllegalArgumentException::class.java) { TextModelPackage.load(directory) }
    }
}
