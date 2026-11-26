package ru.landilf.memeorganizer.presentation.importing

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.landilf.memeorganizer.domain.model.importing.ImportFailure
import ru.landilf.memeorganizer.domain.model.importing.ImportResult
import ru.landilf.memeorganizer.domain.model.importing.ImportSource
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import ru.landilf.memeorganizer.domain.model.meme.TagSuggestion
import ru.landilf.memeorganizer.domain.model.search.SearchPreparation
import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import ru.landilf.memeorganizer.domain.repository.MemeRepository
import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository
import ru.landilf.memeorganizer.domain.usecase.meme.ImportMemesUseCase
import ru.landilf.memeorganizer.domain.usecase.meme.SaveMemeDetailsUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = object : MemeRepository {
        override fun observeMemes() = flowOf(emptyList<Meme>())
        override suspend fun importMeme(source: ImportSource) =
            ImportResult.Failed(ImportFailure.SourceUnavailable)
    }
    private val detailsRepository = object : MemeDetailsRepository {
        override fun observeTags() = flowOf(emptyList<TagSuggestion>())
        override suspend fun load(memeId: Long) = MemeDetails("", emptyList())
        override suspend fun save(memeId: Long, details: MemeDetails) = Unit
        override suspend fun setFavorite(memeId: Long, isFavorite: Boolean) = Unit
        override suspend fun markUsed(memeId: Long) = Unit
        override suspend fun delete(memeIds: Set<Long>) = Unit
    }
    private val searchRepository = object : MemeSearchRepository {
        override suspend fun prepare() = SearchPreparation(false, 0)
        override suspend fun reindexAll() = SearchPreparation(false, 0)
        override suspend fun indexMeme(memeId: Long) = Unit
        override suspend fun search(query: String) = emptyList()
    }

    private fun createViewModel(savedState: SavedStateHandle) = ImportViewModel(
        importMemes = ImportMemesUseCase(repository),
        detailsRepository = detailsRepository,
        saveMemeDetails = SaveMemeDetailsUseCase(detailsRepository),
        searchRepository = searchRepository,
        savedStateHandle = savedState
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selection and removal preserve remaining sources`() {
        val savedState = SavedStateHandle()
        val viewModel = createViewModel(savedState)
        viewModel.setSources(listOf("one", "two", "one"))
        viewModel.toggle("one")
        viewModel.remove(viewModel.state.value.selected)

        assertEquals(listOf("two"), viewModel.state.value.sources)
        assertTrue(viewModel.state.value.selected.isEmpty())
        assertEquals(
            listOf("two"),
            createViewModel(savedState).state.value.sources
        )
    }

    @Test
    fun `failed files remain available for retry`() = runTest(dispatcher) {
        val viewModel = createViewModel(SavedStateHandle())
        viewModel.setSources(listOf("one", "two"))
        viewModel.startImport()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("one", "two"), viewModel.state.value.sources)
        assertEquals(ImportSummary.Completed(0, 0, 2), viewModel.state.value.summary)
    }
}
