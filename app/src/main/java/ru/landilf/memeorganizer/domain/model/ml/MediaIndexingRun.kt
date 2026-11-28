package ru.landilf.memeorganizer.domain.model.ml

data class MediaIndexingRun(
    val id: Long,
    val startedAtMillis: Long,
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val totalDurationMillis: Long,
    val finishedAtMillis: Long?,
    val pausedAtMillis: Long? = null,
    val totalPausedDurationMillis: Long = 0
) {
    val processedCount: Int get() = completedCount + failedCount
    val isActive: Boolean get() = finishedAtMillis == null
    val averageItemDurationMillis: Long?
        get() = totalDurationMillis.takeIf { processedCount > 0 }?.div(processedCount)

    fun elapsedMillis(nowMillis: Long): Long {
        val endMillis = finishedAtMillis ?: nowMillis
        val ongoingPause = pausedAtMillis?.let { nowMillis - it } ?: 0
        return (endMillis - startedAtMillis - totalPausedDurationMillis - ongoingPause)
            .coerceAtLeast(0)
    }
}

data class MediaIndexingProgress(
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val startedAtMillis: Long,
    val totalDurationMillis: Long,
    val pausedAtMillis: Long? = null,
    val totalPausedDurationMillis: Long = 0
) {
    val processedCount: Int get() = completedCount + failedCount
    val remainingCount: Int get() = (totalCount - processedCount).coerceAtLeast(0)
    val fraction: Float get() = if (totalCount == 0) 0f else processedCount.toFloat() / totalCount
    val averageItemDurationMillis: Long?
        get() = totalDurationMillis.takeIf { processedCount > 0 }?.div(processedCount)
    val estimatedRemainingMillis: Long?
        get() = averageItemDurationMillis?.times(remainingCount)
    val isPaused: Boolean get() = pausedAtMillis != null

    fun elapsedMillis(nowMillis: Long): Long {
        val ongoingPause = pausedAtMillis?.let { nowMillis - it } ?: 0
        return (nowMillis - startedAtMillis - totalPausedDurationMillis - ongoingPause)
            .coerceAtLeast(0)
    }

    companion object {
        fun from(runs: List<MediaIndexingRun>): MediaIndexingProgress? {
            if (runs.isEmpty()) return null
            return MediaIndexingProgress(
                totalCount = runs.sumOf(MediaIndexingRun::totalCount),
                completedCount = runs.sumOf(MediaIndexingRun::completedCount),
                failedCount = runs.sumOf(MediaIndexingRun::failedCount),
                startedAtMillis = runs.minOf(MediaIndexingRun::startedAtMillis),
                totalDurationMillis = runs.sumOf(MediaIndexingRun::totalDurationMillis),
                pausedAtMillis = runs.mapNotNull(MediaIndexingRun::pausedAtMillis).minOrNull(),
                totalPausedDurationMillis = runs.maxOf(MediaIndexingRun::totalPausedDurationMillis)
            )
        }
    }
}
