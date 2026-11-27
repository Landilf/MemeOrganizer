package ru.landilf.memeorganizer.domain.usecase.meme

import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import ru.landilf.memeorganizer.domain.model.meme.MemeTextRules
import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import javax.inject.Inject

class SaveMemeDetailsUseCase @Inject constructor(private val repository: MemeDetailsRepository) {
    suspend operator fun invoke(memeId: Long, details: MemeDetails) {
        MemeTextRules.validate(details.description, details.tags)
        repository.save(memeId, details.copy(tags = details.tags.map(String::trim)))
    }
}
