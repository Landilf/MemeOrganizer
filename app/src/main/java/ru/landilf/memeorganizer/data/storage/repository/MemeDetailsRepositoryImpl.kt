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
import java.io.File
import javax.inject.Inject

class MemeDetailsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase
) : MemeDetailsRepository {
    private val memes get() = database.memeDao()
    private val tags get() = database.tagDao()

    override fun observeTags() = tags.observeTags().map { list -> list.map { TagSuggestion(it.name, it.usageCount) } }
    override suspend fun load(memeId: Long): MemeDetails = database.withTransaction {
        val meme = requireNotNull(memes.getById(memeId))
        MemeDetails(meme.description, tags.getMemeTags(memeId))
    }
    override suspend fun save(memeId: Long, details: MemeDetails) = database.withTransaction {
        MemeTextRules.validate(details.description, details.tags)
        check(memes.updateDescription(memeId, details.description) == 1)
        tags.clearLinks(memeId)
        details.tags.forEach { name ->
            val normalized = MemeTextRules.normalizeTag(name)
            val existing = tags.findByName(normalized)
            val id = existing?.id ?: tags.insertTag(TagEntity(name = name.trim(), normalizedName = normalized)).let {
                if (it == -1L) requireNotNull(tags.findByName(normalized)).id else it
            }
            tags.insertLink(MemeTagEntity(memeId, id))
        }
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
