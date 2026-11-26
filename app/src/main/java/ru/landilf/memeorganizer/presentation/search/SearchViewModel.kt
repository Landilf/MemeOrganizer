package ru.landilf.memeorganizer.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.domain.model.search.MemeSearchResult
import ru.landilf.memeorganizer.domain.usecase.search.PrepareMemeSearchUseCase
import ru.landilf.memeorganizer.domain.usecase.search.SearchMemesUseCase
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val modelAvailable: Boolean = false,
    val preparing: Boolean = true,
    val results: List<MemeSearchResult> = emptyList(),
    val error: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val prepareSearch: PrepareMemeSearchUseCase,
    private val searchMemes: SearchMemesUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
    private var queryJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val preparation = prepareSearch()
                _state.update { it.copy(modelAvailable = preparation.modelAvailable, preparing = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.mlError(error) { "search.prepare.failed" }
                _state.update { it.copy(preparing = false, error = true) }
            }
        }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query, error = false) }
        queryJob?.cancel()
        if (query.isBlank() || !_state.value.modelAvailable) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        queryJob = viewModelScope.launch {
            delay(200)
            try {
                _state.update { it.copy(preparing = true) }
                val results = searchMemes(query)
                _state.update { state -> if (state.query == query) state.copy(results = results, preparing = false) else state }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.mlError(error) { "search.query.failed" }
                _state.update { state -> if (state.query == query) state.copy(preparing = false, error = true) else state }
            }
        }
    }
}
