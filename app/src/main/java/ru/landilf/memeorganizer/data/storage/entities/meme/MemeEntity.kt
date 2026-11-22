package ru.landilf.memeorganizer.data.storage.entities.meme

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "memes", indices = [Index(value = ["contentHash"], unique = true)])
data class MemeEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val mediaType: String,
    val mimeType: String,
    val addedAtMillis: Long,
    val contentHash: String,
    val storedFileName: String
)
