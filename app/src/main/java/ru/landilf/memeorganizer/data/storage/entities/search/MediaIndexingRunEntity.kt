package ru.landilf.memeorganizer.data.storage.entities.search

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_indexing_runs",
    indices = [Index("finishedAtMillis")]
)
data class MediaIndexingRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val totalCount: Int,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val totalDurationMillis: Long = 0,
    val finishedAtMillis: Long? = null
)
