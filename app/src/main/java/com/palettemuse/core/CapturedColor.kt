package com.palettemuse.core

/**
 * A whole-photo captured color and the signals that say how much it stands for
 * the photo's actual dominant. Built by [ColorAnalyzer.extractCapturedColor] and
 * consumed by [CaptureConfidencePolicy]; see `docs/adr/0014-captured-color-
 * confidence-signal.md` for why both signals are surfaced rather than only a
 * pre-baked `isLowConfidence` boolean.
 *
 * @property hex            `#RRGGBB` quantized dominant of the photo. Always set;
 *                          falls back to `"#808080"` only when Palette produces
 *                          no swatch at all. The single source of truth for
 *                          captured color (ADR-0001).
 * @property populationShare Top Palette swatch pixel count / total pixel count,
 *                          in [0.0, 1.0]. Answers "how much of the photo IS
 *                          this color".
 * @property topVsSecondRatio Top swatch pixel count / second-place swatch pixel
 *                          count, `≥ 1.0`. `Double.POSITIVE_INFINITY` when the
 *                          second swatch is absent (single-color bitmap).
 *                          Answers "does the top color clearly beat the second".
 */
data class CapturedColor(
    val hex: String,
    val populationShare: Double,
    val topVsSecondRatio: Double,
)
