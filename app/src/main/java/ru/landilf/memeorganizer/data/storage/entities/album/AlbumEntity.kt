package ru.landilf.memeorganizer.data.storage.entities.album

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "albums", indices = [Index(value = ["normalizedName"], unique = true)])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val createdAtMillis: Long
)
