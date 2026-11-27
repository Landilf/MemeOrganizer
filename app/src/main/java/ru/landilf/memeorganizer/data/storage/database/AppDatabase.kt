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
import ru.landilf.memeorganizer.data.storage.entities.search.ExtractedTextChunkEntity
import ru.landilf.memeorganizer.data.storage.dao.ExtractedTextChunkDao
import ru.landilf.memeorganizer.data.storage.entities.search.MediaIndexingEntity
import ru.landilf.memeorganizer.data.storage.dao.MediaIndexingDao
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
        ExtractedTextChunkEntity::class,
        MediaIndexingEntity::class,
        LightLogEventEntity::class,
        AlbumEntity::class,
        AlbumMemeEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memeDao(): MemeDao
    abstract fun tagDao(): TagDao
    abstract fun textEmbeddingDao(): TextEmbeddingDao
    abstract fun extractedTextChunkDao(): ExtractedTextChunkDao
    abstract fun mediaIndexingDao(): MediaIndexingDao
    abstract fun lightLogDao(): LightLogDao
    abstract fun albumDao(): AlbumDao
}
