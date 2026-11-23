package ru.landilf.memeorganizer.data.storage.repository

import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.data.storage.mappers.meme.MemeStorageMapper
import ru.landilf.memeorganizer.domain.model.importing.ImportFailure
import ru.landilf.memeorganizer.domain.model.importing.ImportResult
import ru.landilf.memeorganizer.domain.model.importing.ImportSource
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.meme.MemeMediaType
import ru.landilf.memeorganizer.domain.repository.MemeRepository
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

class MemeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memeDao: MemeDao,
    private val mapper: MemeStorageMapper
) : MemeRepository {
    override fun observeMemes(): Flow<List<Meme>> =
        memeDao.observeMemes().map { entities -> entities.map(mapper::toDomain) }

    override suspend fun importMeme(source: ImportSource): ImportResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(source.identifier)
        val resolver = context.contentResolver
        val fileName = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "meme"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "meme"
        }
        val mimeType = try {
            resolver.getType(uri)
        } catch (_: SecurityException) {
            return@withContext ImportResult.Failed(ImportFailure.SourceUnavailable)
        } ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            fileName.substringAfterLast('.', "").lowercase()
        ) ?: return@withContext ImportResult.Failed(ImportFailure.UnsupportedMedia)
        val mediaType = when {
            mimeType.equals("image/gif", ignoreCase = true) -> MemeMediaType.Gif
            mimeType.startsWith("image/", ignoreCase = true) -> MemeMediaType.Image
            mimeType.startsWith("video/", ignoreCase = true) -> MemeMediaType.Video
            else -> return@withContext ImportResult.Failed(ImportFailure.UnsupportedMedia)
        }
        val input = try {
            resolver.openInputStream(uri)
        } catch (_: SecurityException) {
            null
        } catch (_: IOException) {
            null
        } ?: return@withContext ImportResult.Failed(ImportFailure.SourceUnavailable)

        val storedFileName = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "memes")
        val target = File(directory, storedFileName)
        var committed = false
        try {
            input.use { stream ->
                if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                    throw IOException("Cannot create media directory")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                val entity = MemeEntity(
                    fileName = fileName, mediaType = mediaType.name,
                    mimeType = mimeType, addedAtMillis = System.currentTimeMillis(),
                    contentHash = hash, storedFileName = storedFileName
                )
                // Finish the database write and ownership transfer even if cancellation arrives.
                withContext(NonCancellable) {
                    val id = memeDao.insert(entity)
                    if (id != -1L) {
                        committed = true
                        ImportResult.Imported(mapper.toDomain(entity.copy(id = id)))
                    } else {
                        val existing = memeDao.findByHash(hash)
                            ?: throw IOException("Conflicting meme was not found")
                        ImportResult.Duplicate(mapper.toDomain(existing))
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val noSpace = exception is SQLiteFullException ||
                generateSequence(exception as Throwable?) { it.cause }
                    .filterIsInstance<ErrnoException>().any { it.errno == OsConstants.ENOSPC }
            ImportResult.Failed(if (noSpace) ImportFailure.InsufficientStorage else ImportFailure.StorageError)
        } finally {
            if (!committed) target.delete()
        }
    }
}
