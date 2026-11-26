package ru.landilf.memeorganizer.domain.usecase.meme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.landilf.memeorganizer.domain.model.importing.ImportResult
import ru.landilf.memeorganizer.domain.model.importing.ImportSource
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.meme.MemeMediaType
import ru.landilf.memeorganizer.domain.repository.MemeRepository
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMemesUseCaseTest {
    @Test
    fun `observes initial library and subsequent changes`() = runTest {
        val meme =
            Meme(1L, "meme.png", MemeMediaType.Image, "image/png", Instant.EPOCH, "hash", "file")
        val library = MutableStateFlow<List<Meme>>(emptyList())
        val repository = object : MemeRepository {
            override fun observeMemes() = library
            override suspend fun importMeme(source: ImportSource): ImportResult =
                error("Unexpected import")
        }
        val observed = mutableListOf<List<Meme>>()
        val job = launch { ObserveMemesUseCase(repository)().take(2).toList(observed) }

        runCurrent()
        library.value = listOf(meme)
        job.join()

        assertEquals(listOf(emptyList<Meme>(), listOf(meme)), observed)
    }
}
