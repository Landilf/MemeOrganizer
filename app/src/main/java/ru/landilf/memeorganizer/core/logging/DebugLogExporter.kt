package ru.landilf.memeorganizer.core.logging

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.landilf.memeorganizer.BuildConfig
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugLogExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val memeDao: MemeDao
) {
    suspend fun exportLogs(destination: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.buffered()?.use { output ->
            GZIPOutputStream(output).bufferedWriter().use { writer ->
                writer.appendLine(header(containsMedia = false))
                eventsFile().takeIf { it.isFile }?.forEachLine { writer.appendLine(it) }
            }
        } ?: error("Cannot open export destination")
    }

    suspend fun exportFullBundle(destination: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.buffered()?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeTextEntry("events.jsonl", header(containsMedia = true) + "\n" + eventsFile().takeIf { it.isFile }?.readText().orEmpty())
                val metadata = StringBuilder()
                memeDao.getAll().forEach { meme ->
                    val media = File(File(context.filesDir, "memes"), meme.storedFileName)
                    metadata.append(JSONObject()
                        .put("meme_id", meme.id)
                        .put("file_hash", meme.contentHash)
                        .put("file_name", meme.fileName)
                        .put("mime_type", meme.mimeType)
                        .put("included", media.isFile)
                        .toString())
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

    private fun header(containsMedia: Boolean): String = JSONObject()
        .put("record_type", "export_header")
        .put("exported_at_ms", System.currentTimeMillis())
        .put("app_version", BuildConfig.VERSION_NAME)
        .put("contains_media", containsMedia)
        .toString()

    private fun eventsFile() = File(context.filesDir, "debug/events.jsonl")

    private fun ZipOutputStream.writeTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
