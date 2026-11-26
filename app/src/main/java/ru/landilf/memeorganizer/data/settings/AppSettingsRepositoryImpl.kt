package ru.landilf.memeorganizer.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.landilf.memeorganizer.domain.model.settings.AppSettings
import ru.landilf.memeorganizer.domain.model.settings.LibrarySort
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
            showRecentlyAddedAlbum = values[SHOW_RECENTLY_ADDED] ?: true,
            showRecentlyUsedAlbum = values[SHOW_RECENTLY_USED] ?: true
        )
    }

    override suspend fun setLibrarySort(sort: LibrarySort) {
        context.appSettingsDataStore.edit { it[LIBRARY_SORT] = sort.name }
    }

    override suspend fun setRecentlyAddedAlbumVisible(visible: Boolean) {
        context.appSettingsDataStore.edit { it[SHOW_RECENTLY_ADDED] = visible }
    }

    override suspend fun setRecentlyUsedAlbumVisible(visible: Boolean) {
        context.appSettingsDataStore.edit { it[SHOW_RECENTLY_USED] = visible }
    }

    private companion object {
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val SHOW_RECENTLY_ADDED = booleanPreferencesKey("show_recently_added_album")
        val SHOW_RECENTLY_USED = booleanPreferencesKey("show_recently_used_album")
    }
}
