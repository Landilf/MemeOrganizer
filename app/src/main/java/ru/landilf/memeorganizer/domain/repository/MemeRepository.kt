package ru.landilf.memeorganizer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.importing.ImportResult
import ru.landilf.memeorganizer.domain.model.importing.ImportSource
import ru.landilf.memeorganizer.domain.model.meme.Meme

interface MemeRepository {
    fun observeMemes(): Flow<List<Meme>>

    /**
     * Copies one source into the library, or reports a duplicate or expected failure.
     * Implementations must clean up incomplete copies on failure or cancellation and
     * prevent duplicate records when imports run concurrently.
     */
    suspend fun importMeme(source: ImportSource): ImportResult
}
