package ru.landilf.memeorganizer

import android.app.Application
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.data.backup.AutomaticBackupScheduler
import ru.landilf.memeorganizer.domain.repository.AppSettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MemeOrganizerApplication : Application() {
    @Inject lateinit var settingsRepository: AppSettingsRepository
    @Inject lateinit var automaticBackupScheduler: AutomaticBackupScheduler

    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val settings = settingsRepository.observeSettings().first()
            if (settings.automaticBackupEnabled && settings.automaticBackupDirectoryUri != null) {
                automaticBackupScheduler.schedule(settings.automaticBackupPeriod)
            }
        }
    }
}
