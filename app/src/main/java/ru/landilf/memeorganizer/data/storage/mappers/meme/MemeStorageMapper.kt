package ru.landilf.memeorganizer.data.storage.mappers.meme

import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.meme.MemeMediaType
import java.time.Instant
import javax.inject.Inject

class MemeStorageMapper @Inject constructor() {
    fun toDomain(entity: MemeEntity): Meme = Meme(
        id = entity.id,
        fileName = entity.fileName,
        mediaType = MemeMediaType.valueOf(entity.mediaType),
        mimeType = entity.mimeType,
        addedAt = Instant.ofEpochMilli(entity.addedAtMillis),
        contentHash = entity.contentHash,
        storedFileName = entity.storedFileName
    )
}
