package ru.landilf.memeorganizer.data.indexing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import ru.landilf.memeorganizer.data.ml.OcrImageLoader
import ru.landilf.memeorganizer.data.ml.OnnxOcrEngine
import ru.landilf.memeorganizer.data.ml.TextModelPackage
import ru.landilf.memeorganizer.data.storage.dao.ExtractedTextChunkDao
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.data.storage.entities.search.ExtractedTextChunkEntity
import ru.landilf.memeorganizer.domain.model.ml.ExtractedTextSource

@Singleton
class OcrMediaProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val extractedTextChunkDao: ExtractedTextChunkDao
) {
    suspend fun process(meme: MemeEntity, modelPackage: TextModelPackage): Boolean {
        if (!meme.mimeType.startsWith("image/") || meme.mimeType == "image/gif") return false
        val engine = OnnxOcrEngine.open(modelPackage) ?: return false
        try {
            val blocks = engine.recognize(OcrImageLoader.load(File(context.filesDir, "memes/${meme.storedFileName}")))
            extractedTextChunkDao.deleteForMemeAndSource(meme.id, ExtractedTextSource.Ocr.storageValue)
            if (blocks.isNotEmpty()) extractedTextChunkDao.insertAll(
                blocks.map { block ->
                    ExtractedTextChunkEntity(
                        memeId = meme.id,
                        source = ExtractedTextSource.Ocr.storageValue,
                        timestampMs = null,
                        text = block.text,
                        modelReleaseTag = modelPackage.releaseTag,
                        createdAtMillis = System.currentTimeMillis()
                    )
                }
            )
            return true
        } finally {
            engine.close()
        }
    }
}
