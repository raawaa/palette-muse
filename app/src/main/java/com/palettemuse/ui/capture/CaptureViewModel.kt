package com.palettemuse.ui.capture

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.data.repository.BitmapStorage
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val targetTheme: TargetState = TargetState("", 0, isFallback = true)
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val colorAnalyzer: ColorAnalyzer,
    private val bitmapStorage: BitmapStorage,
    private val themeMatcher: ThemeMatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _themes = MutableStateFlow<List<ThemeEntity>>(emptyList())

    init {
        viewModelScope.launch {
            themeRepository.getAllThemes().collect { _themes.value = it }
        }
    }

    fun onFrameAnalyzed(bitmap: Bitmap) {
        if (_themes.value.isEmpty()) return // 等待 themes 缓存就绪 (首帧 themes 可能未加载)
        val sampleHex = colorAnalyzer.extractDominantHex(bitmap)
        val best = themeMatcher.bestMatch(_themes.value, sampleHex)
        val target = if (best != null) {
            TargetState(best.theme.name, best.score, isFallback = false)
        } else {
            TargetState("", 0, isFallback = true)
        }
        _uiState.value = _uiState.value.copy(
            targetTheme = target,
            matchPercentage = target.matchPct
        )
    }

    fun capturePhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val (imagePath, dominantHex) = withContext(Dispatchers.Default) {
                bitmapStorage.saveCapture(bitmap) to colorAnalyzer.extractDominantHex(bitmap)
            }
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
