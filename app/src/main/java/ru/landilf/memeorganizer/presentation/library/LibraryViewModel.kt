package ru.landilf.memeorganizer.presentation.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.core.logging.SearchActionLogWriter
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import ru.landilf.memeorganizer.domain.repository.MemeExportRepository
import ru.landilf.memeorganizer.domain.repository.MediaIndexingRepository
import ru.landilf.memeorganizer.domain.usecase.album.CreateAlbumUseCase
import ru.landilf.memeorganizer.domain.usecase.album.AddMemesToAlbumUseCase
import ru.landilf.memeorganizer.domain.usecase.album.DeleteAlbumUseCase
import ru.landilf.memeorganizer.domain.usecase.album.ObserveAlbumMemesUseCase
import ru.landilf.memeorganizer.domain.usecase.album.ObserveAlbumsUseCase
import ru.landilf.memeorganizer.domain.usecase.album.RemoveMemesFromAlbumUseCase
import ru.landilf.memeorganizer.domain.usecase.album.RenameAlbumUseCase
import ru.landilf.memeorganizer.domain.usecase.meme.MarkMemeUsedUseCase
import ru.landilf.memeorganizer.domain.usecase.meme.ObserveMemesUseCase
import ru.landilf.memeorganizer.domain.usecase.meme.SaveMemeDetailsUseCase
import ru.landilf.memeorganizer.domain.usecase.meme.SetMemeFavoriteUseCase
import javax.inject.Inject

enum class AlbumNameError { EmptyName, Duplicate, Failed }

