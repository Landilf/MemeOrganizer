package ru.landilf.memeorganizer.data.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.TagEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeTagEntity
import ru.landilf.memeorganizer.data.storage.dao.TagDao
import ru.landilf.memeorganizer.data.storage.dao.TextEmbeddingDao
import ru.landilf.memeorganizer.data.storage.entities.search.TextEmbeddingEntity
import ru.landilf.memeorganizer.data.storage.entities.logging.LightLogEventEntity
import ru.landilf.memeorganizer.data.storage.dao.LightLogDao
import ru.landilf.memeorganizer.data.storage.dao.AlbumDao
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumEntity
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumMemeEntity

@Database(
    entities = [
        MemeEntity::class,
        TagEntity::class,
        MemeTagEntity::class,
        TextEmbeddingEntity::class,
        LightLogEventEntity::class,
        AlbumEntity::class,
        AlbumMemeEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memeDao(): MemeDao
    abstract fun tagDao(): TagDao
    abstract fun textEmbeddingDao(): TextEmbeddingDao
    abstract fun lightLogDao(): LightLogDao
    abstract fun albumDao(): AlbumDao
}
