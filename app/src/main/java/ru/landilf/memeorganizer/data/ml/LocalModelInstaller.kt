package ru.landilf.memeorganizer.data.ml

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelInstaller @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val directory get() = File(context.filesDir, "models/text")
    private val pointer get() = AtomicFile(File(directory, "active"))

    suspend fun activePackage(): TextModelPackage? = withContext(Dispatchers.IO) {
        if (!pointer.baseFile.isFile) return@withContext null
        val id = pointer.readFully().toString(Charsets.UTF_8)
        require(id.matches(Regex("[0-9a-f-]{36}")))
        TextModelPackage.load(File(directory, id))
    }

    suspend fun install(uri: Uri): TextModelPackage = withContext(Dispatchers.IO) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create model directory")
        val id = UUID.randomUUID().toString()
        val staged = File(directory, id)
        if (!staged.mkdir()) throw IOException("Cannot create model staging directory")
        var activated = false
        try {
            val allowed = setOf("preset_manifest.json", "text_encoder_int8.onnx", "smoke_test_pack.json", "README.md", "tokenizer/tokenizer.json", "tokenizer/tokenizer_config.json")
            val seen = mutableSetOf<String>()
            var total = 0L
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read archive")
            input.use { raw -> GZIPInputStream(raw.buffered()).use { gzip -> TarArchiveInputStream(gzip).use { tar ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = tar.nextEntry ?: break
                    if (entry.isDirectory) continue
                    require(entry.isFile && !entry.isSymbolicLink && !entry.isLink) { "Unsupported archive entry" }
                    val parts = entry.name.split('/')
                    require(parts.size >= 2 && parts.first() == "meme-organizer-text-champion" && parts.none { it == ".." || it == "." || it.isEmpty() }) { "Invalid archive path" }
                    val relative = parts.drop(1).joinToString("/")
                    require(relative in allowed && seen.add(relative)) { "Unsupported or repeated archive file" }
                    require(entry.size in 0..(200L * 1024 * 1024)) { "Archive file too large" }
                    total += entry.size
                    require(total <= 300L * 1024 * 1024) { "Archive too large" }
                    val output = File(staged, relative)
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = tar.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                        }
                    }
                }
            } } }
            val model = TextModelPackage.load(staged)
            currentCoroutineContext().ensureActive()
            val output = pointer.startWrite()
            try { output.write(id.toByteArray(Charsets.UTF_8)); pointer.finishWrite(output) } catch (e: Exception) { pointer.failWrite(output); throw e }
            activated = true
            model
        } finally {
            if (!activated) staged.deleteRecursively()
        }
    }
}
