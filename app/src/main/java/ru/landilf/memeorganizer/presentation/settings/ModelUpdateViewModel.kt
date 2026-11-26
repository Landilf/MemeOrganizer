package ru.landilf.memeorganizer.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.data.ml.GitHubModelUpdater
import ru.landilf.memeorganizer.data.ml.ModelDownloadProgress
import ru.landilf.memeorganizer.data.ml.ModelRelease
import ru.landilf.memeorganizer.data.ml.ModelUpdateCheck
import ru.landilf.memeorganizer.data.ml.ModelUpdateOfflineException
import javax.inject.Inject

enum class ModelUpdateNotice { None, UpToDate, Offline, Installed, Error }

data class ModelUpdateUiState(
    val checking: Boolean = false,
    val available: ModelRelease? = null,
    val progress: ModelDownloadProgress? = null,
    val notice: ModelUpdateNotice = ModelUpdateNotice.None
)

@HiltViewModel
class ModelUpdateViewModel @Inject constructor(private val updater: GitHubModelUpdater) : ViewModel() {
    private val _state = MutableStateFlow(ModelUpdateUiState())
    val state = _state.asStateFlow()

    init { checkForUpdates(manual = false) }

    fun checkForUpdates(manual: Boolean = true) {
        if (_state.value.checking || _state.value.progress != null) return
        _state.update { it.copy(checking = true, notice = ModelUpdateNotice.None) }
        viewModelScope.launch {
            try {
                when (val result = updater.checkForUpdate(manual)) {
                    is ModelUpdateCheck.Available -> _state.update { it.copy(checking = false, available = result.release) }
                    ModelUpdateCheck.UpToDate -> _state.update { it.copy(checking = false, notice = if (manual) ModelUpdateNotice.UpToDate else ModelUpdateNotice.None) }
                    ModelUpdateCheck.Offline -> _state.update { it.copy(checking = false, notice = if (manual) ModelUpdateNotice.Offline else ModelUpdateNotice.None) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.mlError(error) { "update.check.failed" }
                _state.update { it.copy(checking = false, notice = if (manual) ModelUpdateNotice.Error else ModelUpdateNotice.None) }
            }
        }
    }

    fun postpone(release: ModelRelease) {
        _state.update { it.copy(available = null) }
        viewModelScope.launch { updater.dismiss(release) }
    }

    fun install(release: ModelRelease) {
        if (_state.value.progress != null) return
        _state.update { it.copy(progress = ModelDownloadProgress.Downloading(0, release.archiveSizeBytes), notice = ModelUpdateNotice.None) }
        viewModelScope.launch {
            try {
                updater.downloadAndInstall(release) { progress -> _state.update { it.copy(progress = progress) } }
                _state.update { it.copy(available = null, progress = null, notice = ModelUpdateNotice.Installed) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: ModelUpdateOfflineException) {
                _state.update { it.copy(progress = null, notice = ModelUpdateNotice.Offline) }
            } catch (error: Exception) {
                AppLogger.mlError(error) { "update.install.failed" }
                _state.update { it.copy(progress = null, notice = ModelUpdateNotice.Error) }
            }
        }
    }
}
