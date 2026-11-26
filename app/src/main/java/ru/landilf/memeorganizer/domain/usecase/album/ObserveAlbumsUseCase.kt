package ru.landilf.memeorganizer.domain.usecase.album

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.album.Album
import ru.landilf.memeorganizer.domain.repository.AlbumRepository
import javax.inject.Inject

class ObserveAlbumsUseCase @Inject constructor(
    private val repository: AlbumRepository
) {
    operator fun invoke(): Flow<List<Album>> = repository.observeAlbums()
}
