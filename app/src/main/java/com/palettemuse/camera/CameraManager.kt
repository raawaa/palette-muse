package com.palettemuse.camera

import android.content.Context
import android.graphics.Bitmap
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraManager @Inject constructor() {

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    interface FrameAnalyzer {
        fun analyze(pixels: IntArray, width: Int, height: Int)
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
                        analysis.setAnalyzer(executor) { imageProxy ->
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

    fun takePhoto(
        context: Context,
        onPhotoTaken: (Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = imageCapture ?: return

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "capture_${System.currentTimeMillis()}.jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri != null) {
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                            android.graphics.BitmapFactory.decodeStream(input)
                        }
                        if (bitmap != null) {
                            onPhotoTaken(bitmap)
                        }
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
        executor.shutdown()
    }

    private fun analyzeFrame(imageProxy: ImageProxy, analyzer: FrameAnalyzer) {
        // 由于设置了 OUTPUT_IMAGE_FORMAT_RGBA_8888，第一平面 = [A,R,G,B,A,R,G,B,...]
        val buffer = imageProxy.planes[0].buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val pixels = IntArray(width * height)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        for (i in 0 until width * height) {
            val offset = i * 4
            val r = bytes[offset + 1].toInt() and 0xFF
            val g = bytes[offset + 2].toInt() and 0xFF
            val b = bytes[offset + 3].toInt() and 0xFF
            pixels[i] = android.graphics.Color.rgb(r, g, b)
        }

        analyzer.analyze(pixels, width, height)
        imageProxy.close()
    }
}
