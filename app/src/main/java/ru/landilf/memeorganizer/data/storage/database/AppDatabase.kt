package ru.landilf.memeorganizer.data.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity

@Database(entities = [MemeEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memeDao(): MemeDao
}
