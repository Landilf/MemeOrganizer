package ru.landilf.memeorganaizer.data.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.landilf.memeorganaizer.data.storage.dao.MemeDao
import ru.landilf.memeorganaizer.data.storage.entities.meme.MemeEntity

@Database(entities = [MemeEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memeDao(): MemeDao
}
