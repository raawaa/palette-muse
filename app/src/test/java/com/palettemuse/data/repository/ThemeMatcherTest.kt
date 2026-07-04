package com.palettemuse.data.repository

import com.palettemuse.core.ColorMatcher
import com.palettemuse.data.model.ThemeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the theme-match rule through its own interface — the test surface the
 * matcher exists to provide. No DB, no device state: a hand-built list of themes
 * and a sample hex.
 */
class ThemeMatcherTest {

    private val matcher = ThemeMatcher(ColorMatcher())

    private fun theme(id: String, rgb: Int) =
        ThemeEntity(id = id, name = id, representativeRgb = rgb)

    @Test
    fun bestMatch_picksClosestAboveThreshold() {
        val themes = listOf(theme("rose", 0xDCA8A6), theme("green", 0x00FF00))
        val best = matcher.bestMatch(themes, 0xDCB0A8) // near dusty rose
        assertEquals("rose", best?.theme?.id)
    }

    @Test
    fun bestMatch_nullWhenAllBelowThreshold() {
        val themes = listOf(theme("rose", 0xDCA8A6))
        val best = matcher.bestMatch(themes, 0x00FF00) // bright green, large ΔE
        assertNull(best)
    }

    @Test
    fun bestMatch_nullOnEmptyList() {
        assertNull(matcher.bestMatch(emptyList(), 0xDCA8A6))
    }

    @Test
    fun bestMatch_tieResolvesToFirstInList() {
        val a = theme("first", 0xDCA8A6)
        val b = theme("second", 0xDCA8A6)
        val best = matcher.bestMatch(listOf(a, b), 0xDCA8A6)
        assertEquals("first", best?.theme?.id)
    }

    @Test
    fun bestMatch_exactColorScoresHundred() {
        val best = matcher.bestMatch(listOf(theme("rose", 0xDCA8A6)), 0xDCA8A6)
        assertEquals(100, best?.score)
    }
}
