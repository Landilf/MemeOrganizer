package ru.landilf.memeorganizer.domain.usecase.album

import ru.landilf.memeorganizer.domain.repository.AlbumRepository
import javax.inject.Inject

class DeleteAlbumUseCase @Inject constructor(
    private val repository: AlbumRepository
) {
    suspend operator fun invoke(albumId: Long) {
        repository.delete(albumId)
    }
}
