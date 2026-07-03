package com.palettemuse.core

/**
 * Pure-Kotlin k-means color quantizer. Replaces `androidx.palette.graphics.Palette`
 * per ADR-0017 — Palette's median-cut algorithm systematically merges adjacent
 * warm-tone clusters (browns, tans, skin tones), inflating `populationShare` on
 * warm scenes by up to 2× and producing false-negative confidence verdicts.
 * k-means does not box-merge: N distinct brown clusters stay distinct, so the
 * top swatch's share reflects the photo's true color fragmentation.
 *
 * Pure Kotlin (no `android.graphics` imports) so it is JVM-testable without a
 * `Bitmap` — per ADR-0006's "core/ is pure Kotlin" constraint and ADR-0002's
 * top-level-fn testability pattern.
 *
 * **Determinism:** k-means++ with a fixed [seed] gives bit-for-bit stable output
 * across runs and devices — strictly better than Palette, whose median-cut is
 * not bit-for-bit deterministic (ADR-0014:46-49). This also trims the viewfinder
 * frame-to-frame jitter that ADR-0013's EMA smoother was written to absorb.
 */
internal data class ColorSwatch(val rgb: Int, val population: Int)

/**
 * Quantize [pixels] (each an ARGB `Int`, alpha ignored) into at most [k]
 * clusters via k-means with k-means++ initialization. Returns swatches sorted
 * by population descending. Deterministic for a fixed [seed].
 *
 * Contract:
 * - Returns an empty list when [pixels] is empty or [k] ≤ 0.
 * - A solid-color input collapses to a single swatch carrying the full
 *   population; callers downstream rely on this (the top-vs-second ratio
 *   helper returns `+Inf` for a single-swatch result, mirroring the old
 *   Palette single-swatch convention — ADR-0014).
 * - If the input has fewer than [k] distinct colors, the result contains one
 *   swatch per distinct color (empty clusters are dropped).
 *
 * @param pixels ARGB pixel array (e.g. from `Bitmap.getPixels`). Alpha ignored.
 * @param k target cluster count.
 * @param maxIterations Lloyd's algorithm iteration cap. Convergence (label
 *   stability) ends early.
 * @param seed RNG seed for k-means++ initialization.
 */
internal fun kMeansQuantize(
    pixels: IntArray,
    k: Int,
    maxIterations: Int = 15,
    seed: Long = 42L,
): List<ColorSwatch> {
    if (pixels.isEmpty() || k <= 0) return emptyList()

    val n = pixels.size
    // Pre-extract channels once — decoding ARGB inside the hot loop dominates
    // runtime and would be repeated n × k × maxIterations times.
    val r = FloatArray(n) { ((pixels[it] shr 16) and 0xFF).toFloat() }
    val g = FloatArray(n) { ((pixels[it] shr 8) and 0xFF).toFloat() }
    val b = FloatArray(n) { (pixels[it] and 0xFF).toFloat() }

    val centers = kMeansPlusPlusInit(r, g, b, k, seed)
    val labels = assignClusters(r, g, b, centers, IntArray(n) { -1 })

    // Lloyd's iterations. Stop when labels stabilize (centers can't move if
    // membership didn't change) or [maxIterations] is exhausted.
    repeat(maxIterations - 1) {
        updateCenters(r, g, b, centers, labels)
        val newLabels = assignClusters(r, g, b, centers, labels)
        if (newLabels.contentEquals(labels)) return@repeat
        for (i in 0 until n) labels[i] = newLabels[i]
    }

    return buildSwatches(centers, labels, k, n)
}

/**
 * k-means++ initialization: pick the first center uniformly at random, then
 * each subsequent center is a pixel chosen with probability proportional to
 * its squared distance from the nearest already-chosen center. Yields tighter,
 * more stable clusters than random init and is deterministic given [seed].
 */
