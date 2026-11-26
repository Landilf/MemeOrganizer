package ru.landilf.memeorganizer.data.storage.entities.meme

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags", indices = [Index(value = ["normalizedName"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String
)

@Entity(
    tableName = "meme_tags", primaryKeys = ["memeId", "tagId"], foreignKeys = [
        ForeignKey(
            entity = MemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["memeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ], indices = [Index("tagId")]
)
data class MemeTagEntity(val memeId: Long, val tagId: Long)
