package ru.landilf.memeorganizer.domain.repository

import android.net.Uri
import ru.landilf.memeorganizer.domain.model.backup.LibraryBackupSummary
import ru.landilf.memeorganizer.domain.model.backup.LibraryRestoreSummary

interface LibraryBackupRepository {
    suspend fun create(destination: Uri): LibraryBackupSummary
    suspend fun createInDirectory(directory: Uri): LibraryBackupSummary
    suspend fun restore(source: Uri): LibraryRestoreSummary
}
