package ru.landilf.memeorganizer.data.indexing

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import ru.landilf.memeorganizer.data.storage.dao.MediaIndexingDao
import ru.landilf.memeorganizer.data.storage.entities.search.MediaIndexingEntity
import ru.landilf.memeorganizer.data.device.BatteryStateChecker
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus
import ru.landilf.memeorganizer.domain.repository.MediaIndexingRepository

@Singleton
class MediaIndexingRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val indexingDao: MediaIndexingDao,
    private val batteryStateChecker: BatteryStateChecker,
    private val indexingNotifier: IndexingNotifier
) : MediaIndexingRepository {
    override suspend fun enqueue(memeId: Long) {
        val waitingForBattery = batteryStateChecker.needsHeavyWorkConfirmation()
        indexingDao.upsert(
            MediaIndexingEntity(
                memeId = memeId,
                status = MediaIndexingStatus.Pending.storageValue,
                attemptCount = 0,
                lastError = if (waitingForBattery) BATTERY_LOW_REASON else null,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        if (waitingForBattery) indexingNotifier.waitingForBattery()
        enqueueWork(memeId)
    }

    override suspend fun resumePending() {
        for (memeId in indexingDao.getMemeIdsByStatus(MediaIndexingStatus.Pending.storageValue)) {
            enqueue(memeId)
        }
    }

    override fun observeStatuses(): Flow<Map<Long, MediaIndexingStatus>> =
        indexingDao.observeAll().map { entries ->
            entries.mapNotNull { entry ->
                MediaIndexingStatus.fromStorageValue(entry.status)?.let { status ->
                    entry.memeId to status
                }
            }.toMap()
        }

    private fun enqueueWork(memeId: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(memeId),
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MediaIndexingWorker>()
                .setInputData(workDataOf(MediaIndexingWorker.MEME_ID_KEY to memeId))
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        )
    }

    companion object {
        fun workName(memeId: Long) = "media_indexing_$memeId"
        const val BATTERY_LOW_REASON = "battery_low"
        private const val BACKOFF_SECONDS = 30L
    }
}
