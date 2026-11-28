package ru.landilf.memeorganizer.data.thumbnail

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.landilf.memeorganizer.domain.repository.MemeThumbnailRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemeThumbnailRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val thumbnailCache: ThumbnailCache
) : MemeThumbnailRepository {
    override suspend fun scheduleMissing() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ThumbnailGenerationWorker>().build()
        )
    }

    override suspend fun delete(storedFileName: String) {
        thumbnailCache.delete(storedFileName)
    }

    private companion object {
        const val WORK_NAME = "thumbnail_generation"
    }
}
