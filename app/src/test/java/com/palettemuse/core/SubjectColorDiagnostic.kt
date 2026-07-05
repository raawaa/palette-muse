package com.palettemuse.core

import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.PrintWriter
import javax.imageio.ImageIO
import kotlin.math.sqrt

/**
 * One-shot diagnostic: re-runs the REAL capture pipeline (kMeansQuantize +
 * mergeSwatchesIntoPerceptualFamilies) on the experiment photos to see the
 * family breakdown for each captured scene. Not a unit test of behavior —
 * run via `./gradlew test --tests SubjectColorDiagnostic` and read
 * tools/subject_diagnostic.txt.
 *
 * Prints, per photo: every perceptual family (hex, population, populationShare,
 * Lab, chroma, saturation, ΔE to the expected subject color). Useful for
 * debugging which family k-means selects as dominant and why.
 */
class SubjectColorDiagnostic {

    private data class Photo(val name: String, val expectedHex: String, val label: String)

    private val photos = listOf(
        Photo("capture_1783152305498.jpg", "FFB83A5C", "玫红 begonia 茎"),
        Photo("capture_1783152314469.jpg", "FFC8202C", "红色头盔"),
        Photo("capture_1783152327286.jpg", "FF4F6B5C", "绿色盒子"),
        Photo("capture_1783152334059.jpg", "FFF5CE3E", "黄色蛋帽"),
        Photo("capture_1783152336618.jpg", "FFD41A2A", "红色 McBoom 桶"),
    )

    private val root = File("/Users/yuwenjie/Code/palette-muse")

    @Test
    fun dumpFamilyBreakdown() {
        val photoDir = File(root, "tools/photos")
        val out = PrintWriter(File(root, "tools/subject_diagnostic.txt"), "UTF-8")
        for (p in photos) {
            val file = File(photoDir, p.name)
            out.println("\n========== ${p.name}  (expected ${p.label} ≈ #${p.expectedHex.drop(2)}) ==========")
            if (!file.exists()) { out.println("MISSING"); continue }
            analyze(out, file, p)
        }
        out.close()
    }

    private fun analyze(out: PrintWriter, file: File, p: Photo) {
        val pixels = decodeTo96(file)
        val total = pixels.size
        val swatches = kMeansQuantize(pixels, k = TARGET_COLOR_COUNT)
        val families = mergeSwatchesIntoPerceptualFamilies(swatches)
        val expectedRgb = p.expectedHex.toLong(16).toInt()
        val expectedLab = rgbToLab(rgbFromInt(expectedRgb))

        var nearestIdx = 0
        var nearestDe = Double.MAX_VALUE
        families.forEachIndexed { i, f ->
            val de = deltaE(rgbFromInt(f.rgb), rgbFromInt(expectedRgb))
            if (de < nearestDe) { nearestDe = de; nearestIdx = i }
        }

        out.println("total 96×96 pixels = $total   family count = ${families.size}   expected L*=${"%.1f".format(expectedLab[0])} a*=${"%.1f".format(expectedLab[1])} b*=${"%.1f".format(expectedLab[2])}")
        out.println("rank | hex       | pop    | share  | L*     | a*      | b*      | chroma | sat   | ΔE→exp")
        families.forEachIndexed { i, f ->
            val lab = rgbToLab(rgbFromInt(f.rgb))
            val chroma = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
            val sat = chroma / maxOf(lab[0], 1.0)
            val de = deltaE(rgbFromInt(f.rgb), rgbFromInt(expectedRgb))
            val mark = if (i == nearestIdx) " ◀ subject" else ""
            out.println(
                "%4d | #%06X | %6d | %.3f | %6.2f | %7.2f | %7.2f | %6.2f | %5.2f | %6.2f%s".format(
                    i, f.rgb and 0xFFFFFF, f.population, f.population.toDouble() / total,
                    lab[0], lab[1], lab[2], chroma, sat, de, mark
                )
            )
        }

        val sf = families[nearestIdx]
        val sLab = rgbToLab(rgbFromInt(sf.rgb))
        val sSat = sqrt(sLab[1] * sLab[1] + sLab[2] * sLab[2]) / maxOf(sLab[0], 1.0)
        out.println("→ subject family #%06X  share=%.3f  sat=%.2f  ΔE-to-expected=%.2f".format(
            sf.rgb and 0xFFFFFF, sf.population.toDouble() / total, sSat, nearestDe))
    }

    /** Decode JPEG, downscale to 96×96 bilinear, return opaque ARGB IntArray. */
    private fun decodeTo96(file: File): IntArray {
        val img = ImageIO.read(file) ?: error("decode failed: ${file.name}")
        val target = BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img, 0, 0, 96, 96, null)
        g.dispose()
        val argb = target.getRGB(0, 0, 96, 96, null, 0, 96)
        for (i in argb.indices) argb[i] = argb[i] or (0xFF shl 24)
        return argb
    }
}
