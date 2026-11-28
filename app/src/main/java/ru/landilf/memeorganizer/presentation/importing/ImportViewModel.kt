package ru.landilf.memeorganizer.presentation.importing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.domain.model.importing.ImportResult
import ru.landilf.memeorganizer.domain.model.importing.ImportSource
import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import ru.landilf.memeorganizer.domain.repository.MediaIndexingRepository
import ru.landilf.memeorganizer.domain.usecase.meme.ImportMemesUseCase
import ru.landilf.memeorganizer.domain.usecase.meme.SaveMemeDetailsUseCase
import javax.inject.Inject

data class ImportUiState(
    val sources: List<String> = emptyList(),
    val selected: Set<String> = emptySet(),
    val importing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val summary: ImportSummary? = null,
    val detailsBySource: Map<String, MemeDetails> = emptyMap()
)

sealed interface ImportSummary {
    data class Completed(val imported: Int, val duplicates: Int, val failed: Int) : ImportSummary
    data class Interrupted(val imported: Int) : ImportSummary
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importMemes: ImportMemesUseCase,
    private val detailsRepository: MemeDetailsRepository,
    private val saveMemeDetails: SaveMemeDetailsUseCase,
    private val mediaIndexingRepository: MediaIndexingRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state =
        MutableStateFlow(ImportUiState(sources = savedStateHandle["sources"] ?: emptyList()))
    val state = _state.asStateFlow()
    val tags = detailsRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        _state.update {
            it.copy(
                sources = remaining,
                selected = emptySet(),
                detailsBySource = it.detailsBySource.filterKeys { source -> source in remaining }
            )
        }
    }

    fun updateDetails(source: String, details: MemeDetails) {
        _state.update { current ->
            current.copy(detailsBySource = current.detailsBySource + (source to details))
        }
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
            val importedMemeIds = mutableListOf<Long>()
            var indexingQueued = false
            val remaining = sources.map { it.identifier }.toMutableList()
            suspend fun enqueueImportedMemes() {
                if (!indexingQueued && importedMemeIds.isNotEmpty()) {
                    mediaIndexingRepository.enqueueAll(importedMemeIds)
                    indexingQueued = true
                }
            }
            try {
                importMemes(sources).collect { progress ->
                    val result = progress.latestResult
                    when (result) {
                        is ImportResult.Imported -> {
                            imported++
                            progress.latestSource?.identifier?.let { source ->
                                val details = _state.value.detailsBySource[source]
                                if (
                                    details != null &&
                                        (details.description.isNotBlank() || details.tags.isNotEmpty())
                                ) {
                                    saveMemeDetails(result.meme.id, details)
                                }
                                importedMemeIds += result.meme.id
                                remaining.remove(source)
                            }
                        }

                        is ImportResult.Duplicate -> {
                            duplicates++; remaining.remove(progress.latestSource?.identifier)
                        }

                        is ImportResult.Failed -> failed++
                        null -> Unit
                    }
                    _state.update { it.copy(completed = progress.completed) }
                }
                enqueueImportedMemes()
                savedStateHandle["sources"] = ArrayList(remaining)
                _state.update {
                    it.copy(
                        importing = false,
                        sources = remaining.toList(),
                        detailsBySource = it.detailsBySource.filterKeys { source -> source in remaining },
                        summary = ImportSummary.Completed(imported, duplicates, failed)
                    )
                }
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (_: Exception) {
                runCatching { enqueueImportedMemes() }
                savedStateHandle["sources"] = ArrayList(remaining)
                _state.update {
                    it.copy(
                        importing = false,
                        sources = remaining.toList(),
                        summary = ImportSummary.Interrupted(imported)
                    )
                }
            }
        }
    }
}
