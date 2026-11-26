package ru.landilf.memeorganizer.domain.model.settings

enum class LibrarySort {
    AddedAt,
    UsageCount,
    LastUsed
}

data class AppSettings(
    val librarySort: LibrarySort = LibrarySort.AddedAt,
    val showRecentlyAddedAlbum: Boolean = true,
    val showRecentlyUsedAlbum: Boolean = true
)
