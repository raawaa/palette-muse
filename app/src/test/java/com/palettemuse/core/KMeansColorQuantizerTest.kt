package com.palettemuse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [kMeansQuantize]. The quantizer takes an `IntArray`
 * of ARGB pixels (no `android.graphics.Bitmap`) so it is testable in the JVM
 * source set — per ADR-0002's top-level-fn testability pattern and ADR-0006's
 * "core/ is pure Kotlin" constraint.
 *
 * The pivotal case is [warmTones_doNotCollapseIntoOneSwatch] — the regression
 * that motivated replacing `androidx.palette` (ADR-0017). Palette's median-cut
 * merges 5 adjacent brown clusters into one oversized swatch, inflating
 * `populationShare` to ~0.5 and producing false-negative confidence verdicts.
 * k-means must keep the 5 clusters separate so the top share stays at ~0.2.
 */
class KMeansColorQuantizerTest {

    @Test
    fun emptyPixels_returnsEmptyList() {
        assertEquals(emptyList<ColorSwatch>(), kMeansQuantize(IntArray(0), k = 12))
    }

    @Test
    fun kIsZeroOrNegative_returnsEmptyList() {
        val pixels = intArrayOf(0xFF808080.toInt())
        assertEquals(emptyList<ColorSwatch>(), kMeansQuantize(pixels, k = 0))
        assertEquals(emptyList<ColorSwatch>(), kMeansQuantize(pixels, k = -1))
    }

    @Test
    fun solidColor_collapsesToSingleSwatch() {
        // A solid bitmap feeds one distinct color; k-means must reduce it to a
        // single swatch carrying the full population. Downstream, the ratio
        // helper turns this into +Inf (the "beats nothing by infinity" signal).
        val pixels = IntArray(1000) { 0xFFDCA8A6.toInt() }
        val swatches = kMeansQuantize(pixels, k = 12)
        assertEquals("solid input must yield one swatch", 1, swatches.size)
        assertEquals(1000, swatches[0].population)
        // Center of identical pixels is the input itself — no grid snap.
        assertEquals(0xFFDCA8A6.toInt(), swatches[0].rgb)
    }

    @Test
    fun swatchesAreSortedByPopulationDescending() {
        // 700 red + 200 green + 100 blue → top must be red.
        val pixels = IntArray(1000) { idx ->
            when {
                idx < 700 -> 0xFFFF0000.toInt()
                idx < 900 -> 0xFF00FF00.toInt()
                else -> 0xFF0000FF.toInt()
            }
        }
        val swatches = kMeansQuantize(pixels, k = 12)
        assertEquals(3, swatches.size)
        assertTrue("top must beat second", swatches[0].population >= swatches[1].population)
        assertTrue("second must beat third", swatches[1].population >= swatches[2].population)
        assertEquals(700, swatches[0].population)
    }

    @Test
    fun sameSeed_isDeterministic() {
        // k-means++ with a fixed seed must give bit-for-bit identical output
        // across runs. This is the determinism advantage over Palette cited in
        // ADR-0017 (Palette's median-cut is not bit-for-bit stable).
        val pixels = IntArray(500) { idx ->
            if (idx % 2 == 0) 0xFFAA8866.toInt() else 0xFF665544.toInt()
        }
        val run1 = kMeansQuantize(pixels, k = 8, seed = 42L)
        val run2 = kMeansQuantize(pixels, k = 8, seed = 42L)
        assertEquals(run1.size, run2.size)
        for (i in run1.indices) {
            assertEquals("rgb must match at rank $i", run1[i].rgb, run2[i].rgb)
            assertEquals("population must match at rank $i", run1[i].population, run2[i].population)
        }
    }

    /**
     * The regression that replaced Palette (ADR-0017). Five distinct warm-tone
     * browns, equal population. Palette's median-cut merged them into one
     * oversized swatch (app share 0.50 vs offline k-means 0.24 on production
     * frame :33). k-means must keep them separate so the top share reflects
     * the photo's true fragmentation.
     */
    @Test
    fun warmTones_doNotCollapseIntoOneSwatch() {
        // Five browns spanning a warm range — the exact class of input that
        // triggered the false-negative in the 6-frame production corpus.
        val browns = intArrayOf(
            0xFF452F13.toInt(), 0xFF6A4B16.toInt(), 0xFF583C11.toInt(),
            0xFF78571F.toInt(), 0xFF33200C.toInt(),
        )
        // 1000 pixels: 200 of each brown, interleaved so order does not bias
        // the k-means++ seeding sweep.
        val pixels = IntArray(1000) { idx -> browns[idx % browns.size] }
        val swatches = kMeansQuantize(pixels, k = 12)

        assertTrue("expected at least 4 distinct clusters, got ${swatches.size}", swatches.size >= 4)
        val topShare = swatches[0].population.toDouble() / pixels.size
        // With 5 equal-population input clusters, the top share must stay
        // close to 0.20 — NOT the ~0.50 that Palette's median-cut produced.
        // Tolerance is generous (k-means may merge two very close browns on
        // some seeds), but 0.40 is well above any honest result and matches
        // the production bias documented in ADR-0017.
        assertTrue(
            "top share must stay below 0.40 (Palette's over-merge zone), was $topShare",
            topShare < 0.40,
        )
    }
}
