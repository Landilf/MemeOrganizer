package ru.landilf.memeorganizer.domain.model.settings

enum class LibrarySort {
    AddedAt,
    UsageCount,
    MediaType
}

enum class SortDirection {
    Descending,
    Ascending
}

data class AppSettings(
    val librarySort: LibrarySort = LibrarySort.AddedAt,
    val librarySortDirection: SortDirection? = null,
    val showRecentlyAddedAlbum: Boolean = true,
    val showRecentlyUsedAlbum: Boolean = true
)
