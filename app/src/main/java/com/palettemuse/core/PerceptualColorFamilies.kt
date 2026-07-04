package com.palettemuse.core

/**
 * Pure-Kotlin perceptual color-family merge. After k-means returns its sorted
 * swatches, [mergeSwatchesIntoPerceptualFamilies] unions centroids a human
 * would call "the same color" (CIELAB ΔE below a threshold) into families. The
 * confidence signals in [ColorAnalyzer] are then measured over these families
 * rather than raw single clusters, so a visually-uniform color that k-means
 * split across several near-duplicate centroids is unioned back into one
 * family before its population share is computed. See
 * `docs/adr/0020-population-share-over-perceptual-families.md`.
 *
 * Pure Kotlin (no `android.graphics`) — JVM-testable without a `Bitmap`
 * (ADR-0006). Deterministic: a pure function of the swatch set, so the whole
 * capture path stays bit-for-bit stable across runs and devices (ADR-0017's
 * determinism win is preserved — the merge introduces no RNG, no iteration on
 * nondeterministic structure).
 */

/**
 * A perceptual color family: the union of one or more k-means centroids that
 * are mutually within ΔE of each other. Mirrors [ColorSwatch]'s shape so the
 * downstream signal computation ([ColorAnalyzer]) treats families and raw
 * swatches with the same code.
 *
 * @property rgb        The family's representative centroid — the population-
 *                      weighted average of its member centroids' RGB (ADR-0021),
 *                      packed in the same `0xAARRGGBB` form as [ColorSwatch.rgb]
 *                      (alpha high byte preserved from the members). A color
 *                      family that k-means split across several near-duplicate
 *                      centroids is represented by its perceptual center of
 *                      mass rather than whichever single cluster happened to
 *                      carry the most pixels. Deterministic and independent of
 *                      input ordering: channel sums are order-free, and the
 *                      alpha byte is identical across members of one family
 *                      (they all originate from the same k-means pass). This
 *                      field IS the captured `rgb` [ColorAnalyzer] returns —
 *                      ADR-0020's "Out-of-Scope" on dominantHex was resolved by
 *                      ADR-0021.
 * @property population Sum of the member centroids' pixel counts.
 */
internal data class ColorFamily(val rgb: Int, val population: Int)

/**
 * Calibration seed: the CIELAB ΔE below which two k-means centroids are treated
 * as the same perceptual color family. ΔE=10 was selected by the ADR-0020
 * attribution experiment — at this threshold the three ~90%-single-color audit
 * frames and the pink-book frame clear the `(0.40, 1.5)` confidence thresholds
 * while the one genuinely fragmented frame (cluttered chair) stays low. ΔE=15
 * over-merges (clears the chair); ΔE=20 collapses every frame to ~1.0.
 *
 * This is a seed, not a product commitment — it must be re-validated as the
 * audit corpus grows (ADR-0020 Follow-up). ADR-0019's persisted signals make
 * growing that corpus cheap.
 */
internal const val PERCEPTUAL_MERGE_DELTA_E: Double = 10.0

/**
 * Merges k-means [swatches] into perceptual color families via union-find: any
 * two centroids whose CIELAB ΔE is strictly less than [deltaEThreshold] are
 * unioned into one family, and membership is transitive (if A≈B and B≈C then
 * A,B,C are one family even when A≉C — matching how a human calls a gradient
 * "one color"). Family [ColorFamily.population] is the sum of its members'
 * populations; [ColorFamily.rgb] is the population-weighted average of its
 * member centroids' RGB (ADR-0021), so the family is represented by its
 * perceptual center of mass. Returns families sorted by population descending,
 * ties broken by rgb, so the output is a pure deterministic function of the
 * input set (independent of input ordering or HashMap iteration).
 *
 * Contract:
 * - Returns an empty list when [swatches] is empty.
 * - A single swatch returns a single family carrying the same population.
 * - Two centroids whose ΔE equals [deltaEThreshold] are NOT merged — the
 *   comparison is `<` strict, matching the ADR convention (a centroid is a
 *   neighbor only when it is perceptually closer than the threshold, not at it).
 *
 * Performance: O(k²) pairwise ΔE comparisons, each doing two sRGB→Lab
 * conversions. For k=12 that is 66 comparisons — negligible next to the k-means
 * pass itself and far cheaper than the existing 96×96 downscale. No threading;
 * runs on the caller's analyzer thread.
 *
 * @param swatches k-means output. Order does not affect the result.
 * @param deltaEThreshold CIELAB ΔE below which two centroids are the same
 *   family. Defaults to [PERCEPTUAL_MERGE_DELTA_E]; exposed as a parameter so
 *   the `<`-strict boundary can be unit-tested precisely.
 */
