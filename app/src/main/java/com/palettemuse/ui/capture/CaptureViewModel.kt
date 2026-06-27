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

data class TargetState(val name: String, val matchPct: Int, val isFallback: Boolean)

data class PendingCapture(
    val imagePath: String,
    val dominantHex: String,
    val matchedTheme: ThemeEntity?
)

data class CaptureUiState(
    val matchPercentage: Int = 0,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isAnalyzing: Boolean = false,
    val pendingCapture: PendingCapture? = null,
    val targetTheme: TargetState = TargetState("Rose Gold", 0, isFallback = true)
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

    private val _themes = MutableStateFlow<List<ThemeEntity>>(emptyList())

    init {
        viewModelScope.launch {
            themeRepository.getAllThemes().collect { _themes.value = it }
        }
    }

    fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
        val sampleHex = colorMatcher.extractCenterAverageColor(pixels, width, height)
        val themes = _themes.value
        val best = themes
            .map { it to colorMatcher.matchPercentage(it.representativeHex, sampleHex) }
            .filter { it.second >= THEME_MATCH_THRESHOLD }
            .maxByOrNull { it.second }
        val target = if (best != null) {
            TargetState(best.first.name, best.second, isFallback = false)
        } else {
            TargetState("Rose Gold", 0, isFallback = true)
        }
        _uiState.value = _uiState.value.copy(
            targetTheme = target,
            matchPercentage = target.matchPct
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

    companion object {
        const val THEME_MATCH_THRESHOLD = 60
    }
}
