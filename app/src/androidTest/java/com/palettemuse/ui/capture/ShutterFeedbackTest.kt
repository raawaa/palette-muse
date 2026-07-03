package com.palettemuse.ui.capture

import androidx.activity.ComponentActivity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the shutter feedback envelope (issue #31 / ADR-0018).
 *
 * Pins the three acceptance criteria that don't need an emulator:
 *
 * 1. **Button scale-down on press** — [ShutterButton]'s scale drops below 1.0
 *    when a `PressInteraction.Press` lands in its injected
 *    [MutableInteractionSource]. The current scale is read via the
 *    [ShutterScaleSemanticsKey] test seam.
 * 2. **Haptic fires on click** — the click callback runs [performHaptic]
 *    before [onShutter]. The test injects a counter as [performHaptic] and
 *    asserts the counter increments on `performClick`. This avoids depending
 *    on the platform `LocalHapticFeedback`, which has no JVM-safe fake.
 * 3. **Flash overlay peaks within 20ms** — bumping the `triggerKey` counter
 *    drives a [ShutterFlashOverlay] `LaunchedEffect` that animates alpha
 *    `0 → 0.8` over 20ms. The test reads the peak alpha through
 *    [ShutterFlashAlphaSemanticsKey] after `mainClock.advanceTimeBy(25)`.
 *
 * `mainClock.autoAdvance` is set to `false` so the assertions don't race the
 * frame clock — each test advances time explicitly to known milestones.
 * This also keeps the test emulator-friendly: there is no wait/poll loop on
 * a real-time frame stream.
 */
@RunWith(AndroidJUnit4::class)
class ShutterFeedbackTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shutterButton_pressedScaleDropsBelowOne() {
        composeTestRule.mainClock.autoAdvance = false
        val source = MutableInteractionSource()
        composeTestRule.setContent {
            ShutterButton(
                onShutter = {},
                interactionSource = source,
            )
        }
        composeTestRule.runOnUiThread {
            source.tryEmit(PressInteraction.Press(Offset.Zero))
        }
        // Wait past the 80ms scale animation so the value has settled at 0.9f.
        composeTestRule.mainClock.advanceTimeBy(120)
        val scale = composeTestRule.onNodeWithTag("shutter_button")
            .fetchSemanticsNode()
            .config
            .get(ShutterScaleSemanticsKey)
        assertTrue("scale should be < 1.0 while pressed, got $scale", scale < 1.0f)
    }

    @Test
    fun shutterButton_clickFiresHapticBeforeOnShutter() {
        val events = mutableListOf<String>()
        composeTestRule.setContent {
            ShutterButton(
                onShutter = { events += "shutter" },
                performHaptic = { events += "haptic" },
            )
        }
        composeTestRule.onNodeWithTag("shutter_button").performClick()
        assertEquals(
            "expected exactly one haptic then one shutter in order, got $events",
            listOf("haptic", "shutter"),
            events,
        )
    }

    @Test
    fun shutterFlashOverlay_animatesToPeakAlphaWithin20ms() {
        composeTestRule.mainClock.autoAdvance = false
        var triggerKey by mutableStateOf(0)
        composeTestRule.setContent {
            ShutterFlashOverlay(triggerKey = triggerKey)
        }
        composeTestRule.runOnUiThread { triggerKey = 1 }
        // 25ms covers one frame for the LaunchedEffect to start + the full
        // 20ms ramp-up to the 0.8f peak.
        composeTestRule.mainClock.advanceTimeBy(25)
        val alpha = composeTestRule.onNodeWithTag("shutter_flash")
            .fetchSemanticsNode()
            .config
            .get(ShutterFlashAlphaSemanticsKey)
        assertTrue(
            "flashAlpha should reach >= 0.6 within 20ms, got $alpha",
            alpha >= 0.6f
        )
    }
}