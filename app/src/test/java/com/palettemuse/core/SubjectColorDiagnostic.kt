package com.palettemuse.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.PrintWriter
import javax.imageio.ImageIO
import kotlin.math.sqrt

/**
 * Regression gate + diagnostic for the subject-color extraction pipeline
 * (seam 1 in ADR-0024's testing plan).
 *
 * Runs the REAL captured-color entry point [analyzePixels] on the labeled
 * photo corpus in two configurations:
 *
 * 1. **Baseline** (`mask = null`): whole-photo dominant — the pre-saliency
 *    behavior. On leaky-mask scenes (vivid subject on neutral background)
 *    the background wins and ΔE to the expected subject color is high.
 * 2. **With mask**: the pre-computed InSPyReNet mask is applied so k-means
 *    runs only on subject pixels. The captured color's ΔE to the expected
 *    subject color is compared against a per-photo regression baseline
 *    (see [Photo.baselineDeltaE]).
 *
 * The `.npy` masks and photos are gitignored (local-only calibration
 * data) and produced by `tools/saliency_prototype/gen_masks.py`. When
 * absent, the test is skipped via `assumeTrue` — it only runs as a gate
 * on a machine that has the corpus.
 *
 * Output: `tools/subject_diagnostic.txt` with a per-photo comparison
 * (baseline vs masked ΔE, pass/regress per baseline).
 *
 * Run: `./gradlew test --tests "com.palettemuse.core.SubjectColorDiagnostic"`
 */
class SubjectColorDiagnostic {

    /**
     * One labeled photo + its current ΔE baseline (measured with InSPyReNet
     * masks at commit time). The test catches *regressions* — ΔE getting
     worse than the recorded baseline — not ideal performance. When mask
     quality or k-means tuning improves, tighten these baselines down.
     *
     * Why some baselines are loose (ADR-0024 calibration debt):
     * - 红色头盔 / 绿色盒子: mask works well, ΔE < 15 (ideal).
     * - 黄色蛋帽 / 红色桶: mask helps massively (+74/+35 ΔE) but k-means
     *   darkens the centroid; expected colors are vision-probe approximations.
     * - 玫红 begonia 茎: thin-stem subject; InSPyReNet mask misses it at
     *   384×384, so the masked result ≈ baseline (known limitation).
     */
    private data class Photo(
        val name: String,
        val expectedHex: String,
        val label: String,
        val baselineDeltaE: Double,
    )

    private val photos = listOf(
        Photo("capture_1783152305498.jpg", "B83A5C", "玫红 begonia 茎", baselineDeltaE = 60.0),
        Photo("capture_1783152314469.jpg", "C8202C", "红色头盔", baselineDeltaE = 15.0),
        Photo("capture_1783152327286.jpg", "4F6B5C", "绿色盒子", baselineDeltaE = 15.0),
        Photo("capture_1783152334059.jpg", "F5CE3E", "黄色蛋帽", baselineDeltaE = 35.0),
        Photo("capture_1783152336618.jpg", "D41A2A", "红色 McBoom 桶", baselineDeltaE = 30.0),
    )

    private val root = File("/Users/yuwenjie/Code/palette-muse")
    private val photoDir = File(root, "tools/photos")
    private val maskDir = File(root, "tools/saliency_prototype/masks96")

    private data class Result(
        val photo: Photo,
        val baselineRgb: Int,
        val baselineDe: Double,
        val maskedRgb: Int,
        val maskedDe: Double,
        val maskCoverage: Double,
    )

    /**
     * Asserts that the subject-mask pipeline meets the per-photo regression
     * baseline for every labeled photo. Also dumps a baseline-vs-masked
     * comparison to `tools/subject_diagnostic.txt`.
     *
     * Skips entirely when the photo corpus or masks are absent (local-only
     * calibration data, gitignored).
     */
    @Test
    fun subjectMaskPipeline_meetsRegressionBaselines() {
        // Skip if the corpus isn't present (CI, other machines).
        assumeTrue(
            "photo corpus not found at $photoDir — skipping subject-color regression gate",
            photoDir.isDirectory && photos.all { File(photoDir, it.name).exists() },
        )
        assumeTrue(
            "InSPyReNet masks not found at $maskDir — skipping subject-color regression gate. " +
                "Generate with: cd tools/saliency_prototype && uv run python gen_masks.py",
            maskDir.isDirectory && photos.all {
                File(maskDir, it.name.replace(".jpg", "_mask96.npy")).exists()
            },
        )

        val results = photos.map { p -> analyzePhoto(p) }

        // Human-readable dump for debugging.
        val out = PrintWriter(File(root, "tools/subject_diagnostic.txt"), "UTF-8")
        out.println("Subject-color regression gate (per-photo baseline = current measured ΔE)")
        out.println()
        for (r in results) dumpResult(out, r)
        out.close()

        // Regression gate: each photo's masked ΔE must be ≤ its per-photo
        // baseline. Baselines are calibration seeds (see Photo KDoc); tighten
        // when mask/k-means tuning improves.
        val failures = results.filter { it.maskedDe > it.photo.baselineDeltaE }
        check(failures.isEmpty()) {
            buildString {
                appendLine("Subject-mask pipeline regressed (masked ΔE exceeded per-photo baseline):")
                for (f in failures) {
                    appendLine("  ${f.photo.label}: masked ΔE=${"%.1f".format(f.maskedDe)} > baseline=${"%.1f".format(f.photo.baselineDeltaE)}  (captured=#%06X expected=#${f.photo.expectedHex})".format(f.maskedRgb and 0xFFFFFF))
                }
            }
        }
    }

