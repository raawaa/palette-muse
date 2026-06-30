package com.palettemuse.data.repository

import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-function tests for `buildSwatches` — extracted from ThemeRepository so the
 * rule can be exercised on the JVM without spinning up Room. See issue #5.
 *
 * NOTE on the "standout" semantic gap: CONTEXT.md describes swatches as
 * "representative color plus up to two standout colors actually captured into
 * it". The current implementation picks the first two distinct non-representative
 * dominant hexes in insertion order — which may or may not equal "standout".
 * These tests pin the current behavior; a follow-up issue tracks whether
 * "standout" deserves a richer definition (e.g. by match score, recency, or
 * perceptual distance from the representative).
 */
class SwatchesTest {

    private val rose = theme(rep = "#DCA8A6")

    @Test
    fun onlyRepresentative_returnsSingleton() {
        // Gap: a theme with only its seed photo (rep == only dominantHex)
        // collapses to a single-element swatch list.
        val result = buildSwatches(rose, photos("#DCA8A6"))
        assertEquals(listOf("#DCA8A6"), result)
    }

    @Test
    fun distinctCaptured_appendedAfterRepresentative() {
        // Gap: "appended" here means "in photo insertion order". If standout
        // were defined by perceptual salience, the order could differ.
        val result = buildSwatches(rose, photos("#C99A92"))
        assertEquals(listOf("#DCA8A6", "#C99A92"), result)
    }

    @Test
    fun atMostTwoCaptured_takeTwo() {
        // Gap: the cap is hard-coded at 2 because CONTEXT.md says "up to two".
        // Whether "up to two" should depend on theme maturity or visual spread
        // is open — pinned here as a literal `take(2)`.
        val result = buildSwatches(
            rose,
            photos("#C99A92", "#B07A72", "#8E5A52", "#6E3A32")
        )
        assertEquals(listOf("#DCA8A6", "#C99A92", "#B07A72"), result)
    }

    @Test
    fun capturedEqualToRepresentative_filtered() {
        // Gap: photos whose dominant equals the representative are intentionally
        // dropped. This means a "all-same-color" theme still swatches to one.
        val result = buildSwatches(
            rose,
            photos("#DCA8A6", "#DCA8A6", "#DCA8A6")
        )
        assertEquals(listOf("#DCA8A6"), result)
    }

    private fun theme(rep: String, id: String = "t1") =
        ThemeEntity(id = id, name = "rose", representativeHex = rep)

    private fun photos(vararg hexes: String): List<PhotoEntity> = hexes.map { hex ->
        PhotoEntity(themeId = "t1", imagePath = "/p.jpg", dominantHex = hex)
    }
}
