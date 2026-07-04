package com.palettemuse.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorMatcher @Inject constructor() {

    /**
     * Computes the CIELAB ΔE between [targetRgb] and [sampleRgb] and returns a
     * 0–100 match percentage. Pure — no Android framework calls; see ADR 0006.
     */
    fun matchPercentage(targetRgb: Int, sampleRgb: Int): Int {
        // ΔE computed by the shared top-level `deltaE` (ColorUtils.kt) — the
        // single source of truth for perceptual color distance, reused by the
        // ADR-0020 perceptual-family merge. Behavior is byte-identical to the
        // former inline sRGB→Lab→ΔE; only the call site moved.
        val dE = deltaE(rgbFromInt(targetRgb), rgbFromInt(sampleRgb))
        return (100.0 - dE * 2.5).toInt().coerceIn(0, 100)
    }
}
