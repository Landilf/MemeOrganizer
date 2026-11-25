package ru.landilf.memeorganizer.domain.model.meme

import java.util.Locale

data class MemeDetails(val description: String, val tags: List<String>)
data class TagSuggestion(val name: String, val usageCount: Int)

object MemeTextRules {
    const val MAX_DESCRIPTION_LENGTH = 200
    const val MAX_TAG_LENGTH = 20
    fun normalizeTag(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun validate(description: String, tags: List<String>) {
        require(description.length <= MAX_DESCRIPTION_LENGTH)
        val normalized = tags.map(::normalizeTag)
        require(tags.all { it.trim().isNotEmpty() && it.trim().length <= MAX_TAG_LENGTH })
        require(normalized.distinct().size == normalized.size)
    }

    fun suggestions(tags: List<TagSuggestion>, query: String): List<TagSuggestion> {
        val normalized = normalizeTag(query)
        if (normalized.isEmpty()) return emptyList()
        return tags.filter { normalizeTag(it.name).contains(normalized) }
            .sortedWith(compareBy<TagSuggestion> { !normalizeTag(it.name).startsWith(normalized) }
                .thenByDescending { it.usageCount }.thenBy { normalizeTag(it.name) })
    }
}
