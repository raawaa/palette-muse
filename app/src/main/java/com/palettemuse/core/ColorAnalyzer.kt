package com.palettemuse.core

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorAnalyzer @Inject constructor() {

    /**
     * Returns the captured color of [bitmap] together with two confidence
     * signals derived from the same Palette quantization that picked the
     * dominant — see `docs/adr/0014-captured-color-confidence-signal.md`.
     *
     * This is the **single source of truth** for captured color — the value
     * used by both the viewfinder (`CaptureViewModel.onFrameAnalyzed`) and the
     * shutter path (`CaptureViewModel.capturePhoto`). The `.hex` field is the
     * whole-photo dominant quantized from a 96×96 downscale (ADR-0001). The
     * two extra fields let [CaptureConfidencePolicy] decide whether the dominant
     * actually stands for the photo or whether the UI should flag the capture
     * as unclear. Palette quantization runs on the calling thread; each caller
     * owns threading.
     */
    fun extractCapturedColor(bitmap: Bitmap): CapturedColor {
        val palette = Palette.from(bitmap)
            .maximumColorCount(12)
            .clearFilters()
            .resizeBitmapArea(96 * 96)
            .generate()
        val dominantSwatch = palette.dominantSwatch
        val swatches = palette.getSwatches()

        val totalPixels = swatches.sumOf { it.population }.coerceAtLeast(1)
        val hex = dominantSwatch?.rgb?.toHex() ?: "#808080"
        val populationShare = (dominantSwatch?.population?.toDouble() ?: 0.0) / totalPixels

        // Translate to primitive-population form so the ratio computation is
        // pure-Kotlin testable in the JVM source set (see below for the rule).
        val dominantPopulation = dominantSwatch?.population
        val populationOfOthers = swatches
            .filter { it !== dominantSwatch }
            .map { it.population }
        val topVsSecondRatio = computeTopVsSecondRatio(dominantPopulation, populationOfOthers)

        return CapturedColor(
            hex = hex,
            populationShare = populationShare,
            topVsSecondRatio = topVsSecondRatio,
        )
    }

    private fun Int.toHex(): String {
        return "#%06X".format(this and 0xFFFFFF)
    }
}

/**
 * Computes the top-vs-second-swatch ratio from a Palette quantization, with
 * the same convention [ColorAnalyzer.extractCapturedColor] uses. Extracted
 * to a top-level `internal` function with primitive inputs so it is
 * pure-Kotlin JVM-testable without producing a real `Bitmap` /
 * `Palette.Swatch` (per ADR-0002's top-level-fn testability pattern).
 *
 * Contract:
 * - Returns `0.0` when [dominantPopulation] is `null` (no swatches or no
 *   dominant — e.g. all-transparent bitmap). The policy's "both below
 *   threshold" check then flags the capture low-confidence. Returning
 *   `POSITIVE_INFINITY` here would silently mask the worst input as
 *   "no comparison" (PR #19 RISK-review finding).
 * - Returns `Double.POSITIVE_INFINITY` when the dominant exists but the
 *   others list is empty (single-swatch bitmap) — this is the "this beats
 *   nothing by ∞" signal; high share clears the policy threshold alongside.
 * - Otherwise returns `dominantPopulation / max(others)`.
 *
 * @param dominantPopulation `null` when there is no dominant swatch
 *   (Palette returns null in that case — e.g. all-transparent bitmaps).
 * @param populationsOfOtherSwatches populations of every swatch that is
 *   NOT the dominant, in descending order is not assumed. Empty when only
 *   the dominant exists.
 */
internal fun computeTopVsSecondRatio(
    dominantPopulation: Int?,
    populationsOfOtherSwatches: List<Int>,
): Double {
    // No dominant → no comparison possible. Return 0.0 so the policy's
    // "both signals below threshold" arm fires and the capture is flagged
    // low-confidence (PR #19 RISK-review).
    val top = dominantPopulation ?: return 0.0
    val second = populationsOfOtherSwatches.maxOrNull() ?: 0
    return if (second <= 0) Double.POSITIVE_INFINITY else top.toDouble() / second.toDouble()
}
