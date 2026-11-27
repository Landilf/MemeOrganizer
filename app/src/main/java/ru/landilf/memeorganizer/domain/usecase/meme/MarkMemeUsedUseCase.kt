package ru.landilf.memeorganizer.domain.usecase.meme

import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import javax.inject.Inject

class MarkMemeUsedUseCase @Inject constructor(
    private val repository: MemeDetailsRepository
) {
    suspend operator fun invoke(memeId: Long) {
        repository.markUsed(memeId)
    }
}
