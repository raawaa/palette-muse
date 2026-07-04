package com.palettemuse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [mergeSwatchesIntoPerceptualFamilies] — the ADR-0020
 * perceptual color-family merge that backs the captured-color confidence
 * signals. The merge takes a `List<ColorSwatch>` (k-means output, no
 * `android.graphics.Bitmap`) so it is testable in the JVM source set — per
 * ADR-0002's top-level-fn testability pattern and ADR-0006's pure-Kotlin-core
 * constraint.
 *
 * These tests pin external behavior (which families form, what populations
 * result), NOT union-find internals. The pivotal cases mirror the 6-frame
 * real-device audit corpus that motivated ADR-0020:
 *
 * - [falseNegativeCleared_nearDuplicateClustersMergeAndClearThreshold]: the
 *   purple-label / curtain / wood-floor class — k-means split one perceptual
 *   color into ~6 near-duplicate centroids each carrying ~1/k of the pixels, so
 *   the raw top-cluster share sat at ~0.14 and every frame was wrongly flagged
 *   low-confidence. After merge the family clears the 0.40 threshold.
 * - [genuinelyFragmentedStaysLow_mutuallyDistantColorsDoNotMerge]: the
 *   cluttered-chair class — guards against over-merging clearing a scene that
 *   genuinely has no dominant color.
 */
class PerceptualColorFamiliesTest {

    @Test
    fun emptyInput_returnsEmptyList() {
        assertEquals(emptyList<ColorFamily>(), mergeSwatchesIntoPerceptualFamilies(emptyList()))
    }

    @Test
    fun singleSwatch_returnsSingleFamilyWithSamePopulationAndRgb() {
        val families = mergeSwatchesIntoPerceptualFamilies(listOf(ColorSwatch(0xA0A0A0, 250)))
        assertEquals(1, families.size)
        assertEquals(0xA0A0A0, families[0].rgb)
        assertEquals(250, families[0].population)
    }

    @Test
    fun twoIdenticalColors_mergeIntoOneFamilyWithSummedPopulation() {
        val families = mergeSwatchesIntoPerceptualFamilies(
            listOf(ColorSwatch(0xA0A0A0, 100), ColorSwatch(0xA0A0A0, 80))
        )
        assertEquals(1, families.size)
        assertEquals(180, families[0].population)
    }

    /**
     * The 5/6 false-negative regression (ADR-0020). Six near-duplicate grey
     * centroids — the shape k-means produces when a visually-uniform color
     * (glare, grain, JPEG noise) varies slightly across pixels — each carry
     * roughly 1/k of the pixels, so the raw top-cluster share is ~0.14 and the
     * capture is wrongly flagged low-confidence. After perceptual merge the
     * six union into one family carrying ~0.89 of the pixels, clearing the
     * policy's 0.40 threshold. The small blue outlier stays its own family.
     */
    @Test
    fun falseNegativeCleared_nearDuplicateClustersMergeAndClearThreshold() {
        // Six greys within a few RGB units of each other → mutually ΔE < 10.
        val nearDuplicates = listOf(
            ColorSwatch(0xA0A0A0, 140),
            ColorSwatch(0x9B9B9B, 140),
            ColorSwatch(0xA5A5A5, 140),
            ColorSwatch(0x9E9E9E, 140),
            ColorSwatch(0xA2A2A2, 140),
            ColorSwatch(0x9C9C9C, 140),
        )
        val outlier = ColorSwatch(0x0000FF, 100) // pure blue — far from every grey
        val swatches = nearDuplicates + outlier
        val totalPopulation = swatches.sumOf { it.population }

        val families = mergeSwatchesIntoPerceptualFamilies(swatches)

        // Sanity: the greys really are mutually within the default threshold,
        // and the outlier really is outside it — otherwise the test would pass
        // for the wrong reason.
        val maxGreyDeltaE = nearDuplicates.flatMapIndexed { i, si ->
            nearDuplicates.drop(i + 1).map { sj -> deltaE(rgbFromInt(si.rgb), rgbFromInt(sj.rgb)) }
        }.max()
        assertTrue("greys must be mutually within ΔE<10, max was $maxGreyDeltaE", maxGreyDeltaE < 10.0)
        val greyToOutlier = nearDuplicates.maxOf { deltaE(rgbFromInt(it.rgb), rgbFromInt(outlier.rgb)) }
        assertTrue("outlier must be >10 from every grey, min was $greyToOutlier", greyToOutlier > 10.0)

        // The merge: two families — the unioned greys and the blue outlier.
        assertEquals("grey family + outlier family", 2, families.size)

        val dominant = families.first()
        val dominantShare = dominant.population.toDouble() / totalPopulation
        assertTrue(
            "dominant family share $dominantShare should clear the 0.40 threshold",
            dominantShare >= 0.40
        )
        // End-to-end: the merged share lifts the capture out of low-confidence.
        val second = families[1].population
        val ratio = computeTopVsSecondRatio(dominant.population, listOf(second))
        val verdict = CaptureConfidencePolicy().isLowConfidence(
            CapturedColor(rgb = 0xA0A0A0, populationShare = dominantShare, topVsSecondRatio = ratio)
        )
        assertTrue("capture must NOT be low-confidence after merge", verdict.not())
    }

