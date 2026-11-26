package ru.landilf.memeorganizer.data.storage.repository

import kotlinx.coroutines.flow.map
import ru.landilf.memeorganizer.data.storage.dao.AlbumDao
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumEntity
import ru.landilf.memeorganizer.data.storage.mappers.album.AlbumStorageMapper
import ru.landilf.memeorganizer.domain.model.album.AlbumNameRules
import ru.landilf.memeorganizer.domain.repository.AlbumRepository
import javax.inject.Inject

class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val mapper: AlbumStorageMapper
) : AlbumRepository {
    override fun observeAlbums() = albumDao.observeAlbums().map { albums ->
        albums.map(mapper::toDomain)
    }

    override suspend fun create(name: String): Boolean = albumDao.insert(
        AlbumEntity(
            name = name,
            normalizedName = AlbumNameRules.normalize(name),
            createdAtMillis = System.currentTimeMillis()
        )
    ) != -1L
}
