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
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.data.ml.LocalModelInstaller
import ru.landilf.memeorganizer.data.storage.dao.MediaIndexingDao
import ru.landilf.memeorganizer.data.storage.dao.MediaIndexingRunDao
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus
import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository

private const val MAX_FAILURE_MESSAGE_LENGTH = 200

class MediaIndexingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val memeId = inputData.getLong(MEME_ID_KEY, INVALID_MEME_ID)
        val runId = inputData.getLong(RUN_ID_KEY, INVALID_RUN_ID)
        if (memeId == INVALID_MEME_ID || runId == INVALID_RUN_ID) return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            MediaIndexingWorkerEntryPoint::class.java
        )
        val memeDao = dependencies.memeDao()
        val indexingDao = dependencies.indexingDao()
        val indexingRunDao = dependencies.indexingRunDao()
        val queueController = dependencies.queueController()
        val meme = memeDao.getById(memeId)
        if (meme == null) {
            indexingDao.deleteByMemeId(memeId)
            return Result.success()
        }
        val current = indexingDao.getByMemeId(memeId) ?: return Result.success()
        if (current.runId != runId) return Result.success()
        if (queueController.isPaused()) return Result.success()
        val modelPackage = dependencies.modelInstaller().activePackage()
        if (modelPackage == null) {
            AppLogger.ml { "media_indexing.waiting_for_model meme_id=$memeId" }
            return Result.success()
        }
        val startedAtMillis = System.currentTimeMillis()
        indexingDao.upsert(
            current.copy(
                status = MediaIndexingStatus.Running.storageValue,
                lastError = null,
                updatedAtMillis = startedAtMillis,
                processingStartedAtMillis = startedAtMillis
            )
        )
        notifyRunProgress(dependencies.indexingNotifier(), indexingRunDao, runId)
        return try {
            dependencies.ocrMediaProcessor().process(meme, modelPackage)
            dependencies.asrMediaProcessor().process(meme, modelPackage)
            dependencies.searchRepository().indexMeme(memeId)
            if (indexingDao.getByMemeId(memeId)?.runId != runId) return Result.success()
            val finishedAtMillis = System.currentTimeMillis()
            indexingDao.upsert(
                current.copy(
                    status = MediaIndexingStatus.Partial.storageValue,
                    attemptCount = runAttemptCount,
                    lastError = null,
                    updatedAtMillis = finishedAtMillis,
                    processingStartedAtMillis = null
                )
            )
            indexingRunDao.recordTerminalResult(
                runId = runId,
                completedIncrement = 1,
                failedIncrement = 0,
                durationMillis = finishedAtMillis - startedAtMillis,
                finishedAtMillis = finishedAtMillis
            )
            indexingRunDao.pruneFinished(HISTORY_LIMIT)
            AppLogger.ml { "media_indexing.partial meme_id=$memeId" }
            if (notifyRunProgress(dependencies.indexingNotifier(), indexingRunDao, runId)) {
                dependencies.indexingNotifier().completed()
            }
            Result.success()
        } catch (error: CancellationException) {
            val isCurrentRun = indexingDao.getByMemeId(memeId)?.runId == runId
            val waitingReason = MediaIndexingRepositoryImpl.PAUSED_REASON
                .takeIf { queueController.isPaused() }
            if (isCurrentRun && waitingReason != null) {
                withContext<Unit>(NonCancellable) {
                    indexingDao.upsert(
                        current.copy(
                            status = MediaIndexingStatus.Pending.storageValue,
                            attemptCount = runAttemptCount,
                            lastError = waitingReason,
                            updatedAtMillis = System.currentTimeMillis(),
                            processingStartedAtMillis = null
                        )
                    )
                }
            }
            throw error
        } catch (error: Exception) {
            if (indexingDao.getByMemeId(memeId)?.runId != runId) return Result.success()
            val attempts = runAttemptCount + 1
            val finalFailure = attempts >= MAX_ATTEMPTS
            val finishedAtMillis = System.currentTimeMillis()
            indexingDao.upsert(
                current.copy(
                    status = if (finalFailure) {
                        MediaIndexingStatus.Failed.storageValue
                    } else {
                        MediaIndexingStatus.Pending.storageValue
                    },
                    attemptCount = attempts,
                    lastError = error.toFailureReason(),
                    updatedAtMillis = finishedAtMillis,
                    processingStartedAtMillis = null
                )
            )
            if (finalFailure) {
                indexingRunDao.recordTerminalResult(
                    runId = runId,
                    completedIncrement = 0,
                    failedIncrement = 1,
                    durationMillis = finishedAtMillis - startedAtMillis,
                    finishedAtMillis = finishedAtMillis
                )
                indexingRunDao.pruneFinished(HISTORY_LIMIT)
            }
            AppLogger.mlError(error) { "media_indexing.failed meme_id=$memeId attempts=$attempts" }
            if (finalFailure && notifyRunProgress(dependencies.indexingNotifier(), indexingRunDao, runId)) {
                dependencies.indexingNotifier().failed()
            }
            if (finalFailure) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val MEME_ID_KEY = "meme_id"
        const val RUN_ID_KEY = "run_id"
        private const val INVALID_MEME_ID = -1L
        private const val INVALID_RUN_ID = -1L
        private const val MAX_ATTEMPTS = 3
        private const val HISTORY_LIMIT = 20
    }
}

private suspend fun notifyRunProgress(
    notifier: IndexingNotifier,
    indexingRunDao: MediaIndexingRunDao,
    runId: Long
): Boolean {
    val run = indexingRunDao.getById(runId) ?: return false
    notifier.showProgress(
        completedCount = run.completedCount + run.failedCount,
        totalCount = run.totalCount
    )
    return run.finishedAtMillis != null
}

private fun Throwable.toFailureReason(): String = buildString {
    append(javaClass.simpleName)
    message?.take(MAX_FAILURE_MESSAGE_LENGTH)?.takeIf(String::isNotBlank)?.let { detail ->
        append(": ")
        append(detail)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaIndexingWorkerEntryPoint {
    fun memeDao(): MemeDao
    fun indexingDao(): MediaIndexingDao
    fun indexingRunDao(): MediaIndexingRunDao
    fun modelInstaller(): LocalModelInstaller
    fun searchRepository(): MemeSearchRepository
    fun indexingNotifier(): IndexingNotifier
    fun queueController(): IndexingQueueController
    fun ocrMediaProcessor(): OcrMediaProcessor
    fun asrMediaProcessor(): AsrMediaProcessor
}
