package ru.landilf.memeorganizer.domain.usecase.album

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import ru.landilf.memeorganizer.domain.model.album.Album
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.repository.AlbumRepository

class AlbumUseCasesTest {
    @Test
    fun `rename trims a valid album name`() = runTest {
        val repository = FakeAlbumRepository()

        val renamed = RenameAlbumUseCase(repository)(12L, "  Реакции  ")

        assertEquals(true, renamed)
        assertEquals(12L to "Реакции", repository.renamed)
    }

    @Test
    fun `rename rejects a blank album name`() = runTest {
        try {
            RenameAlbumUseCase(FakeAlbumRepository())(12L, "  ")
            fail("Expected blank album name to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `remove use case delegates selected meme ids`() = runTest {
        val repository = FakeAlbumRepository()

        RemoveMemesFromAlbumUseCase(repository)(4L, setOf(3L, 8L))

        assertEquals(4L to setOf(3L, 8L), repository.removed)
    }

    @Test
    fun `delete use case delegates album id`() = runTest {
        val repository = FakeAlbumRepository()

        DeleteAlbumUseCase(repository)(7L)

        assertEquals(7L, repository.deletedAlbumId)
    }

    private class FakeAlbumRepository : AlbumRepository {
        var renamed: Pair<Long, String>? = null
        var removed: Pair<Long, Set<Long>>? = null
        var deletedAlbumId: Long? = null

        override fun observeAlbums(): Flow<List<Album>> = emptyFlow()
        override fun observeMemes(albumId: Long): Flow<List<Meme>> = emptyFlow()
        override suspend fun create(name: String): Boolean = true
        override suspend fun rename(albumId: Long, name: String): Boolean {
            renamed = albumId to name
            return true
        }

        override suspend fun delete(albumId: Long) {
            deletedAlbumId = albumId
        }
        override suspend fun addMemes(albumId: Long, memeIds: Set<Long>) = Unit
        override suspend fun removeMemes(albumId: Long, memeIds: Set<Long>) {
            removed = albumId to memeIds
        }
    }
}
