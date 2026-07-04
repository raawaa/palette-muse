package com.palettemuse.core

/**
 * A whole-photo captured color and the signals that say how much it stands for
 * the photo's actual dominant. Built by [ColorAnalyzer.extractCapturedColor] and
 * consumed by [CaptureConfidencePolicy]; see `docs/adr/0014-captured-color-
 * confidence-signal.md` for why both signals are surfaced rather than only a
 * pre-baked `isLowConfidence` boolean.
 *
 * @property hex            `#RRGGBB` quantized dominant of the photo — the top
 *                          k-means cluster's centroid (ADR-0017). Always set;
 *                          falls back to `"#808080"` only when k-means produces
 *                          no swatch at all. The single source of truth for
 *                          captured color (ADR-0001).
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
 */
data class CapturedColor(
    val hex: String,
    val populationShare: Double,
    val topVsSecondRatio: Double,
)
