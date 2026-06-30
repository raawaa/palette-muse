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

    private fun theme(id: String, hex: String) =
        ThemeEntity(id = id, name = id, representativeHex = hex)

    @Test
    fun bestMatch_picksClosestAboveThreshold() {
        val themes = listOf(theme("rose", "#DCA8A6"), theme("green", "#00FF00"))
        val best = matcher.bestMatch(themes, "#DCB0A8") // near dusty rose
        assertEquals("rose", best?.theme?.id)
    }

    @Test
    fun bestMatch_nullWhenAllBelowThreshold() {
        val themes = listOf(theme("rose", "#DCA8A6"))
        val best = matcher.bestMatch(themes, "#00FF00") // bright green, large ΔE
        assertNull(best)
    }

    @Test
    fun bestMatch_nullOnEmptyList() {
        assertNull(matcher.bestMatch(emptyList(), "#DCA8A6"))
    }

    @Test
    fun bestMatch_tieResolvesToFirstInList() {
        val a = theme("first", "#DCA8A6")
        val b = theme("second", "#DCA8A6")
        val best = matcher.bestMatch(listOf(a, b), "#DCA8A6")
        assertEquals("first", best?.theme?.id)
    }

    @Test
    fun bestMatch_exactColorScoresHundred() {
        val best = matcher.bestMatch(listOf(theme("rose", "#DCA8A6")), "#DCA8A6")
        assertEquals(100, best?.score)
    }
}
