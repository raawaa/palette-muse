package com.palettemuse.ui.capture

import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.data.repository.PhotoStorage
import com.palettemuse.data.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CapturedSwatch(
    val hexColor: String,
    val semanticName: String,
    val matchPercentage: Int
)

data class PendingCapture(
    val imagePath: String,
    val dominantHex: String,
    val matchedTheme: ThemeEntity?
)

data class CaptureUiState(
    val matchPercentage: Int = 0,
    val capturedSwatches: List<CapturedSwatch> = emptyList(),
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isAnalyzing: Boolean = false,
    val pendingCapture: PendingCapture? = null
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val colorAnalyzer: ColorAnalyzer,
    private val photoStorage: PhotoStorage,
    private val colorMatcher: ColorMatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
        val sampleHex = colorMatcher.extractCenterAverageColor(pixels, width, height)
        _uiState.value = _uiState.value.copy(
            matchPercentage = colorMatcher.matchPercentage("#B76E79", sampleHex)
        )
    }

    fun capturePhoto(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val imagePath = photoStorage.save(bitmap)
            val dominantHex = colorAnalyzer.extractDominantHex(bitmap)
            val matched = themeRepository.findMatchingTheme(dominantHex)
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                pendingCapture = PendingCapture(imagePath, dominantHex, matched)
            )
        }
    }

    fun confirmCapture() {
        val pending = _uiState.value.pendingCapture ?: return
        viewModelScope.launch {
            val matched = pending.matchedTheme
            if (matched != null) {
                themeRepository.savePhotoToTheme(matched.id, pending.imagePath, pending.dominantHex)
            } else {
                themeRepository.createThemeAndSave(pending.imagePath, pending.dominantHex)
            }
            _uiState.value = _uiState.value.copy(pendingCapture = null)
        }
    }

    fun saveAsNewTheme() {
        val pending = _uiState.value.pendingCapture ?: return
        viewModelScope.launch {
            themeRepository.createThemeAndSave(pending.imagePath, pending.dominantHex)
            _uiState.value = _uiState.value.copy(pendingCapture = null)
        }
    }

    fun flipCamera() {
        _uiState.value = _uiState.value.copy(
            lensFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }

    fun dismissPending() {
        _uiState.value = _uiState.value.copy(pendingCapture = null)
    }

    @androidx.annotation.VisibleForTesting
    internal fun setPending(pending: PendingCapture) {
        _uiState.value = _uiState.value.copy(pendingCapture = pending)
    }
}
