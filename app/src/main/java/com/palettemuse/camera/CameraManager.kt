package com.palettemuse.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Trace
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager {

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Frame analysis executor. Kept separate from [captureExecutor] so a pending
     * shutter callback does not queue behind an in-flight k-means pass
     * (CaptureViewModel.capturePhoto perf — ADR-0018).
     */
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Capture callback executor. The ImageCapture→Bitmap decode runs here so
     * the main thread stays free across the shutter. Single-thread is enough:
     * shutter is one-shot.
     */
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    interface FrameAnalyzer {
        fun analyze(bitmap: Bitmap)
    }

    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        frameAnalyzer: FrameAnalyzer? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // ImageAnalysis (用于实时帧分析)
            if (frameAnalyzer != null) {
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeFrame(imageProxy, frameAnalyzer)
                        }
                    }
            }

            cameraProvider?.unbindAll()
            val useCases = mutableListOf(preview, imageCapture)
            if (imageAnalysis != null) useCases.add(imageAnalysis!!)
            cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, *useCases.toTypedArray())

        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Trigger a shutter and deliver the resulting [Bitmap] to [onPhotoTaken].
     *
     * Uses `ImageCapture.takePicture(executor, OnImageCapturedCallback)` —
     * i.e. no MediaStore round-trip. The ImageProxy→Bitmap decode runs on
     * [captureExecutor], so the main thread stays free for the duration of
     * the shutter. ADR-0018.
     */
    fun takePhoto(
        onPhotoTaken: (Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = imageCapture ?: return

        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = imageProxyToJpegBitmap(image)
                        if (bitmap != null) {
                            onPhotoTaken(bitmap)
                        } else {
                            onError(IllegalStateException("BitmapFactory.decodeByteArray returned null"))
                        }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    fun flipCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        currentFacing: Int,
        frameAnalyzer: FrameAnalyzer?
    ): Int {
        val newFacing = if (currentFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        startCamera(context, lifecycleOwner, previewView, newFacing, frameAnalyzer)
        return newFacing
    }

    fun cleanup() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        captureExecutor.shutdown()
    }

    private fun analyzeFrame(imageProxy: ImageProxy, analyzer: FrameAnalyzer) {
        // OUTPUT_IMAGE_FORMAT_RGBA_8888 → planes[0] is a packed RGBA byte buffer.
        val buffer = imageProxy.planes[0].buffer
        val width = imageProxy.width
        val height = imageProxy.height
        analyzer.analyze(rgbaBufferToBitmap(buffer, width, height))
        imageProxy.close()
    }

    /**
     * Decode an [ImageProxy] carrying JPEG bytes (the default ImageCapture output
     * format in CameraX 1.5.x) into a [Bitmap]. Returns null if the buffer
     * cannot be decoded.
     */
    private fun imageProxyToJpegBitmap(imageProxy: ImageProxy): Bitmap? {
        Trace.beginSection("capture.decodeJpeg")
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Copies a packed RGBA [ByteBuffer] (as produced by CameraX
     * `OUTPUT_IMAGE_FORMAT_RGBA_8888`) into an [Bitmap]. Exposed `internal` so the
     * byte-order concern is unit-testable (see ADR-0001).
     */
    internal fun rgbaBufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }
}
