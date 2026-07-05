package com.palettemuse.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [SubjectMaskProvider] backed by the InSPyReNet salient-object-detection
 * model via ONNX Runtime Mobile, running on the NNAPI execution provider.
 *
 * The model (~28 MB) is bundled in APK assets under `models/insPyReNet.onnx`
 * and is loaded lazily on the first shutter press to avoid delaying the
 * viewfinder start. Inference runs on the calling thread (the capture path
 * calls this on [kotlinx.coroutines.Dispatchers.Default]).
 *
 * ## Model contract
 * - Input:  `float32[1,3,H,W]` — RGB image normalized to [0,1], where H×W is
 *   the model's native resolution (typically 384×384 or 512×512).
 * - Output: `float32[1,1,H,W]` — saliency map in [0,1]; values > 0.5 are
 *   considered subject pixels.
 *
 * ## NNAPI
 * NNAPI is the production execution provider. If NNAPI is unavailable on the
 * device (e.g. emulator, < API 27, or a GPU driver issue), ONNX Runtime
 * silently falls back to the CPU provider — the inference still produces a
 * valid mask, but latency may be higher. The [provideMask] method does NOT
 * fail on NNAPI absence; it logs a warning so the fallback rate can be
 * monitored via the instrumented test.
 *
 * ## Thread safety
 * The [OrtSession] is stateless on a single thread (one inference → one mask),
 * so no locking is needed. Multiple concurrent calls are safe because each
 * creates its own input/output tensors. The [OrtEnvironment] is a process-wide
 * singleton in ONNX Runtime and is thread-safe by design.
 */
@Singleton
class RealSubjectMaskProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : SubjectMaskProvider {

    /**
     * Model input resolution (width and height). InSPyReNet (Res2Net50) uses
     * 384×384 by default; the input is resized to this resolution before
     * inference. The output mask is then downscaled to the pipeline's working
     * resolution (see [DOWNSCALE_SIZE]).
     */
    private val modelInputSize = 384

    /**
     * Saliency threshold: pixels with output value >= this threshold are
     * considered subject pixels. 0.5 is the standard default for binary
     * saliency masks.
     */
    private val saliencyThreshold = 0.5f

    override fun provideMask(bitmap: Bitmap): BooleanArray? {
        val session = loadSession() ?: return null
        val t0 = System.nanoTime()

        try {
            // 1. Preprocess: resize bitmap to model input size, extract RGB,
            //    normalize to [0,1], and pack into CHW layout.
            val resized = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)
            val inputBuffer = preprocess(resized)
            resized.recycle()

            // 2. Run inference via ONNX Runtime.
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, longArrayOf(1L, 3L, modelInputSize.toLong(), modelInputSize.toLong()))
            val output = session.run(mapOf(INPUT_NAME to inputTensor))
            val outputTensor = output.get(OUTPUT_NAME)?.get() as? OnnxTensor
                ?: run { Log.w(TAG, "model output missing key '$OUTPUT_NAME'"); return null }
            val saliencyMap = FloatArray(modelInputSize * modelInputSize)
            outputTensor.floatBuffer.rewind()
            outputTensor.floatBuffer.get(saliencyMap)
            inputTensor.close()
            output.close()

            // 3. Downscale to pipeline working resolution.
            val downscaled = downscaleMask(saliencyMap, modelInputSize, DOWNSCALE_SIZE)

            val elapsed = (System.nanoTime() - t0) / 1_000_000
            Log.i(TAG, "inference done in ${elapsed}ms")

            return downscaled
        } catch (e: Exception) {
            Log.e(TAG, "inference failed", e)
            return null
        }
    }

    /** Preprocess a 384×384 ARGB_8888 bitmap into a CHW float array. */
    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val pixels = IntArray(modelInputSize * modelInputSize)
        bitmap.getPixels(pixels, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)
        val n = modelInputSize * modelInputSize
        val buffer = FloatBuffer.allocate(3 * n)
        val r = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val px = pixels[i]
            // Normalize: [0,255] → [0,1], then ImageNet mean/std (standard
            // preprocessing for Res2Net50-based models).
            r[i] = (((px shr 16) and 0xFF) / 255.0f - IMAGE_MEAN_R) / IMAGE_STD_R
            g[i] = (((px shr 8) and 0xFF) / 255.0f - IMAGE_MEAN_G) / IMAGE_STD_G
            b[i] = ((px and 0xFF) / 255.0f - IMAGE_MEAN_B) / IMAGE_STD_B
        }
        // CHW layout: all R channels, then all G, then all B.
        buffer.put(r)
        buffer.put(g)
        buffer.put(b)
        buffer.rewind()
        return buffer
    }

    /**
     * Downscale a [srcSize]×[srcSize] saliency map to [dstSize]×[dstSize] via
     * simple nearest-neighbour sampling. Returns a [BooleanArray] at the
     * pipeline's working resolution.
     */
    private fun downscaleMask(src: FloatArray, srcSize: Int, dstSize: Int): BooleanArray {
        val mask = BooleanArray(dstSize * dstSize)
        val scale = srcSize.toFloat() / dstSize.toFloat()
        for (dy in 0 until dstSize) {
            for (dx in 0 until dstSize) {
                val sx = (dx * scale).toInt().coerceIn(0, srcSize - 1)
                val sy = (dy * scale).toInt().coerceIn(0, srcSize - 1)
                mask[dy * dstSize + dx] = src[sy * srcSize + sx] >= saliencyThreshold
            }
        }
        return mask
    }

    // ── Lazy session loading ──────────────────────────────────────────────

    private val sessionRef = AtomicReference<OrtSession?>()

    /** Returns the cached session or loads it from assets. */
    private fun loadSession(): OrtSession? {
        var session = sessionRef.get()
        if (session != null) return session
        session = createSession()
        if (session != null) {
            sessionRef.compareAndSet(null, session)
        }
        return sessionRef.get()
    }

    /** Load the model from assets and create an ONNX Runtime session. */
    private fun createSession(): OrtSession? {
        return try {
            val modelBytes = context.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
            val opts = OrtSession.SessionOptions()
            // NNAPI is the production EP; CPU is the fallback if NNAPI is
            // unavailable. Setting NNAPI first gives it preference.
            try {
                opts.addNnapi()
                Log.i(TAG, "NNAPI execution provider registered")
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI not available, falling back to CPU", e)
            }
            ortEnvironment.createSession(modelBytes, opts).also {
                Log.i(TAG, "model loaded (${modelBytes.size / 1024} KB)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to load model from assets", e)
            null
        }
    }

    companion object {
        private const val TAG = "RealMaskProvider"
        private const val MODEL_ASSET_PATH = "models/insPyReNet.onnx"
        private const val INPUT_NAME = "input"
        private const val OUTPUT_NAME = "output"

        // ImageNet normalization (standard for Res2Net50-based saliency models).
        private const val IMAGE_MEAN_R = 0.485f
        private const val IMAGE_MEAN_G = 0.456f
        private const val IMAGE_MEAN_B = 0.406f
        private const val IMAGE_STD_R = 0.229f
        private const val IMAGE_STD_G = 0.224f
        private const val IMAGE_STD_B = 0.225f

        /** Process-wide ONNX Runtime environment (thread-safe singleton). */
        private val ortEnvironment: OrtEnvironment by lazy {
            OrtEnvironment.getEnvironment()
        }
    }
}
