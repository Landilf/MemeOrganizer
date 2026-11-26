package ru.landilf.memeorganizer.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.landilf.memeorganizer.domain.model.settings.AppSettings
import ru.landilf.memeorganizer.domain.model.settings.AutomaticBackupPeriod
import ru.landilf.memeorganizer.domain.model.settings.LibrarySort
import ru.landilf.memeorganizer.domain.model.settings.SortDirection
import ru.landilf.memeorganizer.domain.repository.AppSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore by preferencesDataStore("app_settings")

@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppSettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = context.appSettingsDataStore.data.map { values ->
        AppSettings(
            librarySort = values[LIBRARY_SORT]
                ?.let { name -> LibrarySort.entries.firstOrNull { it.name == name } }
                ?: LibrarySort.AddedAt,
            librarySortDirection = values[LIBRARY_SORT_DIRECTION]
                ?.let { name -> SortDirection.entries.firstOrNull { it.name == name } },
            showRecentlyAddedAlbum = values[SHOW_RECENTLY_ADDED] ?: true,
            showRecentlyUsedAlbum = values[SHOW_RECENTLY_USED] ?: true,
            automaticBackupEnabled = values[AUTOMATIC_BACKUP_ENABLED] ?: false,
            automaticBackupPeriod = values[AUTOMATIC_BACKUP_PERIOD_DAYS]
                ?.let { days -> AutomaticBackupPeriod.entries.firstOrNull { it.days == days.toLong() } }
                ?: AutomaticBackupPeriod.Weekly,
            automaticBackupDirectoryUri = values[AUTOMATIC_BACKUP_DIRECTORY_URI],
            automaticBackupDirectoryName = values[AUTOMATIC_BACKUP_DIRECTORY_NAME],
            automaticBackupLastSuccessAtMillis = values[AUTOMATIC_BACKUP_LAST_SUCCESS],
            automaticBackupLastError = values[AUTOMATIC_BACKUP_LAST_ERROR]
        )
    }

    override suspend fun setLibrarySort(sort: LibrarySort) {
        context.appSettingsDataStore.edit { it[LIBRARY_SORT] = sort.name }
    }

    override suspend fun setLibrarySortDirection(direction: SortDirection) {
        context.appSettingsDataStore.edit { it[LIBRARY_SORT_DIRECTION] = direction.name }
    }

    override suspend fun setRecentlyAddedAlbumVisible(visible: Boolean) {
        context.appSettingsDataStore.edit { it[SHOW_RECENTLY_ADDED] = visible }
    }

    override suspend fun setRecentlyUsedAlbumVisible(visible: Boolean) {
        context.appSettingsDataStore.edit { it[SHOW_RECENTLY_USED] = visible }
    }

    override suspend fun setAutomaticBackupEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[AUTOMATIC_BACKUP_ENABLED] = enabled }
    }

    override suspend fun setAutomaticBackupPeriod(period: AutomaticBackupPeriod) {
        context.appSettingsDataStore.edit { it[AUTOMATIC_BACKUP_PERIOD_DAYS] = period.days.toInt() }
    }

    override suspend fun setAutomaticBackupDirectory(uri: String, name: String?) {
        context.appSettingsDataStore.edit {
            it[AUTOMATIC_BACKUP_DIRECTORY_URI] = uri
            if (name == null) it.remove(AUTOMATIC_BACKUP_DIRECTORY_NAME)
            else it[AUTOMATIC_BACKUP_DIRECTORY_NAME] = name
        }
    }

    override suspend fun recordAutomaticBackupSuccess(atMillis: Long) {
        context.appSettingsDataStore.edit {
            it[AUTOMATIC_BACKUP_LAST_SUCCESS] = atMillis
            it.remove(AUTOMATIC_BACKUP_LAST_ERROR)
        }
    }

    override suspend fun recordAutomaticBackupError(error: String) {
        context.appSettingsDataStore.edit { it[AUTOMATIC_BACKUP_LAST_ERROR] = error }
    }

    override suspend fun clearAutomaticBackupStatus() {
        context.appSettingsDataStore.edit {
            it.remove(AUTOMATIC_BACKUP_LAST_SUCCESS)
            it.remove(AUTOMATIC_BACKUP_LAST_ERROR)
        }
    }

    private companion object {
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val LIBRARY_SORT_DIRECTION = stringPreferencesKey("library_sort_direction")
        val SHOW_RECENTLY_ADDED = booleanPreferencesKey("show_recently_added_album")
        val SHOW_RECENTLY_USED = booleanPreferencesKey("show_recently_used_album")
        val AUTOMATIC_BACKUP_ENABLED = booleanPreferencesKey("automatic_backup_enabled")
        val AUTOMATIC_BACKUP_PERIOD_DAYS = intPreferencesKey("automatic_backup_period_days")
        val AUTOMATIC_BACKUP_DIRECTORY_URI = stringPreferencesKey("automatic_backup_directory_uri")
        val AUTOMATIC_BACKUP_DIRECTORY_NAME = stringPreferencesKey("automatic_backup_directory_name")
        val AUTOMATIC_BACKUP_LAST_SUCCESS = longPreferencesKey("automatic_backup_last_success")
        val AUTOMATIC_BACKUP_LAST_ERROR = stringPreferencesKey("automatic_backup_last_error")
    }
}
