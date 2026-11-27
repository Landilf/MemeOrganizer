package ru.landilf.memeorganizer.data.storage.entities.search

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity

/** Derived processing state. Deleting a meme removes its pending background work state as well. */
@Entity(
    tableName = "media_indexing",
    foreignKeys = [ForeignKey(
        entity = MemeEntity::class,
        parentColumns = ["id"],
        childColumns = ["memeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("status")]
)
data class MediaIndexingEntity(
    @PrimaryKey val memeId: Long,
    val status: String,
    val attemptCount: Int,
    val lastError: String?,
    val updatedAtMillis: Long,
    val runId: Long? = null,
    val processingStartedAtMillis: Long? = null
)
