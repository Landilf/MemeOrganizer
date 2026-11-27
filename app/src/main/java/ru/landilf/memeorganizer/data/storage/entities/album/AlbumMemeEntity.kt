package ru.landilf.memeorganizer.data.storage.entities.album

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity

@Entity(
    tableName = "album_memes",
    primaryKeys = ["albumId", "memeId"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["memeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("memeId")]
)
data class AlbumMemeEntity(
    val albumId: Long,
    val memeId: Long
)
