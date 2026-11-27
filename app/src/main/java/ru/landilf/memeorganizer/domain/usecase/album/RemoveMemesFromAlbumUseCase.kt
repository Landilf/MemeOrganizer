package ru.landilf.memeorganizer.domain.usecase.album

import ru.landilf.memeorganizer.domain.repository.AlbumRepository
import javax.inject.Inject

class RemoveMemesFromAlbumUseCase @Inject constructor(
    private val repository: AlbumRepository
) {
    suspend operator fun invoke(albumId: Long, memeIds: Set<Long>) {
        repository.removeMemes(albumId, memeIds)
    }
}
