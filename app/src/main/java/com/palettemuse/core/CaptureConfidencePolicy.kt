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
 * A capture is low-confidence only when **both** [CapturedColor.populationShare]
 * is below [MIN_POPULATION_SHARE] **and** [CapturedColor.topVsSecondRatio] is
 * below [MIN_TOP_VS_SECOND_RATIO]. Either signal alone is not enough:
 *
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
     * @return `true` when both [CapturedColor.populationShare] and
     *   [CapturedColor.topVsSecondRatio] are strictly below their respective
     *   thresholds.
     */
    fun isLowConfidence(c: CapturedColor): Boolean =
        c.populationShare < MIN_POPULATION_SHARE &&
            c.topVsSecondRatio < MIN_TOP_VS_SECOND_RATIO

    private companion object {
        // Calibration seeds, not product commitments: see ADR-0014
        // (calibration corpus), and the future ADR-0015 tune that will replace
        // these constants after a real-data review of the 5-case corpus (per
        // the ADR's Follow-up section).


        /** Below this top-swatch share, the dominant does not represent the photo. */
        const val MIN_POPULATION_SHARE = 0.40

        /** Below this top-vs-second ratio, the dominant does not clearly beat the runner-up. */
        const val MIN_TOP_VS_SECOND_RATIO = 1.5
    }
}