    /**
     * The over-merge guard (ADR-0020). A genuinely fragmented scene — four
     * mutually distant colors at equal population — must stay fragmented after
     * merge: four families each carrying ~0.25, below the 0.40 threshold. If
     * the merge ever became aggressive enough to union these, the
     * cluttered-chair class of frames would silently clear and the
     * low-confidence prompt would stop meaning anything.
     */
    @Test
    fun genuinelyFragmentedStaysLow_mutuallyDistantColorsDoNotMerge() {
        val swatches = listOf(
            ColorSwatch(0xFF0000, 250), // red
            ColorSwatch(0x00FF00, 250), // green
            ColorSwatch(0x0000FF, 250), // blue
            ColorSwatch(0xFFFF00, 250), // yellow
        )
        val totalPopulation = swatches.sumOf { it.population }

        val families = mergeSwatchesIntoPerceptualFamilies(swatches)

        assertEquals("four mutually-distant colors stay as four families", 4, families.size)
        val dominantShare = families.first().population.toDouble() / totalPopulation
        assertTrue(
            "fragmented dominant share $dominantShare must stay below 0.40",
            dominantShare < 0.40
        )
    }

    /**
     * The merge comparison is `<` strict: two centroids whose ΔE equals the
     * threshold are NOT merged (a neighbor must be perceptually *closer* than
     * the threshold, not at it). Pinning the convention prevents a future `<=`
     * slip from quietly shifting the family boundaries.
     */
    @Test
    fun deltaEBoundary_isStrictLessThan_atThresholdNotMerged() {
        val a = 0xA0A0A0
        val b = 0x707070
        val dE = deltaE(rgbFromInt(a), rgbFromInt(b))
        assertTrue("need a non-trivial ΔE for this case, got $dE", dE > 1.0)

        val atThreshold = mergeSwatchesIntoPerceptualFamilies(
            listOf(ColorSwatch(a, 100), ColorSwatch(b, 100)), deltaEThreshold = dE
        )
        assertEquals("at-threshold must NOT merge (< strict)", 2, atThreshold.size)

        val aboveThreshold = mergeSwatchesIntoPerceptualFamilies(
            listOf(ColorSwatch(a, 100), ColorSwatch(b, 100)), deltaEThreshold = dE + 0.001
        )
        assertEquals("just-above-threshold must merge", 1, aboveThreshold.size)
    }

