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
import ru.landilf.memeorganizer.domain.repository.MemeRepository
import ru.landilf.memeorganizer.domain.usecase.meme.ImportMemesUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = object : MemeRepository {
        override fun observeMemes() = flowOf(emptyList<Meme>())
        override suspend fun importMeme(source: ImportSource) =
            ImportResult.Failed(ImportFailure.SourceUnavailable)
    }

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
        val viewModel = ImportViewModel(ImportMemesUseCase(repository), savedState)
        viewModel.setSources(listOf("one", "two", "one"))
        viewModel.toggle("one")
        viewModel.remove(viewModel.state.value.selected)

        assertEquals(listOf("two"), viewModel.state.value.sources)
        assertTrue(viewModel.state.value.selected.isEmpty())
        assertEquals(
            listOf("two"),
            ImportViewModel(ImportMemesUseCase(repository), savedState).state.value.sources
        )
    }

    @Test
    fun `failed files remain available for retry`() = runTest(dispatcher) {
        val viewModel = ImportViewModel(ImportMemesUseCase(repository), SavedStateHandle())
        viewModel.setSources(listOf("one", "two"))
        viewModel.startImport()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("one", "two"), viewModel.state.value.sources)
        assertEquals(ImportSummary.Completed(0, 0, 2), viewModel.state.value.summary)
    }
}