    // ── internals ──────────────────────────────────────────────────

    /** Runs both baseline and masked paths for one photo. */
    private fun analyzePhoto(p: Photo): Result {
        val pixels = decodeTo96(File(photoDir, p.name))
        val mask = loadMaskNpy(File(maskDir, p.name.replace(".jpg", "_mask96.npy")))
        val expectedRgb = p.expectedHex.toInt(16)

        val baseline = analyzePixels(pixels, mask = null)
        val masked = analyzePixels(pixels, mask = mask)
        val coverage = mask.count { it }.toDouble() / mask.size

        return Result(
            photo = p,
            baselineRgb = baseline.rgb,
            baselineDe = deltaE(rgbFromInt(baseline.rgb), rgbFromInt(expectedRgb)),
            maskedRgb = masked.rgb,
            maskedDe = deltaE(rgbFromInt(masked.rgb), rgbFromInt(expectedRgb)),
            maskCoverage = coverage,
        )
    }

    /** Writes a single photo's comparison to the diagnostic dump. */
    private fun dumpResult(out: PrintWriter, r: Result) {
        val pass = if (r.maskedDe <= r.photo.baselineDeltaE) "PASS" else "REGRESS"
        val improvement = r.baselineDe - r.maskedDe
        out.println("========== ${r.photo.name}  (${r.photo.label} ≈ #${r.photo.expectedHex}) ==========")
        out.println("  baseline (no mask):  #${"%06X".format(r.baselineRgb and 0xFFFFFF)}   ΔE=${"%.1f".format(r.baselineDe)}")
        out.println("  masked:              #${"%06X".format(r.maskedRgb and 0xFFFFFF)}   ΔE=${"%.1f".format(r.maskedDe)}   coverage=${"%.1f%%".format(r.maskCoverage * 100)}")
        out.println("  → $pass  (regression baseline ΔE<${"%.1f".format(r.photo.baselineDeltaE)})   improvement=${"%+.1f".format(improvement)} ΔE")
        out.println()
    }

    /**
     * Decode JPEG → 96×96 opaque ARGB [IntArray], matching the pipeline's
     * [DOWNSCALE_SIZE] resolution normalization (ADR-0001).
     */
    private fun decodeTo96(file: File): IntArray {
        val img = ImageIO.read(file) ?: error("decode failed: ${file.name}")
        val target = BufferedImage(DOWNSCALE_SIZE, DOWNSCALE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img, 0, 0, DOWNSCALE_SIZE, DOWNSCALE_SIZE, null)
        g.dispose()
        val argb = target.getRGB(0, 0, DOWNSCALE_SIZE, DOWNSCALE_SIZE, null, 0, DOWNSCALE_SIZE)
        for (i in argb.indices) argb[i] = argb[i] or (0xFF shl 24)
        return argb
    }

    /**
     * Load a NumPy `.npy` file containing a `bool[96,96]` mask (produced by
     * `tools/saliency_prototype/gen_masks.py`). The `.npy` format is:
     * 6-byte magic (`\x93NUMPY`) + 2-byte version + 2-byte header length +
     * ASCII header dict + raw data. For `|b1` (bool) the data is 9216 bytes
     * of 0/1 — we read it directly into a [BooleanArray].
     */
    private fun loadMaskNpy(file: File): BooleanArray {
        val bytes = file.readBytes()
        // Verify magic: \x93 N U M P Y
        require(bytes.size >= 10 && bytes[0] == 0x93.toByte() &&
            String(bytes, 1, 5, Charsets.US_ASCII) == "NUMPY") { "not a .npy file: ${file.name}" }
        val major = bytes[6].toInt()
        val headerLen = if (major == 1) {
            (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        } else {
            (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8) or
                ((bytes[10].toInt() and 0xFF) shl 16) or ((bytes[11].toInt() and 0xFF) shl 24)
        }
        val dataOffset = if (major == 1) 10 + headerLen else 12 + headerLen
        val expected = DOWNSCALE_SIZE * DOWNSCALE_SIZE
        require(bytes.size >= dataOffset + expected) {
            "mask data too short: got ${bytes.size - dataOffset} bytes, expected $expected"
        }
        return BooleanArray(expected) { bytes[dataOffset + it].toInt() != 0 }
    }
}
