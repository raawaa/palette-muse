package com.palettemuse.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the initial (0.40, 1.5) thresholds shipped for #17. These cases encode the
 * product rule — a photo is low-confidence only when *both* the population share
 * AND the top-vs-second ratio are below their threshold. Tuning these values is
 * an explicit test change: per ADR-0014, the seeds are calibration seeds and a
 * real-photo review in a later session replaces them.
 *
 * `CaptureConfidencePolicy` is 100% JVM-testable (no Android imports) — it sits
 * on plain data.
 */
class CaptureConfidencePolicyTest {

    private val policy = CaptureConfidencePolicy()

    @Test
    fun sharedIsBelowThresholdButRatioClears_isNotLowConfidence() {
        // share = 0.50 ≥ 0.40 → not low confidence even though ratio is tight.
        // Models the "two large swatches of similar size" photo where the user
        // can still pick — but a low-share rule would erroneously flag it.
        val c = captured(share = 0.50, ratio = 1.4)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun shareJustBelowAndRatioJustBelow_isLowConfidence() {
        // Both signals below threshold → low confidence. This is the bicolored
        // flat-lay case: top swatch 25%, second 25% — neither dominates.
        val c = captured(share = 0.25, ratio = 1.0)
        assertTrue(policy.isLowConfidence(c))
    }

    @Test
    fun veryLowShareAlone_isNotLowConfidence_whenRatioClears() {
        // Tiny distinct object on a near-uniform background: top swatch count
        // beats second by ratio 1.8 (above threshold). Share is tiny (0.05)
        // but the dominant IS decisive at the top of the palette. With one
        // signal below and the other above, the photo is NOT low-confidence:
        // the user can still pick the small-but-clear dominant.
        val c = captured(share = 0.05, ratio = 1.8)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun highShareHighRatio_isNotLowConfidence() {
        // Sunset reference case: orange is 75% of the photo, second is trivial
        // (15%). Both signals clear thresholds.
        val c = captured(share = 0.75, ratio = 5.0)
        assertFalse(policy.isLowConfidence(c))
    }

    /**
     * Spec pin (issue #17 / Agent Brief, criterion 4 verbatim):
     * `share=0.05, ratio=1.2` → low. Both signals far below threshold — a
     * photo with a barely-attended dominant AND a near-tied second swatch
     * carries no decision, even though neither signal alone is "extreme".
     */
    @Test
    fun tinyShareAndNearTiedRatio_isLowConfidence() {
        val c = captured(share = 0.05, ratio = 1.2)
        assertTrue(policy.isLowConfidence(c))
    }

    /**
     * Boundary cases anchor the rule at exactly threshold. isLowConfidence is
     * strictly `<`, so exactly-on-threshold is NOT low.
     */
    @Test
    fun exactlyOnShareThreshold_alone_isNotLowConfidence() {
        val c = captured(share = 0.40, ratio = 1.0) // ratio alone is below → share passes
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun exactlyOnBothThresholds_isNotLowConfidence() {
        // Both signals AT threshold (not below) — outside the low set.
        val c = captured(share = 0.40, ratio = 1.5)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun bothJustBelowThresholds_isLowConfidence() {
        val c = captured(share = 0.39, ratio = 1.49)
        assertTrue(policy.isLowConfidence(c))
    }

    private fun captured(share: Double, ratio: Double) =
        CapturedColor(rgb = 0x000000, populationShare = share, topVsSecondRatio = ratio)
}
