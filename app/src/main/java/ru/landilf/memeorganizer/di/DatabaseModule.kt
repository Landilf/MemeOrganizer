package ru.landilf.memeorganizer.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.dao.TextEmbeddingDao
import ru.landilf.memeorganizer.data.storage.dao.LightLogDao
import ru.landilf.memeorganizer.data.storage.dao.AlbumDao
import ru.landilf.memeorganizer.data.storage.database.AppDatabase
import ru.landilf.memeorganizer.data.storage.database.DatabaseMigrations
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "meme_organizer.db")
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
                DatabaseMigrations.MIGRATION_3_4,
                DatabaseMigrations.MIGRATION_4_5,
                DatabaseMigrations.MIGRATION_5_6,
                DatabaseMigrations.MIGRATION_6_7
            )
            .build()

    @Provides
    fun provideMemeDao(database: AppDatabase): MemeDao = database.memeDao()

    @Provides
    fun provideTextEmbeddingDao(database: AppDatabase): TextEmbeddingDao =
        database.textEmbeddingDao()

    @Provides
    fun provideLightLogDao(database: AppDatabase): LightLogDao = database.lightLogDao()

    @Provides
    fun provideAlbumDao(database: AppDatabase): AlbumDao = database.albumDao()
}
