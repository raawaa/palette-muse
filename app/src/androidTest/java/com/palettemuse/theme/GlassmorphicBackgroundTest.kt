package com.palettemuse.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the "glass bars obscure content" bug.
 *
 * Root cause: [Modifier.glassmorphicBackground] used `Modifier.blur()`, which
 * (a) is a no-op against a uniform translucent fill — so there was no real
 * frosted-glass blur of the backdrop, and (b) blurred the bar's own
 * text/icons. Combined with a 0.6 alpha, content behind the bars was washed
 * out (perceived as "obscured"). The fix removed `.blur()` and lowered alpha.
 */
@RunWith(AndroidJUnit4::class)
class GlassmorphicBackgroundTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun glass_overContent_isTranslucentNotOpaque() {
        composeTestRule.setContent {
            // Vivid red behind the scrim. If the scrim were opaque, the red is hidden.
            Box(Modifier.testTag("stage").size(140.dp).background(Color.Red)) {
                Box(Modifier.fillMaxSize().glassmorphicBackground())
            }
        }
        val (r, g, b) = centerRgb("stage")
        // Translucent scrim => the red backdrop shows through (red channel dominates).
        assertTrue(
            "glass over red rendered ($r,$g,$b) — expected red to show through " +
                "(red channel well above green/blue), i.e. NOT an opaque cover",
            r > g + 40 && r > b + 40
        )
    }

    @Test
    fun glass_doesNotBlurChildren() {
        composeTestRule.setContent {
            Box(
                Modifier
                    .testTag("stage")
                    .size(140.dp)
                    .glassmorphicBackground(),
                contentAlignment = Alignment.Center
            ) {
                // Sharp black child. If the scrim blurred it, it would read as grey.
                Box(Modifier.size(60.dp).background(Color.Black))
            }
        }
        val (r, g, b) = centerRgb("stage")
        assertTrue(
            "black child inside the scrim rendered ($r,$g,$b) — expected pure black; " +
                "the scrim must not blur its own children",
            r <= 5 && g <= 5 && b <= 5
        )
    }

    private fun centerRgb(tag: String): Triple<Int, Int, Int> {
        val bmp = composeTestRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val p = bmp.getPixel(bmp.width / 2, bmp.height / 2)
        return Triple((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
    }
}
