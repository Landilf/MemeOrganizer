package ru.landilf.memeorganizer.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import ru.landilf.memeorganizer.domain.usecase.meme.ObserveMemesUseCase
import ru.landilf.memeorganizer.domain.usecase.meme.SaveMemeDetailsUseCase
import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository
import ru.landilf.memeorganizer.core.logging.LightLogStore
import org.json.JSONObject
import ru.landilf.memeorganizer.domain.model.meme.Meme
import javax.inject.Inject

data class EditState(val memeId: Long, val description: String = "", val tags: List<String> = emptyList(), val original: MemeDetails? = null, val loading: Boolean = true, val saving: Boolean = false, val error: Boolean = false)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeMemes: ObserveMemesUseCase,
    private val detailsRepository: MemeDetailsRepository,
    private val saveDetails: SaveMemeDetailsUseCase,
    private val searchRepository: MemeSearchRepository,
    private val lightLog: LightLogStore
) : ViewModel() {
    val memes = observeMemes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = detailsRepository.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _edit = MutableStateFlow<EditState?>(null)
    val edit = _edit.asStateFlow()
    private val _deleteError = MutableStateFlow(false)
    val deleteError = _deleteError.asStateFlow()

    fun openEditor(id: Long) {
        _edit.value = EditState(id)
        viewModelScope.launch {
            try {
                val details = detailsRepository.load(id)
                _edit.update { if (it?.memeId == id) it.copy(description = details.description, tags = details.tags, original = details, loading = false) else it }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _edit.update { if (it?.memeId == id) it.copy(loading = false, error = true) else it }
            }
        }
    }
    fun updateDescription(value: String) { _edit.update { it?.copy(description = value, error = false) } }
    fun updateTags(value: List<String>) { _edit.update { it?.copy(tags = value, error = false) } }
    fun closeEditor() { _edit.value = null }
    fun saveEditor() {
        val current = _edit.value ?: return
        if (current.loading || current.saving || current.original == null) return
        _edit.update { it?.copy(saving = true, error = false) }
        viewModelScope.launch {
            try {
                saveDetails(current.memeId, MemeDetails(current.description, current.tags))
                _edit.update { if (it?.memeId == current.memeId) null else it }
                launch { searchRepository.indexMeme(current.memeId) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _edit.update { if (it?.memeId == current.memeId) it.copy(saving = false, error = true) else it }
            }
        }
    }
    fun delete(ids: Set<Long>) {
        viewModelScope.launch {
            try { detailsRepository.delete(ids) } catch (e: CancellationException) { throw e } catch (_: Exception) { _deleteError.value = true }
        }
    }
    fun clearDeleteError() { _deleteError.value = false }

    fun recordSearchCopy(searchId: String, meme: Meme, rank: Int, impressionAtMs: Long) {
        viewModelScope.launch {
            lightLog.record("search_action", JSONObject()
                .put("search_id", searchId)
                .put("action_type", "copy")
                .put("file_hash", meme.contentHash)
                .put("rank", rank)
                .put("time_since_impression_ms", System.currentTimeMillis() - impressionAtMs))
        }
    }
}
