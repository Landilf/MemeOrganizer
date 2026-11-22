package ru.landilf.memeorganaizer.domain.usecase.meme

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import ru.landilf.memeorganaizer.domain.model.importing.ImportFailure
import ru.landilf.memeorganaizer.domain.model.importing.ImportResult
import ru.landilf.memeorganaizer.domain.model.importing.ImportSource
import ru.landilf.memeorganaizer.domain.model.meme.Meme
import ru.landilf.memeorganaizer.domain.model.meme.MemeMediaType
import ru.landilf.memeorganaizer.domain.repository.MemeRepository
import java.time.Instant

class ImportMemesUseCaseTest {
    @Test
    fun `reports progress and continues after a duplicate and an expected failure`() = runTest {
        val meme = Meme("1", "meme.png", MemeMediaType.Image, "image/png", Instant.EPOCH, "hash")
        val results = listOf(
            ImportResult.Imported(meme),
            ImportResult.Duplicate(meme),
            ImportResult.Failed(ImportFailure.SourceUnavailable)
        )
        val sources = (1..3).map { ImportSource("source-$it") }
        val received = mutableListOf<ImportSource>()
        val repository = object : MemeRepository {
            override fun observeMemes() = flowOf(emptyList<Meme>())
            override suspend fun importMeme(source: ImportSource): ImportResult {
                received.add(source)
                return results[received.lastIndex]
            }
        }

        val progress = ImportMemesUseCase(repository)(sources).toList()

        assertEquals(sources, received)
        assertEquals(listOf(0, 1, 2, 3), progress.map { it.completed })
        assertEquals(listOf(null) + results, progress.map { it.latestResult })
        assertEquals(listOf(null) + sources, progress.map { it.latestSource })
        assertEquals(listOf(3, 3, 3, 3), progress.map { it.total })
    }

    @Test
    fun `empty batch reports zero progress without importing`() = runTest {
        val repository = object : MemeRepository {
            override fun observeMemes() = flowOf(emptyList<Meme>())
            override suspend fun importMeme(source: ImportSource): ImportResult = error("Unexpected import")
        }

        val progress = ImportMemesUseCase(repository)(emptyList()).toList()

        assertEquals(1, progress.size)
        assertEquals(0, progress.single().total)
        assertEquals(0, progress.single().completed)
    }

    @Test
    fun `cancellation stops the batch and is propagated`() = runTest {
        val sources = listOf(ImportSource("first"), ImportSource("second"))
        val received = mutableListOf<ImportSource>()
        val repository = object : MemeRepository {
            override fun observeMemes() = flowOf(emptyList<Meme>())
            override suspend fun importMeme(source: ImportSource): ImportResult {
                received.add(source)
                throw CancellationException("Import cancelled")
            }
        }

        try {
            ImportMemesUseCase(repository)(sources).toList()
            fail("Expected cancellation")
        } catch (exception: CancellationException) {
            assertEquals("Import cancelled", exception.message)
        }

        assertEquals(listOf(sources.first()), received)
    }
}
