package ru.landilf.memeorganizer.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.landilf.memeorganizer.domain.model.settings.AppSettings
import ru.landilf.memeorganizer.domain.model.settings.LibrarySort
import ru.landilf.memeorganizer.domain.model.settings.SortDirection
import ru.landilf.memeorganizer.domain.repository.AppSettingsRepository
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository
) : ViewModel() {
    val settings = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setLibrarySort(sort: LibrarySort) {
        viewModelScope.launch { repository.setLibrarySort(sort) }
    }

    fun setLibrarySortDirection(direction: SortDirection) {
        viewModelScope.launch { repository.setLibrarySortDirection(direction) }
    }

    fun setRecentlyAddedAlbumVisible(visible: Boolean) {
        viewModelScope.launch { repository.setRecentlyAddedAlbumVisible(visible) }
    }

    fun setRecentlyUsedAlbumVisible(visible: Boolean) {
        viewModelScope.launch { repository.setRecentlyUsedAlbumVisible(visible) }
    }
}