data class EditState(
    val memeId: Long,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val original: MemeDetails? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeMemes: ObserveMemesUseCase,
    observeAlbums: ObserveAlbumsUseCase,
    observeAlbumMemes: ObserveAlbumMemesUseCase,
    private val detailsRepository: MemeDetailsRepository,
    private val saveDetails: SaveMemeDetailsUseCase,
    private val setMemeFavorite: SetMemeFavoriteUseCase,
    private val recordMemeUsage: MarkMemeUsedUseCase,
    private val createNewAlbum: CreateAlbumUseCase,
    private val addMemes: AddMemesToAlbumUseCase,
    private val renameAlbumUseCase: RenameAlbumUseCase,
    private val deleteAlbumUseCase: DeleteAlbumUseCase,
    private val removeMemesFromAlbumUseCase: RemoveMemesFromAlbumUseCase,
    private val memeExportRepository: MemeExportRepository,
    private val mediaIndexingRepository: MediaIndexingRepository,
    private val searchActionLog: SearchActionLogWriter
) : ViewModel() {
    val memes =
        observeMemes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums =
        observeAlbums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val openedAlbumId = MutableStateFlow<Long?>(null)
    val albumMemes = openedAlbumId
        .flatMapLatest { albumId ->
            if (albumId == null) flowOf(emptyList()) else observeAlbumMemes(albumId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = detailsRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val indexingStatuses = mediaIndexingRepository.observeStatuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    private val _edit = MutableStateFlow<EditState?>(null)
    val edit = _edit.asStateFlow()
    private val _deleteError = MutableStateFlow(false)
    val deleteError = _deleteError.asStateFlow()
    private val _albumCreationError = MutableStateFlow<AlbumNameError?>(null)
    val albumCreationError = _albumCreationError.asStateFlow()
    private val _albumRenameError = MutableStateFlow<AlbumNameError?>(null)
    val albumRenameError = _albumRenameError.asStateFlow()

    fun openEditor(id: Long) {
        _edit.value = EditState(id)
        viewModelScope.launch {
            try {
                val details = detailsRepository.load(id)
                _edit.update {
                    if (it?.memeId == id) it.copy(
                        description = details.description,
                        tags = details.tags,
                        original = details,
                        loading = false
                    ) else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _edit.update {
                    if (it?.memeId == id) it.copy(
                        loading = false,
                        error = true
                    ) else it
                }
            }
        }
    }

    fun updateDescription(value: String) {
        _edit.update { it?.copy(description = value, error = false) }
    }

    fun updateTags(value: List<String>) {
        _edit.update { it?.copy(tags = value, error = false) }
    }

    fun closeEditor() {
        _edit.value = null
    }

    fun saveEditor() {
        val current = _edit.value ?: return
        if (current.loading || current.saving || current.original == null) return
        _edit.update { it?.copy(saving = true, error = false) }
        viewModelScope.launch {
            try {
                saveDetails(current.memeId, MemeDetails(current.description, current.tags))
                _edit.update { if (it?.memeId == current.memeId) null else it }
                launch { mediaIndexingRepository.enqueue(current.memeId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _edit.update {
                    if (it?.memeId == current.memeId) it.copy(
                        saving = false,
                        error = true
                    ) else it
                }
            }
        }
    }

    fun delete(ids: Set<Long>) {
        viewModelScope.launch {
            try {
                detailsRepository.delete(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _deleteError.value = true
            }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = false
    }

    fun setFavorite(meme: Meme, isFavorite: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                setMemeFavorite(meme.id, isFavorite)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.app { "favorite.update.failed type=${error.javaClass.simpleName}" }
            }
        }
    }

    fun markMemeUsed(memeId: Long) {
        viewModelScope.launch {
            try {
                recordMemeUsage(memeId)
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.app { "meme.usage.update.failed type=${error.javaClass.simpleName}" }
            }
        }
    }

    fun exportMeme(
        meme: Meme,
        destination: Uri,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                memeExportRepository.export(meme, destination)
                recordMemeUsage(meme.id)
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.app { "media.export.failed type=${error.javaClass.simpleName}" }
                onFailure()
            }
        }
    }

    fun createAlbum(name: String, onCreated: () -> Unit) {
        _albumCreationError.value = null
        viewModelScope.launch {
            try {
                if (createNewAlbum(name)) onCreated() else _albumCreationError.value = AlbumNameError.Duplicate
            } catch (e: IllegalArgumentException) {
                _albumCreationError.value = AlbumNameError.EmptyName
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.app { "album.create.failed type=${error.javaClass.simpleName}" }
                _albumCreationError.value = AlbumNameError.Failed
            }
        }
    }

    fun clearAlbumCreationError() {
        _albumCreationError.value = null
    }

    fun renameAlbum(albumId: Long, name: String, onRenamed: () -> Unit) {
        _albumRenameError.value = null
        viewModelScope.launch {
            try {
                if (renameAlbumUseCase(albumId, name)) {
                    onRenamed()
                } else {
                    _albumRenameError.value = AlbumNameError.Duplicate
                }
            } catch (e: IllegalArgumentException) {
                _albumRenameError.value = AlbumNameError.EmptyName
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.app { "album.rename.failed type=${error.javaClass.simpleName}" }
                _albumRenameError.value = AlbumNameError.Failed
            }
        }
    }

    fun clearAlbumRenameError() {
        _albumRenameError.value = null
    }

    fun deleteAlbum(albumId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                deleteAlbumUseCase(albumId)
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.app { "album.delete.failed type=${error.javaClass.simpleName}" }
            }
        }
    }

    fun openAlbum(albumId: Long) {
        openedAlbumId.value = albumId
    }

    fun closeAlbum() {
        openedAlbumId.value = null
    }

    fun addMemesToAlbum(albumId: Long, memeIds: Set<Long>, onAdded: () -> Unit) {
        if (memeIds.isEmpty()) return
        viewModelScope.launch {
            try {
                addMemes(albumId, memeIds)
                onAdded()
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.app { "album.memes.add.failed type=${error.javaClass.simpleName}" }
            }
        }
    }

    fun removeMemesFromAlbum(albumId: Long, memeIds: Set<Long>, onRemoved: () -> Unit = {}) {
        if (memeIds.isEmpty()) return
        viewModelScope.launch {
            try {
                removeMemesFromAlbumUseCase(albumId, memeIds)
                onRemoved()
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.app { "album.memes.remove.failed type=${error.javaClass.simpleName}" }
            }
        }
    }

    fun recordSearchCopy(searchId: String, meme: Meme, rank: Int, impressionAtMs: Long) {
        recordSearchAction(searchId, meme, rank, impressionAtMs, "copy")
    }

    fun recordSearchShare(searchId: String, meme: Meme, rank: Int, impressionAtMs: Long) {
        recordSearchAction(searchId, meme, rank, impressionAtMs, "share")
    }

    fun recordSearchFavorite(searchId: String, meme: Meme, rank: Int, impressionAtMs: Long) {
        recordSearchAction(searchId, meme, rank, impressionAtMs, "favorite")
    }

    private fun recordSearchAction(
        searchId: String,
        meme: Meme,
        rank: Int,
        impressionAtMs: Long,
        actionType: String
    ) {
        viewModelScope.launch {
            searchActionLog.record(
                searchId = searchId,
                actionType = actionType,
                impressionAtMs = impressionAtMs,
                fileHash = meme.contentHash,
                rank = rank
            )
        }
    }
}
