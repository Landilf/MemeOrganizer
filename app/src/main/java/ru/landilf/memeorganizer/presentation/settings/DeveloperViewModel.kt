package ru.landilf.memeorganizer.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.core.logging.DebugLogExporter
import ru.landilf.memeorganizer.data.device.BatteryStateChecker
import ru.landilf.memeorganizer.data.ml.FusionSmokeTestResult
import ru.landilf.memeorganizer.data.ml.FusionSmokeTestRunner
import ru.landilf.memeorganizer.data.ml.LocalModelInstaller
import ru.landilf.memeorganizer.data.indexing.IndexingNotifier
import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository
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
    FusionTestError
}

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val exporter: DebugLogExporter,
    private val searchRepository: MemeSearchRepository,
    private val modelInstaller: LocalModelInstaller,
    private val fusionSmokeTestRunner: FusionSmokeTestRunner,
    private val batteryStateChecker: BatteryStateChecker,
    private val indexingNotifier: IndexingNotifier
) : ViewModel() {
    private val _status = MutableStateFlow(DebugExportStatus.Idle)
    val status = _status.asStateFlow()
    private val _reindexedCount = MutableStateFlow(0)
    val reindexedCount = _reindexedCount.asStateFlow()
    private val _lowBatteryConfirmationRequired = MutableStateFlow(false)
    val lowBatteryConfirmationRequired = _lowBatteryConfirmationRequired.asStateFlow()

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
        if (_status.value.isBusy()) return
        if (batteryStateChecker.needsHeavyWorkConfirmation()) {
            _lowBatteryConfirmationRequired.value = true
        } else {
            reindexAll()
        }
    }

    fun confirmReindexAtLowBattery() {
        _lowBatteryConfirmationRequired.value = false
        reindexAll()
    }

    fun dismissLowBatteryConfirmation() {
        _lowBatteryConfirmationRequired.value = false
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
}

internal fun DebugExportStatus.isBusy(): Boolean = this in setOf(
    DebugExportStatus.Exporting,
    DebugExportStatus.Clearing,
    DebugExportStatus.Reindexing,
    DebugExportStatus.TestingFusion
)
