package com.palettemuse.core

import android.graphics.Bitmap

/**
 * Produces a subject mask from a captured bitmap.
 *
 * Implementations run the full-resolution [bitmap] through a saliency model
 * (or a deterministic stub) and return a [BooleanArray] at the pipeline's
 * working resolution (see [DOWNSCALE_SIZE]), where `true` marks subject pixels.
 * Returns `null` when no subject can be identified — the capture path then
 * falls back to whole-photo dominant color extraction.
 *
 * Threading: the caller (capture ViewModel) owns threading; the provider may
 * assume it is called on a background dispatcher.
 */
interface SubjectMaskProvider {

    /**
     * @param bitmap The full-resolution captured bitmap (the post-shutter frame).
     * @return A [BooleanArray] of length [DOWNSCALE_SIZE] × [DOWNSCALE_SIZE],
     *   or `null` when no subject could be identified.
     */
    fun provideMask(bitmap: Bitmap): BooleanArray?
}
