package ru.landilf.memeorganizer.data.search

import kotlin.math.ln

/** Matches the mobile bundle's BM25Okapi tokenization and parameters. */
internal class SimpleBm25(private val documents: List<String>) {
    private val tokens = documents.map(::tokenize)
    private val averageLength =
        tokens.map { it.size }.average().takeIf { !it.isNaN() && it > 0.0 } ?: 1.0
    private val documentFrequency = buildMap<String, Int> {
        tokens.forEach { document ->
            document.toSet().forEach { token -> put(token, (get(token) ?: 0) + 1) }
        }
    }

    fun scores(query: String): FloatArray {
        val queryTokens = tokenize(query)
        return FloatArray(documents.size) { index ->
            val document = tokens[index]
            if (document.isEmpty()) return@FloatArray 0f
            queryTokens.sumOf { token ->
                val frequency = document.count { it == token }.toDouble()
                if (frequency == 0.0) 0.0 else {
                    val idf = ln(
                        (documents.size - (documentFrequency[token]
                            ?: 0) + 0.5) / ((documentFrequency[token] ?: 0) + 0.5) + 1.0
                    )
                    idf * frequency * (K1 + 1.0) / (frequency + K1 * (1.0 - B + B * document.size / averageLength))
                }
            }.toFloat()
        }
    }

    companion object {
        private const val K1 = 1.5
        private const val B = 0.75
        fun tokenize(text: String): List<String> =
            text.lowercase().split(Regex("[^\\p{L}\\p{N}_]+"))
                .filter { it.length > 1 }
    }
}
