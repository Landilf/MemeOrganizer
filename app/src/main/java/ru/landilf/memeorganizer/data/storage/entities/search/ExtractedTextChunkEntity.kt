package ru.landilf.memeorganizer.data.storage.entities.search

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity

/**
 * Text derived from media rather than entered by the user. OCR and ASR share this table so both
 * can be associated with a video timestamp and independently replaced after a model update.
 */
@Entity(
    tableName = "extracted_text_chunks",
    foreignKeys = [ForeignKey(
        entity = MemeEntity::class,
        parentColumns = ["id"],
        childColumns = ["memeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("memeId"), Index(value = ["memeId", "source", "modelReleaseTag"])]
)
data class ExtractedTextChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memeId: Long,
    val source: String,
    val timestampMs: Long?,
    val text: String,
    val modelReleaseTag: String,
    val createdAtMillis: Long
)
