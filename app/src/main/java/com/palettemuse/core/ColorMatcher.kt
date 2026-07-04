package com.palettemuse.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorMatcher @Inject constructor() {

    /**
     * Computes the CIELAB ΔE between [targetHex] and [sampleHex] and returns a
     * 0–100 match percentage. Pure — no Android framework calls; see ADR 0006.
     */
    fun matchPercentage(targetHex: String, sampleHex: String): Int {
        // ΔE computed by the shared top-level `deltaE` (ColorUtils.kt) — the
        // single source of truth for perceptual color distance, reused by the
        // ADR-0020 perceptual-family merge. Behavior is byte-identical to the
        // former inline sRGB→Lab→ΔE; only the call site moved.
        val dE = deltaE(parseHexRgb(targetHex), parseHexRgb(sampleHex))
        return (100.0 - dE * 2.5).toInt().coerceIn(0, 100)
    }
}
