package ru.landilf.memeorganizer.data.indexing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.indexingQueueDataStore by preferencesDataStore("indexing_queue")

@Singleton
class IndexingQueueController @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val paused: Flow<Boolean> = context.indexingQueueDataStore.data.map { preferences ->
        preferences[PAUSED] ?: false
    }

    suspend fun isPaused(): Boolean = paused.first()

    suspend fun setPaused(value: Boolean) {
        context.indexingQueueDataStore.edit { preferences -> preferences[PAUSED] = value }
    }

    private companion object {
        val PAUSED = booleanPreferencesKey("paused")
    }
}
