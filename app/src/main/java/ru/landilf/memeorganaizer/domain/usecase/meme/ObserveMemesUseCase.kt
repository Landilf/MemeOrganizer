package ru.landilf.memeorganaizer.domain.usecase.meme

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganaizer.domain.model.meme.Meme
import ru.landilf.memeorganaizer.domain.repository.MemeRepository
import javax.inject.Inject

class ObserveMemesUseCase @Inject constructor(
    private val memeRepository: MemeRepository
) {
    operator fun invoke(): Flow<List<Meme>> = memeRepository.observeMemes()
}
