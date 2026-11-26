package ru.landilf.memeorganizer.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.data.ml.LocalModelInstaller
import ru.landilf.memeorganizer.data.ml.GitHubModelUpdater
import ru.landilf.memeorganizer.core.logging.AppLogger
import javax.inject.Inject

enum class ModelStatus { Checking, NotInstalled, Installing, Installed, Error }

@HiltViewModel
class ModelSettingsViewModel @Inject constructor(
    private val installer: LocalModelInstaller,
    private val updater: GitHubModelUpdater
) : ViewModel() {
    private val _status = MutableStateFlow(ModelStatus.Checking)
    val status = _status.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _status.value =
                    if (installer.activePackage() == null) ModelStatus.NotInstalled else ModelStatus.Installed
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.mlError(error) { "model.active.failed" }; _status.value =
                    ModelStatus.Error
            }
        }
    }

    fun install(uri: Uri) {
        if (_status.value == ModelStatus.Installing) return
        _status.value = ModelStatus.Installing
        viewModelScope.launch {
            try {
                val model = installer.install(uri)
                updater.recordInstalledTag(model.releaseTag)
                _status.value = ModelStatus.Installed
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.mlError(error) { "model.install.failed" }; _status.value =
                    ModelStatus.Error
            }
        }
    }
}