private fun kMeansPlusPlusInit(
    r: FloatArray, g: FloatArray, b: FloatArray, k: Int, seed: Long,
): Array<FloatArray> {
    val n = r.size
    val rng = kotlin.random.Random(seed)
    val centers = Array(k) { FloatArray(3) }

    val firstIdx = rng.nextInt(n)
    centers[0] = floatArrayOf(r[firstIdx], g[firstIdx], b[firstIdx])

    for (c in 1 until k) {
        // Squared distance from each pixel to its nearest existing center.
        val d2 = FloatArray(n) { Float.MAX_VALUE }
        for (i in 0 until n) {
            for (cc in 0 until c) {
                val dr = r[i] - centers[cc][0]
                val dg = g[i] - centers[cc][1]
                val db = b[i] - centers[cc][2]
                val dist = dr * dr + dg * dg + db * db
                if (dist < d2[i]) d2[i] = dist
            }
        }
        val total = d2.sum()
        if (total <= 0f) {
            // Every pixel coincides with an existing center — no new distinct
            // color available. Fill the rest with the first center; they will
            // attract zero pixels and be dropped by buildSwatches.
            for (cc in c until k) centers[cc] = centers[0].copyOf()
            break
        }
        // Weighted pick proportional to d2.
        var target = rng.nextFloat() * total
        var picked = n - 1
        for (i in 0 until n) {
            target -= d2[i]
            if (target <= 0f) { picked = i; break }
        }
        centers[c] = floatArrayOf(r[picked], g[picked], b[picked])
    }
    return centers
}

/** Assign each pixel to its nearest center by squared Euclidean RGB distance. */
private fun assignClusters(
    r: FloatArray, g: FloatArray, b: FloatArray,
    centers: Array<FloatArray>, prevLabels: IntArray,
): IntArray {
    val n = r.size
    val k = centers.size
    val labels = IntArray(n)
    for (i in 0 until n) {
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (cc in 0 until k) {
            val dr = r[i] - centers[cc][0]
            val dg = g[i] - centers[cc][1]
            val db = b[i] - centers[cc][2]
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) { bestDist = dist; best = cc }
        }
        labels[i] = best
    }
    return labels
}

/** Recompute each center as the mean RGB of its assigned pixels. */
private fun updateCenters(
    r: FloatArray, g: FloatArray, b: FloatArray,
    centers: Array<FloatArray>, labels: IntArray,
) {
    val k = centers.size
    val sumR = FloatArray(k)
    val sumG = FloatArray(k)
    val sumB = FloatArray(k)
    val cnt = IntArray(k)
    for (i in labels.indices) {
        val lbl = labels[i]
        sumR[lbl] += r[i]; sumG[lbl] += g[i]; sumB[lbl] += b[i]; cnt[lbl]++
    }
    for (cc in 0 until k) {
        if (cnt[cc] > 0) {
            centers[cc][0] = sumR[cc] / cnt[cc]
            centers[cc][1] = sumG[cc] / cnt[cc]
            centers[cc][2] = sumB[cc] / cnt[cc]
        }
        // Empty cluster: leave the center where it is. It will keep attracting
        // zero pixels and be dropped from the result.
    }
}

/** Pack non-empty clusters into [ColorSwatch]s, sorted by population descending. */
private fun buildSwatches(
    centers: Array<FloatArray>, labels: IntArray, k: Int, n: Int,
): List<ColorSwatch> {
    val counts = IntArray(k)
    for (i in 0 until n) counts[labels[i]]++
    return (0 until k)
        .filter { counts[it] > 0 }
        .map {
            val rgb = (0xFF shl 24) or
                (centers[it][0].toInt().coerceIn(0, 255) shl 16) or
                (centers[it][1].toInt().coerceIn(0, 255) shl 8) or
                centers[it][2].toInt().coerceIn(0, 255)
            ColorSwatch(rgb, counts[it])
        }
        .sortedByDescending { it.population }
}
