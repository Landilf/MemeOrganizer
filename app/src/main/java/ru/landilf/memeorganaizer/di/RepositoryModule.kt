package ru.landilf.memeorganaizer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.landilf.memeorganaizer.data.storage.repository.MemeRepositoryImpl
import ru.landilf.memeorganaizer.domain.repository.MemeRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMemeRepository(implementation: MemeRepositoryImpl): MemeRepository
}
