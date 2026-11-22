package ru.landilf.memeorganaizer.data.storage.mappers.meme

import ru.landilf.memeorganaizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganaizer.domain.model.meme.Meme
import ru.landilf.memeorganaizer.domain.model.meme.MemeMediaType
import java.time.Instant
import javax.inject.Inject

class MemeStorageMapper @Inject constructor() {
    fun toDomain(entity: MemeEntity): Meme = Meme(
        id = entity.id,
        fileName = entity.fileName,
        mediaType = MemeMediaType.valueOf(entity.mediaType),
        mimeType = entity.mimeType,
        addedAt = Instant.ofEpochMilli(entity.addedAtMillis),
        contentHash = entity.contentHash
    )
}
