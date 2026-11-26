package ru.landilf.memeorganizer.domain.usecase.album

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.repository.AlbumRepository
import javax.inject.Inject

class ObserveAlbumMemesUseCase @Inject constructor(
    private val repository: AlbumRepository
) {
    operator fun invoke(albumId: Long): Flow<List<Meme>> = repository.observeMemes(albumId)
}
