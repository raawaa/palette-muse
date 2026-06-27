package com.palettemuse.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Compose UI tests for the rename / edit-color AlertDialogs used by
 * [ThemeDetailScreen]. These tests are intentionally scoped to the
 * composables themselves (no Hilt, no database) so they run as plain
 * Compose UI tests against a [ComponentActivity].
 */
@RunWith(AndroidJUnit4::class)
class ThemeDetailScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renameDialog_inputAndConfirm() {
        var confirmed: String? = null
        composeTestRule.setContent {
            RenameDialog(
                initial = "旧名",
                onConfirm = { confirmed = it },
                onDismiss = {}
            )
        }

        // Initial value is pre-populated into the text field.
        composeTestRule.onNodeWithText("旧名").assertExists()

        // Replace the text and confirm.
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("新名字")
        composeTestRule.onNodeWithText("确认").performClick()

        assertEquals("新名字", confirmed)
    }

    @Test
    fun editColorDialog_validHexConfirm() {
        var confirmed: String? = null
        composeTestRule.setContent {
            EditColorDialog(
                initial = "#DCA8A6",
                onConfirm = { confirmed = it },
                onDismiss = {}
            )
        }

        // Replace the hex value and confirm.
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("#FFB6C1")
        composeTestRule.onNodeWithText("确认").performClick()

        assertEquals("#FFB6C1", confirmed)
    }
}