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
 * Compose UI tests for [CaptureConfirmSheet] wired with [LowConfidenceHint]
 * (issue #20 / #23).
 *
 * Drives the confirm sheet directly with a synthetic [PendingCapture] —
 * no camera, no [com.palettemuse.core.ColorAnalyzer],
 * no [com.palettemuse.core.CaptureConfidencePolicy]. The UI only ever reads
 * the Boolean flag, so threshold retuning in ADR-0015 cannot break these tests.
 *
 * Border-thickness assertions on the retake button are intentionally omitted:
 * per the PRD and code review, that visual property is verified on-device.
 */
@RunWith(AndroidJUnit4::class)
class CaptureConfirmSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lowConfidenceTrue_showsHintText() {
        val pending = PendingCapture(
            imagePath = "/tmp/test.jpg",
            dominantRgb = 0xAABBCC,
            matchedTheme = null,
            isLowConfidence = true,
        )
        composeTestRule.setContent {
            CaptureConfirmSheet(
                pending = pending,
                onConfirm = {},
                onSaveAsNew = {},
                onDismiss = {},
            )
        }
        composeTestRule
            .onNodeWithText("颜色不太明显，要重拍吗？")
            .assertIsDisplayed()
    }

    @Test
    fun lowConfidenceFalse_hidesHintText() {
        val pending = PendingCapture(
            imagePath = "/tmp/test.jpg",
            dominantRgb = 0xAABBCC,
            matchedTheme = null,
            isLowConfidence = false,
        )
        composeTestRule.setContent {
            CaptureConfirmSheet(
                pending = pending,
                onConfirm = {},
                onSaveAsNew = {},
                onDismiss = {},
            )
        }
        composeTestRule
            .onAllNodesWithText("颜色不太明显，要重拍吗？")
            .assertCountEquals(0)
    }
}