    /**
     * Family membership is transitive (union-find): if A≈B and B≈C then A,B,C
     * are one family even when A≉C — matching how a human calls a smooth
     * gradient "one color" end to end. The chain is engineered so the endpoints
     * are OUTSIDE the threshold and would never merge without transitivity.
     */
    @Test
    fun transitivity_chainMergesEvenWhenEndpointsExceedThreshold() {
        // Three greys stepping down ~10 RGB units each. Consecutive steps sit
        // just inside a tight threshold; the endpoints span ~20 units and sit
        // outside it.
        val a = 0xA0A0A0
        val b = 0x969696
        val c = 0x8C8C8C
        val threshold = 5.0
        val dAB = deltaE(rgbFromInt(a), rgbFromInt(b))
        val dBC = deltaE(rgbFromInt(b), rgbFromInt(c))
        val dAC = deltaE(rgbFromInt(a), rgbFromInt(c))
        // Preconditions — prove the test exercises transitivity, not direct merge.
        assertTrue("chain link A-B (ΔE=$dAB) must be < $threshold", dAB < threshold)
        assertTrue("chain link B-C (ΔE=$dBC) must be < $threshold", dBC < threshold)
        assertTrue("endpoints A-C (ΔE=$dAC) must be >= $threshold", dAC >= threshold)

        val families = mergeSwatchesIntoPerceptualFamilies(
            listOf(ColorSwatch(a, 100), ColorSwatch(b, 100), ColorSwatch(c, 100)),
            deltaEThreshold = threshold,
        )
        assertEquals("transitive closure merges the chain into one family", 1, families.size)
        assertEquals(300, families[0].population)
    }

    /**
     * Determinism + input-order independence (ADR-0017's bit-for-bit-stability
     * property, extended through the merge). The same centroid set must yield
     * the identical family partition and ordering regardless of how k-means
     * (or the caller) orders its output.
     */
    @Test
    fun determinism_sameInputInAnyOrderYieldsIdenticalFamilies() {
        val swatches = listOf(
            ColorSwatch(0xA0A0A0, 100),
            ColorSwatch(0x9B9B9B, 90),
            ColorSwatch(0xFF0000, 50),
        )
        val first = mergeSwatchesIntoPerceptualFamilies(swatches)
        val reversed = mergeSwatchesIntoPerceptualFamilies(swatches.reversed())
        val shuffled = mergeSwatchesIntoPerceptualFamilies(
            listOf(swatches[2], swatches[0], swatches[1])
        )
        assertEquals(first, reversed)
        assertEquals(first, shuffled)
    }

    @Test
    fun familiesSortedByPopulationDescending() {
        val families = mergeSwatchesIntoPerceptualFamilies(
            listOf(
                ColorSwatch(0xFF0000, 100),
                ColorSwatch(0xA0A0A0, 300),
                ColorSwatch(0x00FF00, 200),
            )
        )
        assertTrue(
            "families must be sorted by population descending: ${families.map { it.population }}",
            families.zipWithNext().all { (x, y) -> x.population >= y.population }
        )
    }

    /**
     * The family's representative rgb is the population-weighted average of its
     * member centroids (ADR-0021) — deterministic and independent of input
     * order. Two mergeable centroids with unequal populations: the family's rgb
     * is the weighted mean of their channels, NOT whichever member carried the
     * most pixels. This is the regression guard for the bug where a color family
     * that k-means split across near-duplicate centroids was represented by a
     * single cluster that disagreed with the very family earning the confidence
     * signal (see issue #41).
     */
    @Test
    fun representativeIsPopulationWeightedAverage() {
        val families = mergeSwatchesIntoPerceptualFamilies(
            listOf(ColorSwatch(0x9B9B9B, 70), ColorSwatch(0xA0A0A0, 130))
        )
        assertEquals(1, families.size)
        // Per channel: (0x9B*70 + 0xA0*130) / 200 = (155*70 + 160*130) / 200
        //             = 31650 / 200 = 158.25 → 0x9E
        assertEquals(
            "representative must be the population-weighted average rgb",
            0x9E9E9E,
            families[0].rgb,
        )
        assertEquals(200, families[0].population)
    }
}
