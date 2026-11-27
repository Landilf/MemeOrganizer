package ru.landilf.memeorganizer.domain.usecase.meme

import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import javax.inject.Inject

class SetMemeFavoriteUseCase @Inject constructor(
    private val repository: MemeDetailsRepository
) {
    suspend operator fun invoke(memeId: Long, isFavorite: Boolean) {
        repository.setFavorite(memeId, isFavorite)
    }
}
