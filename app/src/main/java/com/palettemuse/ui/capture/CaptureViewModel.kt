package com.palettemuse.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.ColorRole
import com.palettemuse.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

data class CaptureUiState(
    val matchPercentage: Int = 0,
    val targetColor: String = "#B76E79",
    val targetColorName: String = "Rose Gold",
    val capturedSwatches: List<CapturedSwatch> = emptyList(),
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isAnalyzing: Boolean = false
)

data class CapturedSwatch(
    val hexColor: String,
    val semanticName: String,
    val matchPercentage: Int
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val colorAnalyzer: ColorAnalyzer,
    private val colorNamer: ColorNamer,
    private val colorMatcher: ColorMatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    // 由 CameraManager 每帧调用
    fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
        val sampleHex = colorMatcher.extractCenterAverageColor(pixels, width, height)
        val match = colorMatcher.matchPercentage(_uiState.value.targetColor, sampleHex)
        _uiState.value = _uiState.value.copy(matchPercentage = match)
    }

    fun capturePhoto(photoBitmap: Bitmap, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            // 保存图片到 App 内部存储
            val imagePath = saveImageToInternalStorage(photoBitmap)

            // 分析颜色
            val result = colorAnalyzer.analyze(photoBitmap)
            val targetMatch = colorMatcher.matchPercentage(
                _uiState.value.targetColor,
                result.primaryHex ?: "#808080"
            )

            val projectId = UUID.randomUUID().toString()
            val palettes = listOfNotNull(
                result.primaryHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.PRIMARY,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it),
                        matchPercentage = targetMatch
                    )
                },
                result.secondaryHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.SECONDARY,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it)
                    )
                },
                result.accentHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.ACCENT,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it)
                    )
                }
            )

            // 添加到临时色样列表
            val primaryHex = result.primaryHex ?: "#808080"
            val swatch = CapturedSwatch(
                hexColor = primaryHex,
                semanticName = colorNamer.nameColor(primaryHex),
                matchPercentage = targetMatch
            )
            _uiState.value = _uiState.value.copy(
                capturedSwatches = _uiState.value.capturedSwatches + swatch,
                isAnalyzing = false
            )

            onSaved(projectId)
        }
    }

    fun flipCamera() {
        _uiState.value = _uiState.value.copy(
            lensFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String {
        val file = File(context.filesDir, "captures")
        file.mkdirs()
        val imageFile = File(file, "capture_${System.currentTimeMillis()}.jpg")
        FileOutputStream(imageFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        return imageFile.absolutePath
    }
}
