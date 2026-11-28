package ru.landilf.memeorganizer.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val memeDao: MemeDao
) {
    suspend fun generateMissing() = withContext(Dispatchers.IO) {
        for (meme in memeDao.getAll()) {
            ensure(meme)
        }
    }

    suspend fun ensure(meme: MemeEntity): Boolean = withContext(Dispatchers.IO) {
        val target = thumbnailFile(meme.storedFileName)
        if (target.isFile && target.length() > 0L) return@withContext true

        val source = File(context.filesDir, "memes/${meme.storedFileName}")
        if (!source.isFile) return@withContext false
        val bitmap = runCatching {
            if (meme.mimeType.startsWith("video/")) {
                extractVideoFrame(source)
            } else {
                decodeImage(source)
            }
        }.getOrNull() ?: return@withContext false

        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        try {
            temporary.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output))
            }
            if (target.exists() && !target.delete()) return@withContext false
            temporary.renameTo(target)
        } finally {
            temporary.delete()
            bitmap.recycle()
        }
    }

    suspend fun delete(storedFileName: String) = withContext(Dispatchers.IO) {
        thumbnailFile(storedFileName).delete()
    }

    private fun thumbnailFile(storedFileName: String) =
        File(context.filesDir, "thumbnails/$storedFileName.webp")

    private fun extractVideoFrame(source: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    0,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_SIZE_PX,
                    THUMBNAIL_SIZE_PX
                )
            } else {
                retriever.getFrameAtTime(0)
            }
        } finally {
            retriever.release()
        }
    }

    private fun decodeImage(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (
            bounds.outWidth / sample > THUMBNAIL_SIZE_PX ||
            bounds.outHeight / sample > THUMBNAIL_SIZE_PX
        ) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            source.path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private companion object {
        const val THUMBNAIL_SIZE_PX = 384
    }
}
