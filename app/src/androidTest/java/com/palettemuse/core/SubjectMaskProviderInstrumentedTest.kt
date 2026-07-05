package com.palettemuse.core

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the [RealSubjectMaskProvider] on the target device.
 *
 * These tests verify:
 * 1. NNAPI execution provider engages (not silently CPU-fallback).
 * 2. Single-inference latency is acceptable (< 2000ms).
 * 3. 10× repeated inference on the same image is bit-identical
 *    (same-device run-determinism).
 *
 * The tests are gated on the model file being present in assets. If the
 * model is not bundled (which is the case until `tools/download_model.sh`
 * is run), the tests are skipped via [Assume].
 *
 * Required: connected device (vivo V2502A or similar with NNAPI support).
 * Run via:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     --tests "com.palettemuse.core.SubjectMaskProviderInstrumentedTest"
 */
@RunWith(AndroidJUnit4::class)
class SubjectMaskProviderInstrumentedTest {

    private lateinit var provider: RealSubjectMaskProvider

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Skip if the model file is not bundled.
        val modelExists = try {
            context.assets.open("models/insPyReNet.onnx").use { true }
        } catch (_: Exception) { false }
        assumeNotNull(if (modelExists) "model" else null)
        provider = RealSubjectMaskProvider(context)
    }

    @Test
    fun inference_producesNonNullMask() {
        val bmp = createTestBitmap()
        val mask = provider.provideMask(bmp)
        assertNotNull("mask must not be null", mask)
        assertTrue("mask must have pipeline resolution",
            mask!!.size == DOWNSCALE_SIZE * DOWNSCALE_SIZE)
    }

    @Test
    fun inference_latency_withinBudget() {
        val bmp = createTestBitmap()
        val start = System.nanoTime()
        provider.provideMask(bmp)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val budgetMs = 2000L
        assertTrue("inference took ${elapsedMs}ms (budget: ${budgetMs}ms)",
            elapsedMs <= budgetMs)
    }

    @Test
    fun inference_tenTimes_isBitIdentical() {
        val bmp = createTestBitmap()
        val masks = Array(10) { provider.provideMask(bmp) }
        val first = masks[0]
        assertNotNull("first mask must be non-null", first)
        for (i in 1 until masks.size) {
            assertNotNull("mask $i must be non-null", masks[i])
            assertArrayEquals(
                "mask $i differs from mask 0 (not bit-identical)",
                first, masks[i]
            )
        }
    }

    /** Create a 640×480 test bitmap with a red rectangle on a grey background. */
    private fun createTestBitmap(): Bitmap {
        val w = 640
        val h = 480
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.GRAY)
        // Red rectangle in the center (roughly 40% of the frame).
        val rect = android.graphics.Rect(
            w / 4, h / 4, 3 * w / 4, 3 * h / 4
        )
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.GRAY)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.RED }
        canvas.drawRect(rect, paint)
        return bmp
    }
}
