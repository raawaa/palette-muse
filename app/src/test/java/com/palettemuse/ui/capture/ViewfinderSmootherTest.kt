package com.palettemuse.ui.capture

import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.data.repository.ThemeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the viewfinder-side temporal smoothing. Each test maps to an
 * acceptance criterion in issue #16:
 *
 *  - "stable +/-5% when holding the camera steady on a uniform surface" -> [smooth_steadySceneIsStable]
 *  - "follows a real color change within ~500ms" -> [smooth_followsRealColorChange]
 *  - "theme name does not flicker between themes" -> [smooth_themeNameDoesNotFlicker]
 *  - "fallback also behaves stably -- does not flicker" -> [smooth_fallbackDoesNotFlickerOnMarginalMatch]
 *  - + edge cases for the seam itself (first match, drop to fallback).
 */
class ViewfinderSmootherTest {

    private fun theme(id: String, name: String, rgb: Int) =
        ThemeEntity(id = id, name = name, representativeRgb = rgb)

    private fun match(theme: ThemeEntity, score: Int) =
        ThemeMatcher.ScoredTheme(theme, score)

    /** 30-frame at 30fps = 1 second. Each frame the same color, same score. */
    @Test
    fun smooth_steadySceneIsStable() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        val outputs = (1..30).map { s.smooth(match(rose, 85)) }
        // Never falls back while a theme is being asserted
        assertTrue(outputs.all { !it.isFallback && it.name == "Rose" })
        // +/-5 of the steady raw value once the EMA has settled (after the first frame).
        val settled = outputs.drop(5)
        assertTrue(
            "expected settled scores within +/-5 of 85, got $settled",
            settled.all { kotlin.math.abs(it.matchPct - 85) <= 5 }
        )
    }

    /** A noisy raw signal around a steady mean should produce a much steadier smoothed output. */
    @Test
    fun smooth_jitteringRawScoreIsDamped() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        // Raw scores alternate +/-10 around mean 80
        val noisy = listOf(80, 70, 90, 68, 92, 71, 89, 70, 90, 72, 88, 70, 90, 71, 89, 70, 90, 71, 89, 70)
        val outputs = noisy.map { s.smooth(match(rose, it)) }
        val settled = outputs.drop(10).map { it.matchPct }
        val range = settled.max() - settled.min()
        // The displayed range after warm-up must be much smaller than the raw range
        assertTrue("settled range $range should be < raw range", range < 20)
        // Theme identity never flips -- single theme, no flicker
        assertTrue(outputs.all { it.name == "Rose" })
    }

    /** When two themes trade the top score frame-to-frame, the displayed theme must not flicker. */
    @Test
    fun smooth_themeNameDoesNotFlicker() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        val green = theme("b", "Green", 0x00FF00)
        // Establish Rose first (first-frame acceptance)
        val first = s.smooth(match(rose, 80))
        assertEquals("Rose", first.name)
        // Now alternate winners -- neither should accumulate 3 consecutive frames
        val outputs = (1..6).map { frame ->
            val winner = if (frame % 2 == 0) green else rose
            s.smooth(match(winner, 82))
        }
        // Displayed theme must remain Rose for the whole sequence
        assertTrue(
            "theme flickered: ${outputs.map { it.name }}",
            outputs.all { it.name == "Rose" }
        )
    }

    /** A new theme must eventually take over once it has won the right number of consecutive frames. */
    @Test
    fun smooth_switchesAfterConsecutiveFrames() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        val green = theme("b", "Green", 0x00FF00)
        // Establish Rose
        s.smooth(match(rose, 80))
        // Three consecutive Green wins
        s.smooth(match(green, 82)) // pending 1
        s.smooth(match(green, 82)) // pending 2
        val switched = s.smooth(match(green, 82)) // pending 3 -> switch
        assertEquals("Green", switched.name)
    }

    /** Once showing a theme, the smoother must drop to fallback only after several no-match frames. */
    @Test
    fun smooth_dropsToFallbackAfterConsecutiveNoMatch() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        s.smooth(match(rose, 80))
        s.smooth(null) // pending 1
        s.smooth(null) // pending 2 -- still Rose
        val dropped = s.smooth(null) // pending 3 -> drop
        assertTrue(dropped.isFallback)
        assertEquals("", dropped.name)
    }

    /** The fallback / theme boundary must not flicker: once a marginal match takes hold,
     *  the display holds that theme across a few no-match frames, so the user sees a
     *  stable TARGET pill instead of one that bounces between "rose name" and
     *  "unmatched" on successive frames. */
    @Test
    fun smooth_fallbackDoesNotFlickerOnMarginalMatch() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        // Start in fallback (no themes ever asserted)
        s.smooth(null)
        // Marginal wins separated by nulls -- neither side accumulates 3 consecutive frames
        val outputs = listOf(
            s.smooth(match(rose, 61)),
            s.smooth(null),
            s.smooth(match(rose, 61)),
            s.smooth(null),
            s.smooth(match(rose, 61))
        )
        // Invariant: the displayed (name, isFallback) must not change across the sequence.
        // Flicker would be: [fallback, Rose, fallback, Rose, ...].
        val transitions = outputs.zipWithNext().count { (a, b) ->
            a.isFallback != b.isFallback || a.name != b.name
        }
        assertEquals(
            "display flickered: ${outputs.map { it.isFallback to it.name }}",
            0, transitions
        )
    }

    /** A new theme should follow a real color change within the ~500ms (15-frame) budget. */
    @Test
    fun smooth_followsRealColorChange() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        val green = theme("b", "Green", 0x00FF00)
        // Warm up on Rose
        repeat(10) { s.smooth(match(rose, 80)) }
        // Scene changes to Green -- held steady for 15 frames ( ~500ms @30fps)
        val afterChange = (1..15).map { s.smooth(match(green, 80)) }
        // By the end of the budget, the new theme must be displayed
        assertEquals("Green", afterChange.last().name)
    }

    /** First frame with a theme: accept immediately (no prior state to confirm against). */
    @Test
    fun smooth_firstFrameMatchIsAccepted() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        val out = s.smooth(match(rose, 80))
        assertEquals("Rose", out.name)
        assertFalse(out.isFallback)
    }

    /** After a theme switch, the EMA for the new theme must seed from the new theme's score,
     *  not be polluted by the previous theme's smoothed history. */
    @Test
    fun smooth_emaDoesNotMixAcrossThemes() {
        val s = ViewfinderSmoother(confirmFrames = 2)
        val rose = theme("a", "Rose", 0xDCA8A6)
        val green = theme("b", "Green", 0x00FF00)
        // Warm up on Rose at 80
        repeat(20) { s.smooth(match(rose, 80)) }
        // Switch to Green
        s.smooth(match(green, 60))  // pending 1
        val switched = s.smooth(match(green, 60))  // pending 2 -> switch
        // First displayed score for Green should be near its raw score (60), not blended
        // with Rose's 80. We accept a 1-step EMA window: within +/-5.
        assertEquals("Green", switched.name)
        assertTrue(
            "expected score near 60, got ${switched.matchPct}",
            kotlin.math.abs(switched.matchPct - 60) <= 5
        )
    }

    /** matchPercentage on the returned TargetState must be a real percentage (not 0 / 100 outlier). */
    @Test
    fun smooth_targetStateCarriesItsOwnScore() {
        val s = ViewfinderSmoother()
        val rose = theme("a", "Rose", 0xDCA8A6)
        val out = s.smooth(match(rose, 80))
        assertNotEquals(0, out.matchPct)
        assertTrue(out.matchPct in 1..100)
    }
}
