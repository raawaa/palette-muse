package com.palettemuse.ui.capture

import com.palettemuse.data.repository.ThemeMatcher

/**
 * Temporal smoother for the camera viewfinder's TARGET pill.
 *
 * The raw per-frame match score from [ThemeMatcher] jitters: Palette quantization
 * can flip between nearby swatches on successive frames of the same scene, and
 * the score it produces is not a monotonic function of the underlying camera
 * input. The smoother stabilises two distinct outputs for the display:
 *
 *  1. **Match percentage** — the displayed 0–100 number is the exponential
 *     moving average of the raw score of the **current** theme, seeded from the
 *     first observation. Different themes' histories are never blended, so a
 *     switch does not show a phantom score halfway between the two.
 *  2. **Theme identity** — a theme must win [confirmFrames] consecutive frames
 *     before it takes over from the current display, and the same number of
 *     consecutive no-match frames must elapse before the display drops to
 *     fallback. This kills the "target flickers between two near-equal themes"
 *     failure mode and the "fallback flickers with a marginal match" mode.
 *
 * This sits at the **display layer** by design. The extraction module
 * ([com.palettemuse.core.ColorAnalyzer.extractCapturedColor]) stays single-sample
 * so that the shutter path (`CaptureViewModel.capturePhoto`) keeps reporting
 * the real score. See ADR-0001 ("Follow-up: temporal smoothing at the viewfinder
 * display layer") and issue #16.
 *
 * Not thread-safe; callers serialise calls onto a single coroutine / handler
 * (the ViewModel's `onFrameAnalyzed`).
 */
internal class ViewfinderSmoother(
    private val alpha: Double = DEFAULT_ALPHA,
    private val confirmFrames: Int = DEFAULT_CONFIRM_FRAMES,
) {
    private var currentThemeId: String? = null
    private var currentThemeName: String = ""
    /** -1.0 sentinel means "no smoothed value yet for the current theme". */
    private var smoothedScore: Double = -1.0

    private var pendingThemeId: String? = null
    private var pendingFrames: Int = 0
    private var pendingFallbackFrames: Int = 0

    /**
     * Smooths one per-frame best-match result into the next stable
     * [TargetState] for display. See class KDoc for the full contract.
     */
    fun smooth(scored: ThemeMatcher.ScoredTheme?): TargetState {
        val newId = scored?.theme?.id
        val newName = scored?.theme?.name ?: ""
        val rawScore = (scored?.score ?: 0).toDouble()

        return when {
            newId == null -> resolveFallback()
            newId == currentThemeId -> reaffirmCurrent(rawScore)
            else -> considerThemeChange(newId, newName, rawScore)
        }
    }

    /** Drop internal state. Call when the scene genuinely changes (e.g. lens flip). */
    fun reset() {
        currentThemeId = null
        currentThemeName = ""
        smoothedScore = -1.0
        pendingThemeId = null
        pendingFrames = 0
        pendingFallbackFrames = 0
    }

    private fun reaffirmCurrent(rawScore: Double): TargetState {
        // Reaffirmation clears any pending candidate and any in-progress fallback.
        pendingThemeId = null
        pendingFrames = 0
        pendingFallbackFrames = 0
        smoothedScore = if (smoothedScore < 0.0) rawScore
                        else alpha * rawScore + (1.0 - alpha) * smoothedScore
        return TargetState(currentThemeName, smoothedScore.toInt().coerceIn(0, 100), isFallback = false)
    }

    private fun considerThemeChange(newId: String, newName: String, rawScore: Double): TargetState {
        // Track consecutive wins for the candidate theme.
        if (newId == pendingThemeId) {
            pendingFrames++
        } else {
            pendingThemeId = newId
            pendingFrames = 1
        }
        pendingFallbackFrames = 0

        val shouldSwitch = currentThemeId == null || pendingFrames >= confirmFrames
        if (shouldSwitch) {
            currentThemeId = newId
            currentThemeName = newName
            pendingThemeId = null
            pendingFrames = 0
            // Seed EMA from the new theme's first observation so we do not blend
            // the previous theme's history into the new theme's display.
            smoothedScore = rawScore
        }
        // If we did not switch, the displayed score is the current theme's last
        // smoothed value, which has not changed this frame (we did not get a
        // sample for the current theme).
        return TargetState(currentThemeName, smoothedScore.toInt().coerceIn(0, 100), isFallback = false)
    }

    private fun resolveFallback(): TargetState {
        pendingThemeId = null
        pendingFrames = 0
        pendingFallbackFrames++

        if (currentThemeId == null) {
            // Already in fallback.
            smoothedScore = -1.0
            return TargetState("", 0, isFallback = true)
        }

        if (pendingFallbackFrames >= confirmFrames) {
            // Sustained no-match: drop to fallback, clear the EMA seed for the next theme.
            currentThemeId = null
            currentThemeName = ""
            smoothedScore = -1.0
            return TargetState("", 0, isFallback = true)
        }

        // Hold the current theme for a few more frames; let the EMA decay
        // toward 0 so the displayed score honestly reflects the weakening match.
        smoothedScore = if (smoothedScore < 0.0) 0.0
                        else alpha * 0.0 + (1.0 - alpha) * smoothedScore
        return TargetState(currentThemeName, smoothedScore.toInt().coerceIn(0, 100), isFallback = false)
    }

    companion object {
        /**
         * EMA weight applied to each new raw observation. With alpha = 0.2 the
         * time constant is ~5 frames (~167ms @30fps); after 15 frames the
         * smoother is at ~96% of a step change, which clears the ~500ms
         * responsiveness budget in the issue brief while still cutting raw
         * noise (sigma ~3-4) to displayed noise (sigma ~1-1.3), comfortably
         * within the +/-5% stability budget.
         */
        const val DEFAULT_ALPHA: Double = 0.2

        /**
         * Number of consecutive frames a candidate theme must win before it
         * takes over, and the same for sustained no-match before dropping to
         * fallback. 3 frames at 30fps is ~100ms -- imperceptible to the user
         * on a real change but long enough to absorb single-frame Palette
         * quantisation flips.
         */
        const val DEFAULT_CONFIRM_FRAMES: Int = 3
    }
}

