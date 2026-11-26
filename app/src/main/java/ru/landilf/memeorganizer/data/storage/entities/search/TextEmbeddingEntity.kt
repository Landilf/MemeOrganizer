package ru.landilf.memeorganizer.data.storage.entities.search

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity

/**
 * Derived ML data. It may be safely replaced whenever a text-model package changes.
 */
@Entity(
    tableName = "text_embeddings",
    foreignKeys = [ForeignKey(
        entity = MemeEntity::class,
        parentColumns = ["id"],
        childColumns = ["memeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("modelSha256")]
)
data class TextEmbeddingEntity(
    @PrimaryKey val memeId: Long,
    val modelSha256: String,
    val contentFingerprint: String,
    val embedding: ByteArray,
    val indexedAtMillis: Long
)
