package ru.landilf.memeorganizer.domain.usecase.album

import ru.landilf.memeorganizer.domain.model.album.AlbumNameRules
import ru.landilf.memeorganizer.domain.repository.AlbumRepository
import javax.inject.Inject

class RenameAlbumUseCase @Inject constructor(
    private val repository: AlbumRepository
) {
    suspend operator fun invoke(albumId: Long, name: String): Boolean {
        val trimmedName = name.trim()
        AlbumNameRules.validate(trimmedName)
        return repository.rename(albumId, trimmedName)
    }
}
