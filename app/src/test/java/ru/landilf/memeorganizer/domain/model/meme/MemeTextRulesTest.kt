package ru.landilf.memeorganizer.domain.model.meme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MemeTextRulesTest {
    @Test fun `matches tags ignoring case and surrounding whitespace`() {
        assertEquals(MemeTextRules.normalizeTag(" КОт "), MemeTextRules.normalizeTag("кот"))
        assertThrows(IllegalArgumentException::class.java) { MemeTextRules.validate("", listOf(" КОт ", "кот")) }
    }

    @Test fun `enforces description and tag limits`() {
        MemeTextRules.validate("a".repeat(200), listOf("b".repeat(20)))
        assertThrows(IllegalArgumentException::class.java) { MemeTextRules.validate("a".repeat(201), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { MemeTextRules.validate("", listOf("b".repeat(21))) }
        assertThrows(IllegalArgumentException::class.java) { MemeTextRules.validate("", listOf("  ")) }
    }

    @Test fun `sorts prefix matches before substring matches by usage then alphabet`() {
        val tags = listOf(TagSuggestion("Супер кот", 100), TagSuggestion("Котик", 2), TagSuggestion("Кот", 2), TagSuggestion("Котёнок", 5))
        assertEquals(listOf("Котёнок", "Кот", "Котик", "Супер кот"), MemeTextRules.suggestions(tags, " КОТ ").map { it.name })
    }
}