internal fun mergeSwatchesIntoPerceptualFamilies(
    swatches: List<ColorSwatch>,
    deltaEThreshold: Double = PERCEPTUAL_MERGE_DELTA_E,
): List<ColorFamily> {
    if (swatches.isEmpty()) return emptyList()

    val n = swatches.size
    // Union-find over swatch indices. Union-by-rank + path compression keeps
    // the k² merges near-constant per op; both are deterministic given the
    // fixed i<j iteration order, so re-running on the same swatches yields a
    // bit-for-bit identical family partition.
    val parent = IntArray(n) { it }
    val rank = IntArray(n)

    fun find(x: Int): Int {
        var root = x
        while (parent[root] != root) root = parent[root]
        // Path compression: re-point every node on the path straight at root.
        var cur = x
        while (parent[cur] != root) {
            val next = parent[cur]
            parent[cur] = root
            cur = next
        }
        return root
    }

    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra == rb) return
        when {
            rank[ra] < rank[rb] -> parent[ra] = rb
            rank[ra] > rank[rb] -> parent[rb] = ra
            else -> { parent[rb] = ra; rank[ra] += 1 }
        }
    }

    // Pairwise ΔE; merge pairs strictly below threshold. The shared `deltaE`
    // (ColorUtils.kt) is the single source of truth for perceptual distance —
    // the same math ColorMatcher.matchPercentage uses (ADR-0020).
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            if (deltaE(rgbFromInt(swatches[i].rgb), rgbFromInt(swatches[j].rgb)) < deltaEThreshold) {
                union(i, j)
            }
        }
    }

    // Group members by root → one ColorFamily per root. Population is the sum;
    // the representative rgb is the population-weighted average of its member
    // centroids (ADR-0021) — a color family that k-means split across several
    // near-duplicate centroids is represented by its perceptual center of mass
    // rather than whichever single cluster happened to carry the most pixels.
    // Channel sums accumulate as Long (a full 96×96 = 9216-pixel family with
    // 8-bit channels and Int populations stays well within Long range). The
    // alpha high byte is taken from the lowest-index member (identical across
    // members of one family — they share a k-means origin), so the packed
    // `0xAARRGGBB` form matches [ColorSwatch.rgb]. Summation is order-free, so
    // the result stays bit-for-bit deterministic regardless of input order.
    val familyPopulation = HashMap<Int, Int>()
    val familySumR = HashMap<Int, Long>()
    val familySumG = HashMap<Int, Long>()
    val familySumB = HashMap<Int, Long>()
    val familyAlphaHigh = HashMap<Int, Int>()
    for (i in 0 until n) {
        val root = find(i)
        val swatch = swatches[i]
        val pop = swatch.population
        familyPopulation[root] = (familyPopulation[root] ?: 0) + pop
        familySumR[root] = (familySumR[root] ?: 0L) + (((swatch.rgb shr 16) and 0xFF).toLong() * pop)
        familySumG[root] = (familySumG[root] ?: 0L) + (((swatch.rgb shr 8) and 0xFF).toLong() * pop)
        familySumB[root] = (familySumB[root] ?: 0L) + ((swatch.rgb and 0xFF).toLong() * pop)
        if (root !in familyAlphaHigh) {
            familyAlphaHigh[root] = swatch.rgb and (0xFF shl 24)
        }
    }

    return familyPopulation.keys.map { root ->
        val pop = familyPopulation[root]!!
        ColorFamily(
            rgb = familyAlphaHigh[root]!! or
                (((familySumR[root]!! / pop).toInt() and 0xFF) shl 16) or
                (((familySumG[root]!! / pop).toInt() and 0xFF) shl 8) or
                ((familySumB[root]!! / pop).toInt() and 0xFF),
            population = pop,
        )
    }.sortedWith(
        // Highest population first; ties broken by lowest rgb so the order is
        // fully deterministic regardless of HashMap iteration.
        compareByDescending<ColorFamily> { it.population }.thenBy { it.rgb }
    )
}
