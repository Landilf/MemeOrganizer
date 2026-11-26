package ru.landilf.memeorganizer.domain.usecase.search

import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository
import javax.inject.Inject

class SearchMemesUseCase @Inject constructor(private val repository: MemeSearchRepository) {
    suspend operator fun invoke(query: String) = repository.search(query)
}
