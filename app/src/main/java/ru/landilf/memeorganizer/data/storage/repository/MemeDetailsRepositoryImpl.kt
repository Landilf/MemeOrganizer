package ru.landilf.memeorganizer.data.storage.repository

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.data.storage.database.AppDatabase
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeTagEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.TagEntity
import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import ru.landilf.memeorganizer.domain.model.meme.MemeTextRules
import ru.landilf.memeorganizer.domain.model.meme.TagSuggestion
import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import ru.landilf.memeorganizer.core.logging.LightLogStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class MemeDetailsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val lightLog: LightLogStore
) : MemeDetailsRepository {
    private val memes get() = database.memeDao()
    private val tags get() = database.tagDao()

    override fun observeTags() =
        tags.observeTags().map { list -> list.map { TagSuggestion(it.name, it.usageCount) } }

    override suspend fun load(memeId: Long): MemeDetails = database.withTransaction {
        val meme = requireNotNull(memes.getById(memeId))
        MemeDetails(meme.description, tags.getMemeTags(memeId))
    }

    override suspend fun save(memeId: Long, details: MemeDetails) {
        val normalizedTags = details.tags.map(String::trim)
        val hash = database.withTransaction {
            MemeTextRules.validate(details.description, normalizedTags)
            val meme = requireNotNull(memes.getById(memeId))
            check(memes.updateDescription(memeId, details.description) == 1)
            tags.clearLinks(memeId)
            normalizedTags.forEach { name ->
                val normalized = MemeTextRules.normalizeTag(name)
                val existing = tags.findByName(normalized)
                val id = existing?.id ?: tags.insertTag(
                    TagEntity(
                        name = name,
                        normalizedName = normalized
                    )
                ).let {
                    if (it == -1L) requireNotNull(tags.findByName(normalized)).id else it
                }
                tags.insertLink(MemeTagEntity(memeId, id))
            }
            meme.contentHash
        }
        lightLog.record(
            "meme_annotation",
            JSONObject()
                .put("file_hash", hash)
                .put("description", details.description)
                .put("tags", JSONArray(normalizedTags))
                .put("annotation_source", "user")
        )
    }

    override suspend fun delete(memeIds: Set<Long>) = withContext(Dispatchers.IO) {
        for (id in memeIds) {
            val meme = memes.getById(id) ?: continue
            val file = File(File(context.filesDir, "memes"), meme.storedFileName)
            val pending = File(file.parentFile, ".delete-${java.util.UUID.randomUUID()}")
            val moved = file.exists()
            if (moved && !file.renameTo(pending)) throw java.io.IOException("Cannot remove file")
            try {
                database.withTransaction { check(memes.deleteById(id) == 1) }
            } catch (error: Exception) {
                if (moved && !pending.renameTo(file)) error.addSuppressed(java.io.IOException("Cannot restore file"))
                throw error
            }
            if (moved && !pending.delete()) throw java.io.IOException("Cannot remove file copy")
        }
    }
}
