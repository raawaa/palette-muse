package com.palettemuse.core

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorAnalyzer @Inject constructor() {

    /**
     * Returns the captured color of [bitmap] together with two confidence
     * signals derived from the same k-means quantization that picked the
     * dominant. When a subject [mask] is provided (aligned 1:1 with the
     * downscaled pixel array), k-means runs only on masked pixels so the
     * captured color is computed from the subject region. A null mask
     * preserves the whole-photo behavior (today's default).
     *
     * This is the **single source of truth** for captured color — the value
     * used by both the viewfinder (`CaptureViewModel.onFrameAnalyzed`) and the
     * shutter path (`CaptureViewModel.capturePhoto`). The `.rgb` field is the
     * whole-photo or subject-region dominant — the population-weighted centroid
     * of the largest perceptual color family (24-bit `0xRRGGBB`), quantized
     * from a 96×96 downscale via k-means (k=12), then union-find merged by
     * CIELAB ΔE < 10. See `docs/adr/0014-captured-color-confidence-signal.md`,
     * `docs/adr/0017-replace-palette-with-kmeans.md`, and
     * `docs/adr/0020-population-share-over-perceptual-families.md`.
     *
     * @param mask  Optional subject-mask aligned 1:1 with the downscaled
     *   pixel array. When non-null, only pixels whose mask entry is `true`
     *   enter k-means quantization. Null preserves whole-photo behavior.
     */
    fun extractCapturedColor(bitmap: Bitmap, mask: BooleanArray? = null): CapturedColor {
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
        return analyzePixels(pixels, mask)
    }
}

/** Pipeline working resolution: captured bitmaps are downscaled to this size. */
internal const val DOWNSCALE_SIZE = 96

/**
 * Pure-Kotlin orchestration: pixel array → CapturedColor.
 * When [mask] is non-null, only pixels whose mask entry is `true` are
 * quantized, so the captured color is extracted from the subject region.
 * Null mask preserves whole-photo behavior.
 *
 * JVM unit tests can construct an IntArray and BooleanArray and call
 * this directly — no Bitmap, no Android dependency.
 *
 * @param mask  Optional mask aligned 1:1 with [pixels]. `true` = subject pixel,
 *   `false` = background (excluded from quantization). When non-null and
 *   non-empty, k-means runs only on the masked pixels; if the mask filters out
 *   every pixel, k-means gets an empty input and the function returns a gray
 *   fallback (0x808080) — the caller's degeneracy guard prevents this path in
 *   production (see CaptureConfidencePolicy's mask-coverage band).
 */
internal fun analyzePixels(pixels: IntArray, mask: BooleanArray? = null): CapturedColor {
    val effectivePixels = if (mask != null) filterByMask(pixels, mask) else pixels
    val swatches = kMeansQuantize(effectivePixels, k = TARGET_COLOR_COUNT)
    val families = mergeSwatchesIntoPerceptualFamilies(swatches)
    val totalPixels = swatches.sumOf { it.population }.coerceAtLeast(1)
    val dominantFamily = families.firstOrNull()
    val populationShare = (dominantFamily?.population?.toDouble() ?: 0.0) / totalPixels
    val dominantPopulation = dominantFamily?.population
    val populationOfOthers = families.filter { it != dominantFamily }.map { it.population }
    val topVsSecondRatio = computeTopVsSecondRatio(dominantPopulation, populationOfOthers)
    return CapturedColor(
        // ADR-0021: the captured rgb is the dominant family's weighted-average
        // centroid, NOT the raw top k-means cluster. The raw top cluster can
        // lose the population race inside a family (k-means splits a perceptually
        // uniform color across several near-duplicate centroids) while still
        // being the largest *single* cluster — yielding an rgb that disagrees
        // with the very family that earned `populationShare`. Selecting the
        // family centroid keeps the rgb and the confidence signal on the same
        // granularity.
        rgb = dominantFamily?.rgb ?: 0x808080,
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

/**
 * Filters [pixels] to only those positions where [mask] is `true`.
 * Returns a new IntArray containing the masked pixels in original order.
 * Used when a subject-mask is provided so k-means runs only on the
 * subject region.
 */
internal fun filterByMask(pixels: IntArray, mask: BooleanArray): IntArray {
    var count = 0
    for (b in mask) if (b) count++
    val result = IntArray(count)
    var idx = 0
    for (i in pixels.indices) {
        if (mask[i]) {
            result[idx++] = pixels[i]
        }
    }
    return result
}
