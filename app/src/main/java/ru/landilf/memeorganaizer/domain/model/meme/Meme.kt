package ru.landilf.memeorganaizer.domain.model.meme

import java.time.Instant

data class Meme(
    val id: String,
    val fileName: String,
    val mediaType: MemeMediaType,
    val mimeType: String,
    val addedAt: Instant,
    val contentHash: String
)
