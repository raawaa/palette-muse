package com.palettemuse.core

/**
 * A captured color and the signals that say how much it stands for the photo's
 * actual dominant. Built by [ColorAnalyzer.extractCapturedColor] and consumed
 * by [CaptureConfidencePolicy]; see `docs/adr/0014-captured-color-confidence-
 * signal.md` for why both signals are surfaced rather than only a pre-baked
 * `isLowConfidence` boolean.
 *
 * When a subject mask was applied, the confidence signals are measured over
 * the subject region only.
 *
 * @property rgb            24-bit `0xRRGGBB` quantized dominant — the top
 *                          k-means cluster's centroid (ADR-0017). Always set;
 *                          falls back to `0x808080` only when k-means produces
 *                          no swatch at all. The single source of truth for
 *                          captured color (ADR-0001). The hex string is
 *                          available via the [hex] computed property.
 * @property populationShare Largest **perceptual color family's** pixel count /
 *                          total pixel count, in [0.0, 1.0]. A family is the
 *                          union of k-means centroids a human would call the
 *                          same color (CIELAB ΔE < 10) — ADR-0020. Answers
 *                          "how much of the photo IS this color family".
 * @property topVsSecondRatio Largest family's pixel count / second-largest
 *                          family's pixel count, `≥ 1.0`.
 *                          `Double.POSITIVE_INFINITY` when only one family
 *                          exists (single-color bitmap). Answers "does the top
 *                          family clearly beat the runner-up".
 * @property maskCoverage   Fraction of pixels covered by the subject mask,
 *                          always in (0.0, 1.0] when the mask was applied.
 *                          `null` when no subject mask was used (pre-saliency
 *                          captures and null-mask fallbacks).
 */
data class CapturedColor(
    val rgb: Int,
    val populationShare: Double,
    val topVsSecondRatio: Double,
    val maskCoverage: Double? = null,
) {
    val hex: String get() = "#%06X".format(rgb and 0xFFFFFF)
}
