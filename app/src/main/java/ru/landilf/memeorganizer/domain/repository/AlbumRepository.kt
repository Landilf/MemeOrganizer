package ru.landilf.memeorganizer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.album.Album
import ru.landilf.memeorganizer.domain.model.meme.Meme

interface AlbumRepository {
    fun observeAlbums(): Flow<List<Album>>
    fun observeMemes(albumId: Long): Flow<List<Meme>>
    suspend fun create(name: String): Boolean
    suspend fun addMemes(albumId: Long, memeIds: Set<Long>)
}
