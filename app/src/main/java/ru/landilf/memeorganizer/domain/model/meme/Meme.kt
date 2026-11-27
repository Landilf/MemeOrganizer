package ru.landilf.memeorganizer.domain.model.meme

import java.time.Instant

data class Meme(
    val id: Long,
    val fileName: String,
    val mediaType: MemeMediaType,
    val mimeType: String,
    val addedAt: Instant,
    val contentHash: String,
    val storedFileName: String,
    val isFavorite: Boolean = false,
    val lastUsedAt: Instant? = null,
    val usageCount: Long = 0
)
