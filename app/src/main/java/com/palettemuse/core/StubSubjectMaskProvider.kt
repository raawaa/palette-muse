package com.palettemuse.core

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub [SubjectMaskProvider] that returns a deterministic center-crop mask
 * covering ~50% of the pixel area at the pipeline's working resolution.
 *
 * This stub doubles as a Hilt-injectable test double: it is deterministic,
 * has no external dependencies beyond the Android SDK, and can be replaced
 * by a mock in JVM unit tests. The real model ([RealSubjectMaskProvider])
 * supersedes this in production once the on-device spike (#51) clears.
 */
@Singleton
class StubSubjectMaskProvider @Inject constructor() : SubjectMaskProvider {

    override fun provideMask(bitmap: Bitmap): BooleanArray? {
        val w = DOWNSCALE_SIZE
        val h = DOWNSCALE_SIZE
        val total = w * h
        val mask = BooleanArray(total)
        // Center 50% crop: skip 1/4 of rows and columns on each side.
        // This gives coverage ≈ 0.50, well within the (0.05, 0.95) range.
        val marginX = w / 4
        val marginY = h / 4
        for (y in marginY until (h - marginY)) {
            for (x in marginX until (w - marginX)) {
                mask[y * w + x] = true
            }
        }
        return mask
    }
}
