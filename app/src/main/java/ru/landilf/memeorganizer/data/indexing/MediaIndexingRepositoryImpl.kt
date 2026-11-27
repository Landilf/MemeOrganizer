package ru.landilf.memeorganizer.data.indexing

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.dao.MediaIndexingDao
import ru.landilf.memeorganizer.data.storage.dao.MediaIndexingRunDao
import ru.landilf.memeorganizer.data.storage.entities.search.MediaIndexingEntity
import ru.landilf.memeorganizer.data.storage.entities.search.MediaIndexingRunEntity
import ru.landilf.memeorganizer.data.device.BatteryStateChecker
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingRun
import ru.landilf.memeorganizer.domain.repository.MediaIndexingRepository

@Singleton
class MediaIndexingRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val indexingDao: MediaIndexingDao,
    private val indexingRunDao: MediaIndexingRunDao,
    private val memeDao: MemeDao,
    private val batteryStateChecker: BatteryStateChecker,
    private val indexingNotifier: IndexingNotifier
) : MediaIndexingRepository {
    override suspend fun enqueue(memeId: Long) {
        scheduleNewRun(listOf(memeId))
    }

    override suspend fun enqueueAll(memeIds: List<Long>): Int = scheduleNewRun(memeIds)

    override suspend fun resumePending() {
        val pending = indexingDao.getByStatus(MediaIndexingStatus.Pending.storageValue)
        pending.filter { it.runId != null }.forEach { item ->
            enqueueWork(item.memeId, requireNotNull(item.runId))
        }
        scheduleNewRun(pending.filter { it.runId == null }.map(MediaIndexingEntity::memeId))
    }

    override suspend fun retryFailed(): Int = scheduleNewRun(
        indexingDao.getMemeIdsByStatus(MediaIndexingStatus.Failed.storageValue)
    )

    override suspend fun reprocessAll(): Int = scheduleNewRun(memeDao.getAll().map { it.id })

    private suspend fun scheduleNewRun(memeIds: List<Long>): Int = withContext(Dispatchers.IO) {
        val distinctIds = memeIds.distinct()
        if (distinctIds.isEmpty()) return@withContext 0
        val waitingForBattery = batteryStateChecker.needsHeavyWorkConfirmation()
        val updatedAtMillis = System.currentTimeMillis()
        val runId = indexingRunDao.insert(
            MediaIndexingRunEntity(
                startedAtMillis = updatedAtMillis,
                totalCount = distinctIds.size
            )
        )
        distinctIds.forEach { memeId ->
            indexingDao.upsert(
                MediaIndexingEntity(
                    memeId = memeId,
                    status = MediaIndexingStatus.Pending.storageValue,
                    attemptCount = 0,
                    lastError = if (waitingForBattery) BATTERY_LOW_REASON else null,
                    updatedAtMillis = updatedAtMillis,
                    runId = runId
                )
            )
        }
        if (waitingForBattery) indexingNotifier.waitingForBattery()
        distinctIds.forEach { memeId -> enqueueWork(memeId, runId) }
        distinctIds.size
    }

    override fun observeStatuses(): Flow<Map<Long, MediaIndexingStatus>> =
        indexingDao.observeAll().map { entries ->
            entries.mapNotNull { entry ->
                MediaIndexingStatus.fromStorageValue(entry.status)?.let { status ->
                    entry.memeId to status
                }
            }.toMap()
        }

    override fun observeRuns(): Flow<List<MediaIndexingRun>> =
        indexingRunDao.observeRecent(HISTORY_LIMIT).map { runs ->
            runs.map { run ->
                MediaIndexingRun(
                    id = run.id,
                    startedAtMillis = run.startedAtMillis,
                    totalCount = run.totalCount,
                    completedCount = run.completedCount,
                    failedCount = run.failedCount,
                    totalDurationMillis = run.totalDurationMillis,
                    finishedAtMillis = run.finishedAtMillis
                )
            }
        }

    private fun enqueueWork(memeId: Long, runId: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(memeId),
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MediaIndexingWorker>()
                .setInputData(
                    workDataOf(
                        MediaIndexingWorker.MEME_ID_KEY to memeId,
                        MediaIndexingWorker.RUN_ID_KEY to runId
                    )
                )
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        )
    }

    companion object {
        fun workName(memeId: Long) = "media_indexing_$memeId"
        const val BATTERY_LOW_REASON = "battery_low"
        private const val BACKOFF_SECONDS = 30L
        private const val HISTORY_LIMIT = 20
    }
}
