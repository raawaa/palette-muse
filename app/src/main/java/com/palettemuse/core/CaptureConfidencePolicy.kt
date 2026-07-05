package com.palettemuse.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a [CapturedColor] should be flagged as "unclear" to the user.
 * Sits apart from `core/CapturedColor.kt` on purpose: the boolean is policy
 * output, not data, and bundling it on the value type would force the extractor
 * to depend on this class (or vice-versa), coupling two unrelated concerns. See
 * `docs/adr/0014-captured-color-confidence-signal.md`.
 *
 * A capture is low-confidence when **either**:
 * - Both [CapturedColor.populationShare] and [CapturedColor.topVsSecondRatio]
 *   are strictly below their respective thresholds (the classic two-signal test).
 * - The subject [CapturedColor.maskCoverage] is outside the degeneracy band
 *   defined by [MIN_MASK_COVERAGE] and [MAX_MASK_COVERAGE] (mask is too small
 *   or too large to carry a meaningful subject signal).
 *
 * The two classic signals alone:
 * - High share + low ratio means the photo is dominated by one large color,
 *   with another large secondary color nearby (e.g. grass 50% + sky 35%) — the
 *   user can pick the dominant and find the day's mood; not low-confidence.
 * - High ratio + low share means a small distinct object on a near-uniform
 *   background (e.g. one red donation box on a grey desk) — clear and small
 *   beats "no one color dominates".
 *
 * Pure — no Android imports — so the threshold pinning tests live in the JVM
 * test source set.
 */
@Singleton
class CaptureConfidencePolicy @Inject constructor() {

    /**
     * @return `true` when the capture is low-confidence by the share-ratio
     *   test or the mask-coverage degeneracy test.
     */
    fun isLowConfidence(c: CapturedColor): Boolean {
        // Degeneracy arm: a mask that covers almost nothing or almost
        // everything cannot carry a meaningful subject signal.
        val mc = c.maskCoverage
        if (mc != null && (mc < MIN_MASK_COVERAGE || mc > MAX_MASK_COVERAGE)) {
            return true
        }
        // Classic two-signal test: both must be below threshold.
        return c.populationShare < MIN_POPULATION_SHARE &&
            c.topVsSecondRatio < MIN_TOP_VS_SECOND_RATIO
    }

    /**
     * Returns `true` when [coverage] is outside the valid mask-coverage band
     * (degenerate — too small or too large to carry a subject signal). Used by
     * the capture path to decide whether to apply a mask or fall back to
     * whole-photo. Thresholds are calibration seeds (ADR-0024).
     */
    fun isMaskCoverageDegenerate(coverage: Double): Boolean =
        coverage < MIN_MASK_COVERAGE || coverage > MAX_MASK_COVERAGE

    private companion object {
        // Calibration seeds, not product commitments: see ADR-0014
        // (calibration corpus), and the future ADR-0015 tune that will replace
        // these constants after a real-data review of the 5-case corpus (per
        // the ADR's Follow-up section).


        /** Below this top-swatch share, the dominant does not represent the photo. */
        const val MIN_POPULATION_SHARE = 0.40

        /** Below this top-vs-second ratio, the dominant does not clearly beat the runner-up. */
        const val MIN_TOP_VS_SECOND_RATIO = 1.5

        /**
         * Mask-coverage degeneracy thresholds. A subject mask covering less
         * than [MIN_MASK_COVERAGE] or more than [MAX_MASK_COVERAGE] of the
         * pixel area is considered degenerate — it cannot carry a meaningful
         * subject signal and the capture should be flagged low-confidence.
         */
        const val MIN_MASK_COVERAGE = 0.05
        const val MAX_MASK_COVERAGE = 0.95
    }
}
