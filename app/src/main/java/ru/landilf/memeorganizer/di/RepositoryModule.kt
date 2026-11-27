package ru.landilf.memeorganizer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.landilf.memeorganizer.data.storage.repository.MemeRepositoryImpl
import ru.landilf.memeorganizer.domain.repository.MemeRepository
import ru.landilf.memeorganizer.domain.repository.MemeDetailsRepository
import ru.landilf.memeorganizer.data.storage.repository.MemeDetailsRepositoryImpl
import ru.landilf.memeorganizer.data.storage.repository.MemeSearchRepositoryImpl
import ru.landilf.memeorganizer.domain.repository.MemeSearchRepository
import ru.landilf.memeorganizer.data.storage.repository.AlbumRepositoryImpl
import ru.landilf.memeorganizer.domain.repository.AlbumRepository
import ru.landilf.memeorganizer.data.settings.AppSettingsRepositoryImpl
import ru.landilf.memeorganizer.domain.repository.AppSettingsRepository
import ru.landilf.memeorganizer.data.backup.LibraryBackupRepositoryImpl
import ru.landilf.memeorganizer.domain.repository.LibraryBackupRepository
import ru.landilf.memeorganizer.data.storage.repository.MemeExportRepositoryImpl
import ru.landilf.memeorganizer.domain.repository.MemeExportRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMemeRepository(implementation: MemeRepositoryImpl): MemeRepository

    @Binds
    @Singleton
    abstract fun bindMemeDetailsRepository(implementation: MemeDetailsRepositoryImpl): MemeDetailsRepository

    @Binds
    @Singleton
    abstract fun bindMemeSearchRepository(implementation: MemeSearchRepositoryImpl): MemeSearchRepository

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(implementation: AlbumRepositoryImpl): AlbumRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(
        implementation: AppSettingsRepositoryImpl
    ): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindLibraryBackupRepository(
        implementation: LibraryBackupRepositoryImpl
    ): LibraryBackupRepository

    @Binds
    @Singleton
    abstract fun bindMemeExportRepository(
        implementation: MemeExportRepositoryImpl
    ): MemeExportRepository
}
