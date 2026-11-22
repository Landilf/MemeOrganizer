package ru.landilf.memeorganizer.presentation.importing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.domain.model.importing.ImportResult
import ru.landilf.memeorganizer.domain.model.importing.ImportSource
import ru.landilf.memeorganizer.domain.usecase.meme.ImportMemesUseCase
import javax.inject.Inject

data class ImportUiState(
    val sources: List<String> = emptyList(),
    val selected: Set<String> = emptySet(),
    val importing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val summary: ImportSummary? = null
)

sealed interface ImportSummary {
    data class Completed(val imported: Int, val duplicates: Int, val failed: Int) : ImportSummary
    data class Interrupted(val imported: Int) : ImportSummary
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importMemes: ImportMemesUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState(sources = savedStateHandle["sources"] ?: emptyList()))
    val state = _state.asStateFlow()

    fun setSources(sources: List<String>) {
        val unique = sources.distinct()
        savedStateHandle["sources"] = ArrayList(unique)
        _state.value = ImportUiState(sources = unique)
    }

    fun toggle(source: String) = _state.update {
        it.copy(selected = if (source in it.selected) it.selected - source else it.selected + source)
    }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }
    fun selectAll() = _state.update { it.copy(selected = it.sources.toSet()) }
    fun remove(sources: Set<String>) {
        val remaining = _state.value.sources.filterNot { it in sources }
        savedStateHandle["sources"] = ArrayList(remaining)
        _state.update { it.copy(sources = remaining, selected = emptySet()) }
    }
    fun clearSummary() = _state.update { it.copy(summary = null) }

    fun startImport() {
        if (_state.value.importing || _state.value.sources.isEmpty()) return
        val sources = _state.value.sources.map(::ImportSource)
        _state.update { it.copy(importing = true, completed = 0, total = sources.size) }
        viewModelScope.launch {
            var imported = 0
            var duplicates = 0
            var failed = 0
            val remaining = sources.map { it.identifier }.toMutableList()
            try {
                importMemes(sources).collect { progress ->
                    when (progress.latestResult) {
                        is ImportResult.Imported -> { imported++; remaining.remove(progress.latestSource?.identifier) }
                        is ImportResult.Duplicate -> { duplicates++; remaining.remove(progress.latestSource?.identifier) }
                        is ImportResult.Failed -> failed++
                        null -> Unit
                    }
                    _state.update { it.copy(completed = progress.completed) }
                }
                savedStateHandle["sources"] = ArrayList(remaining)
                _state.update { it.copy(importing = false, sources = remaining.toList(), summary = ImportSummary.Completed(imported, duplicates, failed)) }
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (_: Exception) {
                savedStateHandle["sources"] = ArrayList(remaining)
                _state.update { it.copy(importing = false, sources = remaining.toList(), summary = ImportSummary.Interrupted(imported)) }
            }
        }
    }
}
