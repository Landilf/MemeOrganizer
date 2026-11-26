package ru.landilf.memeorganizer.data.storage.entities.meme

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "memes", indices = [Index(value = ["contentHash"], unique = true)])
data class MemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val mediaType: String,
    val mimeType: String,
    val addedAtMillis: Long,
    val contentHash: String,
    val storedFileName: String,
    @androidx.room.ColumnInfo(defaultValue = "''") val description: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    val lastUsedAtMillis: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val usageCount: Long = 0
)
