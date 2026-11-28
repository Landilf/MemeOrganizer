package ru.landilf.memeorganizer.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.core.logging.DebugLogExporter
import ru.landilf.memeorganizer.data.ml.AsrSmokeTestResult
import ru.landilf.memeorganizer.data.ml.AsrSmokeTestRunner
import ru.landilf.memeorganizer.data.ml.FusionSmokeTestResult
import ru.landilf.memeorganizer.data.ml.FusionSmokeTestRunner
import ru.landilf.memeorganizer.data.ml.GitHubModelUpdater
import ru.landilf.memeorganizer.data.ml.InsufficientModelStorageException
import ru.landilf.memeorganizer.data.ml.ModelDownloadProgress
import ru.landilf.memeorganizer.data.ml.ModelRelease
import ru.landilf.memeorganizer.data.ml.ModelUpdateOfflineException
import ru.landilf.memeorganizer.data.ml.LocalModelInstaller
import ru.landilf.memeorganizer.data.ml.OcrSmokeTestResult
import ru.landilf.memeorganizer.data.ml.OcrSmokeTestRunner
import ru.landilf.memeorganizer.data.indexing.IndexingNotifier
import ru.landilf.memeorganizer.domain.repository.MediaIndexingRepository
import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository
import ru.landilf.memeorganizer.domain.usecase.meme.ObserveMemesUseCase
import javax.inject.Inject

enum class DebugExportStatus {
    Idle,
    Exporting,
    ExportSuccess,
    ExportError,
    Clearing,
    ClearSuccess,
    ClearError,
    Reindexing,
    ReindexSuccess,
    ReindexModelUnavailable,
    ReindexError,
    TestingFusion,
    FusionTestSuccess,
    FusionTestUnavailable,
    FusionTestError,
    TestingOcr,
    OcrTestSuccess,
    OcrTestUnavailable,
    OcrTestError,
    TestingAsr,
    AsrTestSuccess,
    AsrTestUnavailable,
    AsrTestError,
    SimulatingIndexingFailure,
    SimulatedIndexingFailure,
    SimulatedIndexingFailureError
}

enum class DeveloperModelReleaseNotice { None, Installed, Offline, InsufficientStorage, Error }

