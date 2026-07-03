package com.palettemuse.data.repository

import com.palettemuse.core.ColorMatcher
import com.palettemuse.data.model.ThemeEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the theme-match rule: given a sample color and a set of themes, returns the
 * theme whose representative color is closest by ΔE color distance, provided it
 * clears [MATCH_THRESHOLD]. Returns null when nothing clears the cutoff.
 *
 * Pure — performs no I/O. The two callers are its adapters and differ only in
 * where the theme list comes from:
 *  - [ThemeRepository.findMatchingTheme] reads themes from the DB, then delegates.
 *  - `CaptureViewModel` runs it per camera frame against a cached theme list,
 *    because a DB read on every frame is too slow.
 *
 * Post-ADR-0001 both callers feed the **same** sample color — the Palette-quantized
 * dominant hex from [com.palettemuse.core.ColorAnalyzer.extractCapturedColor] (its
 * `.hex` field), so the rule genuinely lives in one place. See
 * `docs/adr/0003-themematcher-stays-class-threshold-private.md` for why this stays a
 * class with a private threshold.
 *
 * @see ScoredTheme
 */
@Singleton
class ThemeMatcher @Inject constructor(
    private val colorMatcher: ColorMatcher
) {

    /** A [theme] paired with its match [score] (0–100). */
    data class ScoredTheme(val theme: ThemeEntity, val score: Int)

    /**
     * Picks the best-matching theme for [sampleHex]. Themes scoring below
     * [MATCH_THRESHOLD] are excluded; on a tie the earliest theme in [themes] wins.
     */
    fun bestMatch(themes: List<ThemeEntity>, sampleHex: String): ScoredTheme? =
        themes
            .map { ScoredTheme(it, colorMatcher.matchPercentage(it.representativeHex, sampleHex)) }
            .filter { it.score >= MATCH_THRESHOLD }
            .maxByOrNull { it.score }

    private companion object {
        /** Below this ΔE-derived match %, a color is not considered a match for a theme. */
        const val MATCH_THRESHOLD = 60
    }
}
