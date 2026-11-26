package ru.landilf.memeorganizer.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryExpansionTest {
    @Test
    fun `expands cyrillic laughter with cross language terms`() {
        assertEquals(
            "ахахаха смех хахаха hahaha",
            SearchQueryExpansion.expand("ахахаха").value
        )
    }

    @Test
    fun `expands latin laughter without duplicating existing terms`() {
        assertEquals(
            "hahaha смех хахаха",
            SearchQueryExpansion.expand("hahaha смех").value
        )
    }

    @Test
    fun `leaves regular query unchanged`() {
        val result = SearchQueryExpansion.expand("кот в капюшоне")

        assertEquals("кот в капюшоне", result.value)
        assertEquals("laughter-v1", result.version)
    }
}
