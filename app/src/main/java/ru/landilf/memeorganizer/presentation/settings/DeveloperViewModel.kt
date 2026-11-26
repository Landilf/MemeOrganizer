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
import javax.inject.Inject

enum class DebugExportStatus { Idle, Exporting, Success, Error }

@HiltViewModel
class DeveloperViewModel @Inject constructor(private val exporter: DebugLogExporter) : ViewModel() {
    private val _status = MutableStateFlow(DebugExportStatus.Idle)
    val status = _status.asStateFlow()

    fun export(destination: Uri, includeMedia: Boolean) {
        if (_status.value == DebugExportStatus.Exporting) return
        _status.value = DebugExportStatus.Exporting
        viewModelScope.launch {
            try {
                if (includeMedia) exporter.exportFullBundle(destination) else exporter.exportLogs(destination)
                _status.value = DebugExportStatus.Success
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.app { "debug.export.failed type=${error.javaClass.simpleName}" }
                _status.value = DebugExportStatus.Error
            }
        }
    }

    fun clearStatus() { _status.update { DebugExportStatus.Idle } }
}
