package ru.landilf.memeorganizer.data.search

internal data class ExpandedSearchQuery(
    val value: String,
    val version: String
)

internal object SearchQueryExpansion {
    const val VERSION = "laughter-v1"
    private val canonicalLaughterTerms = listOf("смех", "хахаха", "hahaha")
    private val cyrillicLaughter = Regex("^(?:а?ха)+$", RegexOption.IGNORE_CASE)
    private val latinLaughter = Regex("^(?:a?ha)+$", RegexOption.IGNORE_CASE)

    fun expand(query: String): ExpandedSearchQuery {
        val tokens = SimpleBm25.tokenize(query)
        val containsLaughter = tokens.any { token ->
            cyrillicLaughter.matches(token) || latinLaughter.matches(token)
        }
        if (!containsLaughter) return ExpandedSearchQuery(query, VERSION)

        val additions = canonicalLaughterTerms.filterNot(tokens::contains)
        return ExpandedSearchQuery(
            value = (listOf(query.trim()) + additions).joinToString(" "),
            version = VERSION
        )
    }
}
