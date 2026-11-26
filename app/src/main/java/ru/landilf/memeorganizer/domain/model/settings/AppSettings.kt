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

enum class AutomaticBackupPeriod(val days: Long) {
    Every3Days(3),
    Weekly(7),
    Every2Weeks(14)
}

data class AppSettings(
    val librarySort: LibrarySort = LibrarySort.AddedAt,
    val librarySortDirection: SortDirection? = null,
    val showRecentlyAddedAlbum: Boolean = true,
    val showRecentlyUsedAlbum: Boolean = true,
    val automaticBackupEnabled: Boolean = false,
    val automaticBackupPeriod: AutomaticBackupPeriod = AutomaticBackupPeriod.Weekly,
    val automaticBackupDirectoryUri: String? = null,
    val automaticBackupDirectoryName: String? = null,
    val automaticBackupLastSuccessAtMillis: Long? = null,
    val automaticBackupLastError: String? = null
)
