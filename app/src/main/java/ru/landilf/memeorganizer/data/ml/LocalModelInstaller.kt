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
import ru.landilf.memeorganizer.core.logging.AppLogger
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
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
        try {
            TextModelPackage.load(File(directory, id)).also { model ->
                AppLogger.ml { "model.active tag=${model.releaseTag} sha=${model.modelSha256.take(12)} dim=${model.dimension}" }
            }
        } catch (error: Exception) {
            pointer.delete()
            File(directory, id).deleteRecursively()
            AppLogger.ml { "model.active.invalid type=${error.javaClass.simpleName}" }
            null
        }
    }

    suspend fun install(uri: Uri): TextModelPackage = withContext(Dispatchers.IO) {
        val input =
            context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read archive")
        installArchive(input, expectedArchiveSha256 = null, onArchiveReadFinished = {})
    }

    suspend fun installDownloaded(
        input: InputStream,
        expectedArchiveSha256: String,
        onArchiveReadFinished: () -> Unit
    ): TextModelPackage = withContext(Dispatchers.IO) {
        installArchive(input, expectedArchiveSha256, onArchiveReadFinished)
    }

    private suspend fun installArchive(
        input: InputStream,
        expectedArchiveSha256: String?,
        onArchiveReadFinished: () -> Unit
    ): TextModelPackage {
        AppLogger.ml { "model.install.start" }
        require(expectedArchiveSha256 == null || expectedArchiveSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid archive SHA-256" }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create model directory")
        val id = UUID.randomUUID().toString()
        val staged = File(directory, id)
        if (!staged.mkdir()) throw IOException("Cannot create model staging directory")
        var activated = false
        try {
            val allowed = setOf(
                "preset_manifest.json",
                "text_encoder_int8.onnx",
                "smoke_test_pack.json",
                "README.md",
                "tokenizer/tokenizer.json",
                "tokenizer/tokenizer_config.json"
            )
            val seen = mutableSetOf<String>()
            var total = 0L
            val archiveDigest = expectedArchiveSha256?.let { MessageDigest.getInstance("SHA-256") }
            input.use { source ->
                val raw = archiveDigest?.let { DigestInputStream(source, it) } ?: source
                GZIPInputStream(raw.buffered()).use { gzip ->
                    TarArchiveInputStream(gzip).use { tar ->
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
                    }
                }
            }
            if (archiveDigest != null) {
                val actual = archiveDigest.digest().joinToString("") { "%02x".format(it) }
                require(
                    actual.equals(
                        expectedArchiveSha256,
                        ignoreCase = true
                    )
                ) { "Archive SHA-256 mismatch" }
            }
            onArchiveReadFinished()
            val model = TextModelPackage.load(staged)
            currentCoroutineContext().ensureActive()
            val output = pointer.startWrite()
            try {
                output.write(id.toByteArray(Charsets.UTF_8)); pointer.finishWrite(output)
            } catch (e: Exception) {
                pointer.failWrite(output); throw e
            }
            activated = true
            removeInactivePackages(activeId = id)
            AppLogger.ml {
                "model.install.done tag=${model.releaseTag} sha=${
                    model.modelSha256.take(
                        12
                    )
                } dim=${model.dimension}"
            }
            return model
        } finally {
            if (!activated) staged.deleteRecursively()
        }
    }

    private fun removeInactivePackages(activeId: String) {
        val packageId = Regex("[0-9a-f-]{36}")
        directory.listFiles()
            ?.filter { it.isDirectory && it.name != activeId && packageId.matches(it.name) }
            ?.forEach { oldPackage ->
                if (oldPackage.deleteRecursively()) AppLogger.ml { "model.cleanup.removed package=${oldPackage.name}" }
                else AppLogger.ml { "model.cleanup.failed package=${oldPackage.name}" }
            }
    }
}
