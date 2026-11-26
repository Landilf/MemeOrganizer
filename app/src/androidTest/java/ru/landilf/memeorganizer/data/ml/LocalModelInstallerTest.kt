package ru.landilf.memeorganizer.data.ml

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPOutputStream

@RunWith(AndroidJUnit4::class)
class LocalModelInstallerTest {
    @Test
    fun installsValidPackageAndRejectsUnsafeArchive() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val testDir = File(base.cacheDir, "installer-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : android.content.ContextWrapper(base) {
            override fun getFilesDir() = testDir
        }
        val installer = LocalModelInstaller(context)
        val hash = MessageDigest.getInstance("SHA-256").digest("model".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val manifest =
            """{"schema_version":"1.1.0","component":"text_encoder","model_file":"text_encoder_int8.onnx","embedding_dimension":384,"max_sequence_length":64,"tokenizer":{"recommended_asset":"tokenizer/tokenizer.json","max_length":64,"padding":"max_length","truncation":true,"special_token_ids":{"pad":1}},"prefixes":{"query":"query: ","passage":"passage: "},"hybrid_scoring":{"dense_weight":0.501,"bm25_weight":0.499},"provenance":{"onnx_sha256":"$hash"}}"""

        fun archive(name: String, files: Map<String, String>): File {
            val archive = File(testDir, name)
            TarArchiveOutputStream(GZIPOutputStream(archive.outputStream())).use { tar ->
                files.forEach { (path, content) ->
                    val bytes = content.toByteArray()
                    tar.putArchiveEntry(TarArchiveEntry(path).apply { size = bytes.size.toLong() })
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
            }
            return archive
        }
        try {
            val valid = archive(
                "valid.tgz",
                mapOf(
                    "meme-organizer-text-champion/preset_manifest.json" to manifest,
                    "meme-organizer-text-champion/text_encoder_int8.onnx" to "model",
                    "meme-organizer-text-champion/tokenizer/tokenizer.json" to "{}"
                )
            )
            installer.install(Uri.fromFile(valid))
            assertEquals(hash, installer.activePackage()?.modelSha256)
            val unsafe =
                archive("unsafe.tgz", mapOf("meme-organizer-text-champion/../../outside" to "bad"))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    installer.install(
                        Uri.fromFile(
                            unsafe
                        )
                    )
                }
            }
            assertEquals(hash, installer.activePackage()?.modelSha256)
        } finally {
            testDir.deleteRecursively()
        }
    }
}
