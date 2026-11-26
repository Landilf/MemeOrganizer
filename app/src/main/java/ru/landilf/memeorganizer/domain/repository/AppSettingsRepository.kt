package ru.landilf.memeorganizer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.settings.AppSettings
import ru.landilf.memeorganizer.domain.model.settings.LibrarySort
import ru.landilf.memeorganizer.domain.model.settings.SortDirection

interface AppSettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun setLibrarySort(sort: LibrarySort)
    suspend fun setLibrarySortDirection(direction: SortDirection)
    suspend fun setRecentlyAddedAlbumVisible(visible: Boolean)
    suspend fun setRecentlyUsedAlbumVisible(visible: Boolean)
}