data class DeveloperModelReleaseUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val releases: List<ModelRelease> = emptyList(),
    val installedTag: String? = null,
    val selectedRelease: ModelRelease? = null,
    val progress: ModelDownloadProgress? = null,
    val notice: DeveloperModelReleaseNotice = DeveloperModelReleaseNotice.None
)

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val exporter: DebugLogExporter,
    private val searchRepository: MemeSearchRepository,
    private val modelInstaller: LocalModelInstaller,
    private val fusionSmokeTestRunner: FusionSmokeTestRunner,
    private val ocrSmokeTestRunner: OcrSmokeTestRunner,
    private val asrSmokeTestRunner: AsrSmokeTestRunner,
    private val modelUpdater: GitHubModelUpdater,
    private val indexingNotifier: IndexingNotifier,
    observeMemes: ObserveMemesUseCase,
    private val mediaIndexingRepository: MediaIndexingRepository
) : ViewModel() {
    private val _status = MutableStateFlow(DebugExportStatus.Idle)
    val status = _status.asStateFlow()
    private val _reindexedCount = MutableStateFlow(0)
    val reindexedCount = _reindexedCount.asStateFlow()
    private val _modelReleaseState = MutableStateFlow(DeveloperModelReleaseUiState())
    val modelReleaseState = _modelReleaseState.asStateFlow()
    val memes = observeMemes()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun export(destination: Uri, includeMedia: Boolean) {
        if (_status.value.isBusy()) return
        _status.value = DebugExportStatus.Exporting
        viewModelScope.launch {
            try {
                if (includeMedia) exporter.exportFullBundle(destination) else exporter.exportLogs(
                    destination
                )
                _status.value = DebugExportStatus.ExportSuccess
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.app { "debug.export.failed type=${error.javaClass.simpleName}" }
                _status.value = DebugExportStatus.ExportError
            }
        }
    }

    fun clearStatus() {
        _status.update { DebugExportStatus.Idle }
    }

    fun clearLogs() {
        if (_status.value.isBusy()) return
        _status.value = DebugExportStatus.Clearing
        viewModelScope.launch {
            try {
                exporter.clearLogs()
                _status.value = DebugExportStatus.ClearSuccess
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.app { "debug.clear.failed type=${error.javaClass.simpleName}" }
                _status.value = DebugExportStatus.ClearError
            }
        }
    }

    fun requestReindexAll() {
        reindexAll()
    }

    private fun reindexAll() {
        if (_status.value.isBusy()) return
        _status.value = DebugExportStatus.Reindexing
        viewModelScope.launch {
            try {
                val result = searchRepository.reindexAll()
                _reindexedCount.value = result.indexedCount
                _status.value = if (result.modelAvailable) {
                    indexingNotifier.completed()
                    DebugExportStatus.ReindexSuccess
                } else {
                    DebugExportStatus.ReindexModelUnavailable
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.app { "debug.reindex.failed type=${error.javaClass.simpleName}" }
                indexingNotifier.failed()
                _status.value = DebugExportStatus.ReindexError
            }
        }
    }

    fun runFusionSmokeTest() {
        if (_status.value.isBusy()) return
        _status.value = DebugExportStatus.TestingFusion
        viewModelScope.launch {
            try {
                val modelPackage = modelInstaller.activePackage()
                if (modelPackage == null) {
                    _status.value = DebugExportStatus.FusionTestUnavailable
                    return@launch
                }
                _status.value = when (val result = fusionSmokeTestRunner.run(modelPackage)) {
                    is FusionSmokeTestResult.Passed -> {
                        AppLogger.ml { "fusion.smoke_test.passed count=${result.testCount}" }
                        DebugExportStatus.FusionTestSuccess
                    }

                    FusionSmokeTestResult.Unavailable -> DebugExportStatus.FusionTestUnavailable
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.mlError(error) { "fusion.smoke_test.failed" }
                _status.value = DebugExportStatus.FusionTestError
            }
        }
    }

    fun runOcrSmokeTest() {
        if (_status.value.isBusy()) return
        _status.value = DebugExportStatus.TestingOcr
        viewModelScope.launch {
            try {
                val modelPackage = modelInstaller.activePackage()
                _status.value = when {
                    modelPackage == null -> DebugExportStatus.OcrTestUnavailable
                    ocrSmokeTestRunner.run(modelPackage) is OcrSmokeTestResult.Passed -> DebugExportStatus.OcrTestSuccess
                    else -> DebugExportStatus.OcrTestUnavailable
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                AppLogger.mlError(error) { "ocr.smoke_test.failed" }
                _status.value = DebugExportStatus.OcrTestError
            }
        }
    }

    fun runAsrSmokeTest() {
        if (_status.value.isBusy()) return
        _status.value = DebugExportStatus.TestingAsr
        viewModelScope.launch {
            try {
                val modelPackage = modelInstaller.activePackage()
                _status.value = when {
                    modelPackage == null -> DebugExportStatus.AsrTestUnavailable
                    asrSmokeTestRunner.run(modelPackage) is AsrSmokeTestResult.Passed -> {
                        DebugExportStatus.AsrTestSuccess
                    }

                    else -> DebugExportStatus.AsrTestUnavailable
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.mlError(error) { "asr.smoke_test.failed" }
                _status.value = DebugExportStatus.AsrTestError
            }
        }
    }

    fun openModelReleasePicker() {
        if (_status.value.isBusy() || _modelReleaseState.value.loading ||
            _modelReleaseState.value.progress != null
        ) return
        _modelReleaseState.value = DeveloperModelReleaseUiState(visible = true, loading = true)
        viewModelScope.launch {
            try {
                val installedTag = modelInstaller.activePackage()?.releaseTag
                val releases = modelUpdater.availableReleases()
                _modelReleaseState.value = DeveloperModelReleaseUiState(
                    visible = true,
                    releases = releases,
                    installedTag = installedTag
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: ModelUpdateOfflineException) {
                _modelReleaseState.value = DeveloperModelReleaseUiState(
                    notice = DeveloperModelReleaseNotice.Offline
                )
            } catch (error: Exception) {
                AppLogger.mlError(error) { "developer.model_releases.load.failed" }
                _modelReleaseState.value = DeveloperModelReleaseUiState(
                    notice = DeveloperModelReleaseNotice.Error
                )
            }
        }
    }

    fun selectModelRelease(release: ModelRelease) {
        _modelReleaseState.update { it.copy(selectedRelease = release) }
    }

    fun dismissModelReleaseSelection() {
        _modelReleaseState.update { it.copy(selectedRelease = null) }
    }

    fun dismissModelReleasePicker() {
        if (_modelReleaseState.value.progress != null) return
        _modelReleaseState.update {
            it.copy(visible = false, selectedRelease = null, releases = emptyList())
        }
    }

    fun installSelectedModelRelease() {
        val release = _modelReleaseState.value.selectedRelease ?: return
        if (_modelReleaseState.value.progress != null) return
        _modelReleaseState.update {
            it.copy(
                selectedRelease = null,
                progress = ModelDownloadProgress.Downloading(0, release.archiveSizeBytes),
                notice = DeveloperModelReleaseNotice.None
            )
        }
        viewModelScope.launch {
            try {
                modelUpdater.downloadAndInstall(release) { progress ->
                    _modelReleaseState.update { it.copy(progress = progress) }
                }
                try {
                    mediaIndexingRepository.resumePending()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.mlError(error) {
                        "developer.model_releases.install.resume_indexing.failed"
                    }
                }
                _modelReleaseState.value = DeveloperModelReleaseUiState(
                    installedTag = release.tag,
                    notice = DeveloperModelReleaseNotice.Installed
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: ModelUpdateOfflineException) {
                _modelReleaseState.update {
                    it.copy(progress = null, notice = DeveloperModelReleaseNotice.Offline)
                }
            } catch (error: InsufficientModelStorageException) {
                AppLogger.mlError(error) { "developer.model_releases.install.storage_insufficient" }
                _modelReleaseState.update {
                    it.copy(progress = null, notice = DeveloperModelReleaseNotice.InsufficientStorage)
                }
            } catch (error: Exception) {
                AppLogger.mlError(error) { "developer.model_releases.install.failed" }
                _modelReleaseState.update {
                    it.copy(progress = null, notice = DeveloperModelReleaseNotice.Error)
                }
            }
        }
    }

    fun simulateIndexingFailure(memeId: Long) {
        if (_status.value.isBusy()) return
        _status.value = DebugExportStatus.SimulatingIndexingFailure
        viewModelScope.launch {
            try {
                mediaIndexingRepository.simulateFailureForDebug(memeId)
                _status.value = DebugExportStatus.SimulatedIndexingFailure
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.app { "debug.indexing_failure_simulation.failed type=${error.javaClass.simpleName}" }
                _status.value = DebugExportStatus.SimulatedIndexingFailureError
            }
        }
    }
}

internal fun DebugExportStatus.isBusy(): Boolean = this in setOf(
    DebugExportStatus.Exporting,
    DebugExportStatus.Clearing,
    DebugExportStatus.Reindexing,
    DebugExportStatus.TestingFusion,
    DebugExportStatus.TestingOcr,
    DebugExportStatus.TestingAsr,
    DebugExportStatus.SimulatingIndexingFailure
)
