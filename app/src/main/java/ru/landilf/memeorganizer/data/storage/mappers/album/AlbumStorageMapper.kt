package ru.landilf.memeorganizer.data.storage.mappers.album

import ru.landilf.memeorganizer.data.storage.entities.album.AlbumEntity
import ru.landilf.memeorganizer.domain.model.album.Album
import java.time.Instant
import javax.inject.Inject

class AlbumStorageMapper @Inject constructor() {
    fun toDomain(entity: AlbumEntity) = Album(
        id = entity.id,
        name = entity.name,
        createdAt = Instant.ofEpochMilli(entity.createdAtMillis)
    )
}
