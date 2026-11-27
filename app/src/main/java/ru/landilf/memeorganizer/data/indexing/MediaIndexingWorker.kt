package ru.landilf.memeorganizer.data.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.landilf.memeorganizer.data.device.BatteryStateChecker
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.data.ml.LocalModelInstaller
import ru.landilf.memeorganizer.data.storage.dao.MediaIndexingDao
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.entities.search.MediaIndexingEntity
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus
import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository

class MediaIndexingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val memeId = inputData.getLong(MEME_ID_KEY, INVALID_MEME_ID)
        if (memeId == INVALID_MEME_ID) return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            MediaIndexingWorkerEntryPoint::class.java
        )
        val memeDao = dependencies.memeDao()
        val indexingDao = dependencies.indexingDao()
        if (memeDao.getById(memeId) == null) {
            indexingDao.deleteByMemeId(memeId)
            return Result.success()
        }
        if (dependencies.modelInstaller().activePackage() == null) {
            AppLogger.ml { "media_indexing.waiting_for_model meme_id=$memeId" }
            return Result.success()
        }
        val wasWaitingForBattery = indexingDao.getByMemeId(memeId)?.lastError ==
            MediaIndexingRepositoryImpl.BATTERY_LOW_REASON
        if (wasWaitingForBattery) dependencies.indexingNotifier().resumed()
        return try {
            dependencies.searchRepository().indexMeme(memeId)
            indexingDao.upsert(
                state(
                    memeId = memeId,
                    status = MediaIndexingStatus.Partial,
                    attemptCount = runAttemptCount,
                    lastError = null
                )
            )
            AppLogger.ml { "media_indexing.partial meme_id=$memeId" }
            dependencies.indexingNotifier().completed()
            Result.success()
        } catch (error: CancellationException) {
            if (dependencies.batteryStateChecker().needsHeavyWorkConfirmation()) {
                withContext<Unit>(NonCancellable) {
                    indexingDao.upsert(
                        state(
                            memeId = memeId,
                            status = MediaIndexingStatus.Pending,
                            attemptCount = runAttemptCount,
                            lastError = MediaIndexingRepositoryImpl.BATTERY_LOW_REASON
                        )
                    )
                }
                dependencies.indexingNotifier().waitingForBattery()
            }
            throw error
        } catch (error: Exception) {
            val attempts = runAttemptCount + 1
            val finalFailure = attempts >= MAX_ATTEMPTS
            indexingDao.upsert(
                state(
                    memeId = memeId,
                    status = if (finalFailure) MediaIndexingStatus.Failed else MediaIndexingStatus.Pending,
                    attemptCount = attempts,
                    lastError = error.javaClass.simpleName
                )
            )
            AppLogger.mlError(error) { "media_indexing.failed meme_id=$memeId attempts=$attempts" }
            if (finalFailure) dependencies.indexingNotifier().failed()
            if (finalFailure) Result.failure() else Result.retry()
        }
    }

    private fun state(
        memeId: Long,
        status: MediaIndexingStatus,
        attemptCount: Int,
        lastError: String?
    ) = MediaIndexingEntity(
        memeId = memeId,
        status = status.storageValue,
        attemptCount = attemptCount,
        lastError = lastError,
        updatedAtMillis = System.currentTimeMillis()
    )

    companion object {
        const val MEME_ID_KEY = "meme_id"
        private const val INVALID_MEME_ID = -1L
        private const val MAX_ATTEMPTS = 3
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaIndexingWorkerEntryPoint {
    fun memeDao(): MemeDao
    fun indexingDao(): MediaIndexingDao
    fun modelInstaller(): LocalModelInstaller
    fun searchRepository(): MemeSearchRepository
    fun batteryStateChecker(): BatteryStateChecker
    fun indexingNotifier(): IndexingNotifier
}
