package ru.landilf.memeorganizer.domain.model.album

import java.time.Instant
import java.util.Locale

data class Album(
    val id: Long,
    val name: String,
    val createdAt: Instant
)

object AlbumNameRules {
    fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun validate(name: String) {
        require(name.isNotBlank())
    }
}
