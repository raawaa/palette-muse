package com.palettemuse.ui.capture

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [LowConfidenceHint] (issue #20 / #21).
 *
 * The widget is intentionally trivial: a Boolean toggle that either shows one
 * line of text or shows nothing. These tests pin exactly that contract on the
 * semantics tree — no PendingCapture, no camera, no analyzer, no policy.
 *
 * Locking this in at the seam means ADR-0015's threshold retune in
 * [com.palettemuse.core.CaptureConfidencePolicy] cannot break the UI: the UI
 * only ever reads a Boolean, so its tests never see a threshold value.
 */
@RunWith(AndroidJUnit4::class)
class LowConfidenceHintTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun visible_true_rendersHintText() {
        composeTestRule.setContent {
            LowConfidenceHint(visible = true)
        }
        composeTestRule
            .onNodeWithText("颜色不太明显，要重拍吗？")
            .assertIsDisplayed()
    }

    @Test
    fun visible_false_rendersNoTextNode() {
        composeTestRule.setContent {
            LowConfidenceHint(visible = false)
        }
        composeTestRule
            .onAllNodesWithText("颜色不太明显，要重拍吗？")
            .assertCountEquals(0)
    }
}
