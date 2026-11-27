package ru.landilf.memeorganizer.domain.model.backup

data class LibraryBackupSummary(
    val memeCount: Int,
    val albumCount: Int
)

data class LibraryRestoreSummary(
    val memeCount: Int,
    val albumCount: Int
)
