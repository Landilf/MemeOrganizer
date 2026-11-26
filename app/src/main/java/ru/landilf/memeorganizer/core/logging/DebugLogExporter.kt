package ru.landilf.memeorganizer.core.logging

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.landilf.memeorganizer.BuildConfig
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.dao.LightLogDao
import ru.landilf.memeorganizer.data.ml.LocalModelInstaller
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugLogExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val memeDao: MemeDao,
    private val lightLogDao: LightLogDao,
    private val lightLog: LightLogStore,
    private val installer: LocalModelInstaller
) {
    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        lightLog.clear()
        AppLogger.clearPersistedEvents()
    }

    suspend fun exportLogs(destination: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.buffered()?.use { output ->
            GZIPOutputStream(output).bufferedWriter().use { writer ->
                writer.appendLine(header(containsMedia = false))
                writeLightLogEvents(writer)
            }
        } ?: error("Cannot open export destination")
    }

    suspend fun exportFullBundle(destination: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.buffered()?.use { output ->
            ZipOutputStream(output).use { zip ->
                val exportHeader = header(containsMedia = true)
                zip.writeLinesEntry("events.jsonl") { writer ->
                    writer.appendLine(exportHeader)
                    writeLightLogEvents(writer)
                }
                zip.writeFileEntry("technical_events.jsonl", eventsFile())
                val metadata = StringBuilder()
                memeDao.getAll().forEach { meme ->
                    val media = File(File(context.filesDir, "memes"), meme.storedFileName)
                    metadata.append(
                        JSONObject()
                            .put("meme_id", meme.id)
                            .put("file_hash", meme.contentHash)
                            .put("file_name", meme.fileName)
                            .put("mime_type", meme.mimeType)
                            .put("included", media.isFile)
                            .toString()
                    )
                        .append('\n')
                    if (media.isFile) {
                        zip.putNextEntry(ZipEntry("media/${meme.contentHash}"))
                        media.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                zip.writeTextEntry("memes.jsonl", metadata.toString())
            }
        } ?: error("Cannot open export destination")
    }

    private suspend fun header(containsMedia: Boolean): String {
        val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
        val memory = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val model = installer.activePackage()
        val deviceInfo = JSONObject()
            .put("manufacturer", android.os.Build.MANUFACTURER)
            .put("model", android.os.Build.MODEL)
            .put("device", android.os.Build.DEVICE)
            .put("android_release", android.os.Build.VERSION.RELEASE)
            .put("android_sdk", android.os.Build.VERSION.SDK_INT)
            .put("supported_abis", org.json.JSONArray(android.os.Build.SUPPORTED_ABIS))
            .put("cpu_cores", Runtime.getRuntime().availableProcessors())
            .put("total_ram_mb", memory.totalMem / (1024 * 1024))
            .put("memory_class_mb", activityManager.memoryClass)
            .put("available_storage_mb", context.filesDir.usableSpace / (1024 * 1024))
            .put("execution_provider", "CPU")
        return JSONObject()
            .put("schema_version", 1)
            .put("record_type", "export_header")
            .put("exported_at_ms", System.currentTimeMillis())
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("contains_media", containsMedia)
            .put("device_info", deviceInfo)
            .put(
                "model", JSONObject()
                    .put("release_tag", model?.releaseTag ?: JSONObject.NULL)
                    .put("onnx_sha256", model?.modelSha256 ?: JSONObject.NULL)
            )
            .put("logging_health", lightLog.healthJson())
            .toString()
    }

    private fun eventsFile() = File(context.filesDir, "debug/events.jsonl")

    private suspend fun writeLightLogEvents(writer: Appendable) {
        var afterId = 0L
        do {
            val events = lightLogDao.getAfterId(afterId, EXPORT_PAGE_SIZE)
            events.forEach { writer.appendLine(it.payloadJson) }
            afterId = events.lastOrNull()?.id ?: afterId
        } while (events.size == EXPORT_PAGE_SIZE)
    }

    private fun ZipOutputStream.writeTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private suspend fun ZipOutputStream.writeLinesEntry(
        name: String,
        write: suspend (BufferedWriter) -> Unit
    ) {
        putNextEntry(ZipEntry(name))
        try {
            val writer = BufferedWriter(OutputStreamWriter(this, Charsets.UTF_8))
            write(writer)
            writer.flush()
        } finally {
            closeEntry()
        }
    }

    private fun ZipOutputStream.writeFileEntry(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        try {
            if (file.isFile) file.inputStream().buffered().use { it.copyTo(this) }
        } finally {
            closeEntry()
        }
    }

    private companion object {
        const val EXPORT_PAGE_SIZE = 250
    }
}
