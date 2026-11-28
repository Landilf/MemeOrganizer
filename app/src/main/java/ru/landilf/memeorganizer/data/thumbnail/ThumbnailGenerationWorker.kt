package ru.landilf.memeorganizer.data.thumbnail

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ThumbnailGenerationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val cache = EntryPointAccessors.fromApplication(
            applicationContext,
            ThumbnailGenerationWorkerEntryPoint::class.java
        ).thumbnailCache()
        cache.generateMissing()
        return Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThumbnailGenerationWorkerEntryPoint {
    fun thumbnailCache(): ThumbnailCache
}
