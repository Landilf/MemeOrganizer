package ru.landilf.memeorganizer.data.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.TagEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeTagEntity
import ru.landilf.memeorganizer.data.storage.dao.TagDao

@Database(entities = [MemeEntity::class, TagEntity::class, MemeTagEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memeDao(): MemeDao
    abstract fun tagDao(): TagDao
}
