package com.palettemuse.core

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorAnalyzer @Inject constructor() {

    /**
     * Returns the captured color of [bitmap] together with two confidence
     * signals derived from the same k-means quantization that picked the
     * dominant — see `docs/adr/0014-captured-color-confidence-signal.md`,
     * `docs/adr/0017-replace-palette-with-kmeans.md`, and
     * `docs/adr/0020-population-share-over-perceptual-families.md`.
     *
     * This is the **single source of truth** for captured color — the value
     * used by both the viewfinder (`CaptureViewModel.onFrameAnalyzed`) and the
     * shutter path (`CaptureViewModel.capturePhoto`). The `.rgb` field is the
     * whole-photo dominant — the top k-means cluster's centroid (24-bit
     * `0xRRGGBB`), quantized from a 96×96 downscale (ADR-0001) via k-means
     * (k=12). The hex string is available via `.hex` on [CapturedColor]. The two
     * confidence
     * signals are measured over **perceptually-merged color families**
     * (ADR-0020): k-means centroids a human would call the same color (CIELAB
     * ΔE < 10) are unioned into one family before `populationShare` and
     * `topVsSecondRatio` are computed, so a visually-uniform color split across
     * near-duplicate centroids is no longer structurally capped at ~1/k. The
     * two fields let [CaptureConfidencePolicy] decide whether the dominant
     * actually stands for the photo or whether the UI should flag it unclear.
     *
     * Threading: k-means and the merge run on the calling thread; each caller
     * owns threading.
     */
    fun extractCapturedColor(bitmap: Bitmap): CapturedColor {
        // Downscale to 96×96 first — matches ADR-0001's resolution-normalization
        // invariant (viewfinder 640×480 and shutter full-res both enter the
        // quantizer at the same 9,216-pixel scale). The pre-k-means sample
        // replaces Palette's internal `resizeBitmapArea(96*96)`.
        val downscaled = if (bitmap.width == DOWNSCALE_SIZE && bitmap.height == DOWNSCALE_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, DOWNSCALE_SIZE, DOWNSCALE_SIZE, true)
        }
        val pixels = IntArray(DOWNSCALE_SIZE * DOWNSCALE_SIZE)
        downscaled.getPixels(pixels, 0, DOWNSCALE_SIZE, 0, 0, DOWNSCALE_SIZE, DOWNSCALE_SIZE)
        return analyzePixels(pixels)
    }

    private companion object {
        const val DOWNSCALE_SIZE = 96
    }
}

/**
 * Pure-Kotlin orchestration: pixel array → CapturedColor.
 * JVM unit tests can construct an IntArray and call this directly
 * — no Bitmap, no Android dependency.
 */
internal fun analyzePixels(pixels: IntArray): CapturedColor {
    val swatches = kMeansQuantize(pixels, k = TARGET_COLOR_COUNT)
    val families = mergeSwatchesIntoPerceptualFamilies(swatches)
    val totalPixels = swatches.sumOf { it.population }.coerceAtLeast(1)
    val dominant = swatches.firstOrNull()
    val dominantFamily = families.firstOrNull()
    val populationShare = (dominantFamily?.population?.toDouble() ?: 0.0) / totalPixels
    val dominantPopulation = dominantFamily?.population
    val populationOfOthers = families.filter { it != dominantFamily }.map { it.population }
    val topVsSecondRatio = computeTopVsSecondRatio(dominantPopulation, populationOfOthers)
    return CapturedColor(
        rgb = dominant?.rgb ?: 0x808080,
        populationShare = populationShare,
        topVsSecondRatio = topVsSecondRatio,
    )
}

/** Number of k-means clusters used for color quantization. */
internal const val TARGET_COLOR_COUNT = 12

/**
 * Converts an ARGB integer (alpha ignored) to a `#RRGGBB` hex string.
 */
internal fun Int.toHex(): String {
    return "#%06X".format(this and 0xFFFFFF)
}

/**
 * Computes the top-vs-second ratio from a k-means quantization **after
 * perceptual merge** (ADR-0020) — i.e. over merged color-family populations,
 * not raw clusters. Extracted to a top-level `internal` function with
 * primitive inputs so it is pure-Kotlin JVM-testable without producing a real
 * `Bitmap` / `ColorSwatch` (per ADR-0002's top-level-fn testability pattern).
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
 *   (k-means returned an empty list — e.g. all-transparent bitmaps).
 * @param populationsOfOthers populations of every unit (raw k-means cluster or
 *   merged perceptual family — ADR-0020) that is NOT the dominant, in any
 *   order. Empty when only the dominant exists.
 */
internal fun computeTopVsSecondRatio(
    dominantPopulation: Int?,
    populationsOfOthers: List<Int>,
): Double {
    // No dominant → no comparison possible. Return 0.0 so the policy's
    // "both signals below threshold" arm fires and the capture is flagged
    // low-confidence (PR #19 RISK-review).
    val top = dominantPopulation ?: return 0.0
    val second = populationsOfOthers.maxOrNull() ?: 0
    return if (second <= 0) Double.POSITIVE_INFINITY else top.toDouble() / second.toDouble()
}
