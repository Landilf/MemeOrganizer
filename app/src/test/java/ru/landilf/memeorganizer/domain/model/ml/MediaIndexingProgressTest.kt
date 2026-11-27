package ru.landilf.memeorganizer.domain.model.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaIndexingProgressTest {
    @Test
    fun aggregatesActiveRunsAndEstimatesRemainingTime() {
        val progress = requireNotNull(
            MediaIndexingProgress.from(
                listOf(
                    MediaIndexingRun(
                        id = 1,
                        startedAtMillis = 100,
                        totalCount = 4,
                        completedCount = 1,
                        failedCount = 0,
                        totalDurationMillis = 1_000,
                        finishedAtMillis = null
                    ),
                    MediaIndexingRun(
                        id = 2,
                        startedAtMillis = 200,
                        totalCount = 2,
                        completedCount = 0,
                        failedCount = 1,
                        totalDurationMillis = 3_000,
                        finishedAtMillis = null
                    )
                )
            )
        )

        assertEquals(6, progress.totalCount)
        assertEquals(2, progress.processedCount)
        assertEquals(1, progress.failedCount)
        assertEquals(100, progress.startedAtMillis)
        assertEquals(2_000L, progress.averageItemDurationMillis)
        assertEquals(8_000L, progress.estimatedRemainingMillis)
    }

    @Test
    fun returnsNullWithoutActiveRuns() {
        assertNull(MediaIndexingProgress.from(emptyList()))
    }
}
