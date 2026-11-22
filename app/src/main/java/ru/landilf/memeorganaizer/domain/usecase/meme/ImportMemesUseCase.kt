package ru.landilf.memeorganaizer.domain.usecase.meme

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.landilf.memeorganaizer.domain.model.importing.ImportProgress
import ru.landilf.memeorganaizer.domain.model.importing.ImportSource
import ru.landilf.memeorganaizer.domain.repository.MemeRepository
import javax.inject.Inject

class ImportMemesUseCase @Inject constructor(
    private val memeRepository: MemeRepository
) {
    /** Each collection starts a new import; cancellation stops the current batch. */
    operator fun invoke(sources: List<ImportSource>): Flow<ImportProgress> {
        val pendingSources = sources.toList()
        return flow {
            emit(ImportProgress(total = pendingSources.size, completed = 0))
            pendingSources.forEachIndexed { index, source ->
                val result = memeRepository.importMeme(source)
                emit(
                    ImportProgress(
                        total = pendingSources.size,
                        completed = index + 1,
                        latestSource = source,
                        latestResult = result
                    )
                )
            }
        }
    }
}
