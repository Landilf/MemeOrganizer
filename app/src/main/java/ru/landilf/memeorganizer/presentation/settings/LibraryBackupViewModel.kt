package ru.landilf.memeorganizer.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.domain.model.backup.LibraryBackupSummary
import ru.landilf.memeorganizer.domain.model.backup.LibraryRestoreSummary
import ru.landilf.memeorganizer.domain.repository.LibraryBackupRepository
import javax.inject.Inject

sealed interface LibraryBackupStatus {
    data object Idle : LibraryBackupStatus
    data object Creating : LibraryBackupStatus
    data class CreateSuccess(val summary: LibraryBackupSummary) : LibraryBackupStatus
    data object CreateError : LibraryBackupStatus
    data object Restoring : LibraryBackupStatus
    data class RestoreSuccess(val summary: LibraryRestoreSummary) : LibraryBackupStatus
    data object RestoreError : LibraryBackupStatus
}

@HiltViewModel
class LibraryBackupViewModel @Inject constructor(
    private val repository: LibraryBackupRepository
) : ViewModel() {
    private val _status = MutableStateFlow<LibraryBackupStatus>(LibraryBackupStatus.Idle)
    val status = _status.asStateFlow()

    fun clearStatus() {
        _status.value = LibraryBackupStatus.Idle
    }

    fun create(destination: Uri) {
        if (status.value.isBusy()) return
        _status.value = LibraryBackupStatus.Creating
        viewModelScope.launch {
            try {
                _status.value = LibraryBackupStatus.CreateSuccess(repository.create(destination))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.app { "backup.create.failed type=${error.javaClass.simpleName}" }
                _status.value = LibraryBackupStatus.CreateError
            }
        }
    }

    fun restore(source: Uri) {
        if (status.value.isBusy()) return
        _status.value = LibraryBackupStatus.Restoring
        viewModelScope.launch {
            try {
                _status.value = LibraryBackupStatus.RestoreSuccess(repository.restore(source))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.app { "backup.restore.failed type=${error.javaClass.simpleName}" }
                _status.value = LibraryBackupStatus.RestoreError
            }
        }
    }
}

fun LibraryBackupStatus.isBusy(): Boolean = this is LibraryBackupStatus.Creating ||
        this is LibraryBackupStatus.Restoring
