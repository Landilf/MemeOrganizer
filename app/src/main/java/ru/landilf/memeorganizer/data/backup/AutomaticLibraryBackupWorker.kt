package ru.landilf.memeorganizer.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.domain.repository.AppSettingsRepository
import ru.landilf.memeorganizer.domain.repository.LibraryBackupRepository

class AutomaticLibraryBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            AutomaticBackupWorkerEntryPoint::class.java
        )
        val settingsRepository = dependencies.settingsRepository()
        val settings = settingsRepository.observeSettings().first()
        if (!settings.automaticBackupEnabled || settings.automaticBackupDirectoryUri == null) {
            return Result.success()
        }
        return try {
            dependencies.backupRepository().createInDirectory(
                android.net.Uri.parse(settings.automaticBackupDirectoryUri)
            )
            settingsRepository.recordAutomaticBackupSuccess(System.currentTimeMillis())
            AppLogger.app { "backup.automatic.success" }
            Result.success()
        } catch (error: Exception) {
            val message = error.message?.take(MAX_ERROR_LENGTH) ?: error.javaClass.simpleName
            settingsRepository.recordAutomaticBackupError(message)
            AppLogger.app { "backup.automatic.failed type=${error.javaClass.simpleName}" }
            Result.failure()
        }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 240
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomaticBackupWorkerEntryPoint {
    fun settingsRepository(): AppSettingsRepository
    fun backupRepository(): LibraryBackupRepository
}
