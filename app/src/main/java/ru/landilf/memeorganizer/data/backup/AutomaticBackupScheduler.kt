package ru.landilf.memeorganizer.data.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.landilf.memeorganizer.domain.model.settings.AutomaticBackupPeriod
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomaticBackupScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun schedule(period: AutomaticBackupPeriod) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutomaticLibraryBackupWorker>(
                period.days,
                TimeUnit.DAYS
            ).build()
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }

    fun runNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutomaticLibraryBackupWorker>().build()
        )
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "automatic_library_backup_periodic"
        const val IMMEDIATE_WORK_NAME = "automatic_library_backup_immediate"
    }
}
