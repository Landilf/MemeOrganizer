package ru.landilf.memeorganizer.data.storage.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.repository.MemeExportRepository
import java.io.File
import javax.inject.Inject

class MemeExportRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : MemeExportRepository {
    override suspend fun export(meme: Meme, destination: Uri) = withContext(Dispatchers.IO) {
        val source = File(File(context.filesDir, "memes"), meme.storedFileName)
        check(source.isFile) { "Media file is missing" }
        context.contentResolver.openOutputStream(destination, "w")?.buffered()?.use { output ->
            source.inputStream().buffered().use { input -> input.copyTo(output) }
        } ?: error("Cannot open export destination")
        Unit
    }
}
