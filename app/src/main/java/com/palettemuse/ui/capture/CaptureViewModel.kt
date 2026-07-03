package com.palettemuse.ui.capture

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.CaptureConfidencePolicy
import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.data.repository.BitmapStorage
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeMatcher
import com.palettemuse.debug.DebugFrameDumper
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
    val matchedTheme: ThemeEntity?,
    /**
     * Per ADR-0014 / issue #17: `true` when [CaptureConfidencePolicy] flagged
     * this capture as low-confidence based on `populationShare` and
     `topVsSecondRatio`. The confirm sheet reads this flag to surface the
     "this photo's color is unclear" prompt (UI is out of scope for the ADR;
     the wiring lives here).
     */
    val isLowConfidence: Boolean = false,
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
    private val themeMatcher: ThemeMatcher,
    private val captureConfidencePolicy: CaptureConfidencePolicy,
    private val debugFrameDumper: DebugFrameDumper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _themes = MutableStateFlow<List<ThemeEntity>>(emptyList())

    /**
     * Display-layer smoother for the TARGET pill (see class KDoc and issue #16).
     * Constructed inside the ViewModel so the public constructor signature stays
     * stable for the existing instrumented test suite.
     */
    private val viewfinderSmoother = ViewfinderSmoother()

    init {
        viewModelScope.launch {
            themeRepository.getAllThemes().collect { _themes.value = it }
        }
    }

    fun onFrameAnalyzed(bitmap: Bitmap) {
        if (_themes.value.isEmpty()) return // 等待 themes 缓存就绪 (首帧 themes 可能未加载)
        val captured = colorAnalyzer.extractCapturedColor(bitmap)
        val sampleHex = captured.hex
        val best = themeMatcher.bestMatch(_themes.value, sampleHex)
        // Display-layer smoothing only: capturePhoto() below still uses the
        // raw score for the real shutter decision.
        val target = viewfinderSmoother.smooth(best)
        _uiState.value = _uiState.value.copy(
            targetTheme = target,
            matchPercentage = target.matchPct
        )
    }

    fun capturePhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val (imagePath, captured) = withContext(Dispatchers.Default) {
                bitmapStorage.saveCapture(bitmap) to colorAnalyzer.extractCapturedColor(bitmap)
            }
            val dominantHex = captured.hex
            val matched = themeRepository.findMatchingTheme(dominantHex)
            val isLowConfidence = captureConfidencePolicy.isLowConfidence(captured)
            // Debug-only: dump the bitmap ColorAnalyzer actually saw, alongside
            // the analyzer's verdict. Self-gated inside the dumper (no-op in
            // release builds via ApplicationInfo.FLAG_DEBUGGABLE). See
            // `docs/adr/0015-capture-debug-frame-dumper.md` and the dumper KDoc.
            debugFrameDumper.dump(bitmap, captured, "shutter")
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                pendingCapture = PendingCapture(imagePath, dominantHex, matched, isLowConfidence)
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
        // Flipping lenses is a genuine scene change; drop the smoother's
        // history so the new viewfinder does not show a stale theme.
        viewfinderSmoother.reset()
    }

    fun dismissPending() {
        _uiState.value = _uiState.value.copy(pendingCapture = null)
    }

    @androidx.annotation.VisibleForTesting
    internal fun setPending(pending: PendingCapture) {
        _uiState.value = _uiState.value.copy(pendingCapture = pending)
    }
}
