package ru.landilf.memeorganizer.domain.usecase.meme

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.repository.MemeRepository
import javax.inject.Inject

class ObserveMemesUseCase @Inject constructor(
    private val memeRepository: MemeRepository
) {
    operator fun invoke(): Flow<List<Meme>> = memeRepository.observeMemes()
}
