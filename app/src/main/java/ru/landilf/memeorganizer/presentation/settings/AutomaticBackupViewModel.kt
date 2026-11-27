package ru.landilf.memeorganizer.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.data.backup.AutomaticBackupScheduler
import ru.landilf.memeorganizer.domain.model.settings.AppSettings
import ru.landilf.memeorganizer.domain.model.settings.AutomaticBackupPeriod
import ru.landilf.memeorganizer.domain.repository.AppSettingsRepository
import javax.inject.Inject

@HiltViewModel
class AutomaticBackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val scheduler: AutomaticBackupScheduler
) : ViewModel() {
    val settings = settingsRepository.observeSettings().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    private val _notice = MutableStateFlow<AutomaticBackupNotice?>(null)
    val notice = _notice.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            if (enabled && current.automaticBackupDirectoryUri == null) {
                _notice.value = AutomaticBackupNotice.DirectoryRequired
                return@launch
            }
            settingsRepository.setAutomaticBackupEnabled(enabled)
            if (enabled) scheduler.schedule(current.automaticBackupPeriod) else scheduler.cancel()
        }
    }

    fun setPeriod(period: AutomaticBackupPeriod) {
        viewModelScope.launch {
            settingsRepository.setAutomaticBackupPeriod(period)
            if (settings.value.automaticBackupEnabled) scheduler.schedule(period)
        }
    }

    fun setDirectory(uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
                settingsRepository.setAutomaticBackupDirectory(uri.toString(), name)
                settingsRepository.clearAutomaticBackupStatus()
                if (settings.value.automaticBackupEnabled) {
                    scheduler.schedule(settings.value.automaticBackupPeriod)
                }
                _notice.value = AutomaticBackupNotice.DirectorySaved
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _notice.value = AutomaticBackupNotice.DirectoryError
            }
        }
    }

    fun runNow() {
        if (settings.value.automaticBackupDirectoryUri == null) {
            _notice.value = AutomaticBackupNotice.DirectoryRequired
            return
        }
        scheduler.runNow()
        _notice.value = AutomaticBackupNotice.Queued
    }

    fun clearNotice() {
        _notice.value = null
    }
}

enum class AutomaticBackupNotice {
    DirectoryRequired,
    DirectorySaved,
    DirectoryError,
    Queued
}
