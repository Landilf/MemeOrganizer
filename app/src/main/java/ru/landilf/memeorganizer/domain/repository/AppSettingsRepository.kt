package ru.landilf.memeorganizer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.landilf.memeorganizer.domain.model.settings.AppSettings
import ru.landilf.memeorganizer.domain.model.settings.AutomaticBackupPeriod
import ru.landilf.memeorganizer.domain.model.settings.LibrarySort
import ru.landilf.memeorganizer.domain.model.settings.SortDirection

interface AppSettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun setLibrarySort(sort: LibrarySort)
    suspend fun setLibrarySortDirection(direction: SortDirection)
    suspend fun setRecentlyAddedAlbumVisible(visible: Boolean)
    suspend fun setRecentlyUsedAlbumVisible(visible: Boolean)
    suspend fun setAutomaticBackupEnabled(enabled: Boolean)
    suspend fun setAutomaticBackupPeriod(period: AutomaticBackupPeriod)
    suspend fun setAutomaticBackupDirectory(uri: String, name: String?)
    suspend fun recordAutomaticBackupSuccess(atMillis: Long)
    suspend fun recordAutomaticBackupError(error: String)
    suspend fun clearAutomaticBackupStatus()
}
