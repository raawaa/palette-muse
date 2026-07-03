package com.palettemuse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin unit tests for the top-level [computeTopVsSecondRatio]
 * helper that backs [ColorAnalyzer.extractCapturedColor]. The helper takes
 * primitive `Int` populations (not a swatch value object)
 * so it is testable in the JVM source set without an Android runtime
 * (per ADR-0002's top-level-fn testability pattern).
 *
 * PR #19's RISK-review found that a null dominant on an empty palette
 * leaked POSITIVE_INFINITY through the original in-line expression,
 * silently masking the worst input (corrupt / all-transparent bitmap) as
 * "high confidence". The fix is in [computeTopVsSecondRatio]; these
 * tests pin every branch the helper takes.
 */
class ColorAnalyzerRatioTest {

    @Test
    fun nullDominant_emptyOthers_ratioIsZero() {
        // Worst input — Palette's `getSwatches()` came back empty AND
        // `dominantSwatch` is null. PR #19 RISK-review case: previously
        // returned POSITIVE_INFINITY, silently masked as "high confidence".
        // Fix: ratio = 0.0 → policy's `ratio < 1.5` arm fires.
        assertEquals(0.0, computeTopVsSecondRatio(null, emptyList()), 0.0001)
    }

    @Test
    fun nullDominant_withOtherSwatches_ratioIsZero() {
        // Defensive: others may have entries even when dominant is null
        // (Palette edge cases). The ratio must still be 0.0 — the
        // dominant's absence is the deciding signal, not the others count.
        assertEquals(0.0, computeTopVsSecondRatio(null, listOf(100, 80)), 0.0001)
    }

    @Test
    fun dominantExists_butIsSingleSwatch_ratioIsInfinity() {
        // A solid-color bitmap quantises to a single swatch. We have to
        // distinguish "single dominant beats nothing" (+Inf, high confidence)
        // from "no dominant at all" (0.0, low confidence). Pixel-perfect
        // distinction the user-facing policy relies on.
        assertEquals(
            Double.POSITIVE_INFINITY,
            computeTopVsSecondRatio(dominantPopulation = 9216, populationsOfOtherSwatches = emptyList()),
            0.0001,
        )
    }

    @Test
    fun dominantExists_withSecondSwatch_ratioIsTopOverSecondLargest() {
        // Standard heterogeneous case — top beats the largest other by some
        // ratio. The helper takes a list (not "the second", since Palette
        // returns swatches in some internal order) and uses the max as the
        // comparison baseline.
        assertEquals(
            500.0 / 200.0,
            computeTopVsSecondRatio(dominantPopulation = 500, populationsOfOtherSwatches = listOf(200)),
            0.0001,
        )
    }

    @Test
    fun dominantExists_multipleOthers_ratioIsTopOverLargest() {
        // Same rule with multiple other swatches — the helper picks the
        // largest. (Palette's swatch list is not necessarily ordered by
        // population descending — we cannot assume that.)
        assertEquals(
            500.0 / 250.0, // not /150 — largest wins
            computeTopVsSecondRatio(dominantPopulation = 500, populationsOfOtherSwatches = listOf(150, 250, 100)),
            0.0001,
        )
    }

    @Test
    fun dominantExists_largestOtherHasZeroPopulation_ratioIsInfinity() {
        // Sibling of single-swatch: dominant is non-null, others list
        // exists but the max is 0 (all-zero populations coalesced). Either
        // way the second can't "compete", so the +Inf signal is correct.
        assertEquals(
            Double.POSITIVE_INFINITY,
            computeTopVsSecondRatio(dominantPopulation = 100, populationsOfOtherSwatches = listOf(0, 0)),
            0.0001,
        )
    }

    @Test
    fun dominantExists_swallowsTiedSecond_ratioIsOne() {
        // Tie boundary: top == max-other → ratio = 1.0 (top NOT strictly
        // greater). The policy uses strict `<` so a tie is *not*
        // low-confidence on the ratio signal — share still has to clear.
        val ratio = computeTopVsSecondRatio(
            dominantPopulation = 100,
            populationsOfOtherSwatches = listOf(100),
        )
        assertEquals(1.0, ratio, 0.0001)
        assertTrue("ties are NOT strictly greater", ratio >= 1.0)
    }
}
