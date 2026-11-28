package ru.landilf.memeorganizer.domain.repository

interface MemeThumbnailRepository {
    suspend fun scheduleMissing()
    suspend fun delete(storedFileName: String)
}
