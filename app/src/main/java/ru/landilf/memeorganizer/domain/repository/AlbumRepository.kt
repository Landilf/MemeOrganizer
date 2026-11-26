package ru.landilf.memeorganizer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.album.Album

interface AlbumRepository {
    fun observeAlbums(): Flow<List<Album>>
    suspend fun create(name: String): Boolean
}
