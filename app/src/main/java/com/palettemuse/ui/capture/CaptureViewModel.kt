package com.palettemuse.ui.capture

import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.CaptureConfidencePolicy
import com.palettemuse.core.CapturedColor
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.DOWNSCALE_SIZE
import com.palettemuse.core.SubjectMaskProvider
import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.data.repository.BitmapStorage
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeMatcher
import com.palettemuse.debug.DebugFrameDumper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Post-shutter extraction animation mode.
 * - [NONE]: no animation (before shutter or after animation completes).
 * - [SUBJECT_LOCKED]: a subject mask was applied — highlight the subject
 *   region and bloom the color from it.
 * - [FALLBACK]: no mask was available — a whole-frame color bloom without
 *   subject highlight.
 */
enum class ExtractionMode { NONE, SUBJECT_LOCKED, FALLBACK }

data class TargetState(val name: String, val matchPct: Int, val isFallback: Boolean)

data class PendingCapture(
    val imagePath: String,
    val dominantRgb: Int,
    val matchedTheme: ThemeEntity?,
    /**
     * Per ADR-0014 / issue #17: `true` when [CaptureConfidencePolicy] flagged
     * this capture as low-confidence based on `populationShare` and
     * `topVsSecondRatio`. The confirm sheet reads this flag to surface the
     * "this photo's color is unclear" prompt (UI is out of scope for the ADR;
     * the wiring lives here).
     */
    val isLowConfidence: Boolean = false,
    /**
     * The two [CaptureConfidencePolicy] input signals captured at the shutter
     * press, forwarded into [PhotoEntity] on confirm so a low-confidence
     * verdict can be audited after the fact (adb run-as + sqlite3). See
     * ADR-0019. Nullable because [setPending] test fixtures may omit them.
     */
    val populationShare: Double? = null,
    val topVsSecondRatio: Double? = null,
    /**
     * Subject-mask coverage fraction — the fraction of downscaled pixels
     * covered by the subject mask. Null when no subject mask was applied
     * (pre-saliency captures and null-mask fallbacks). Persisted via
     * [PhotoEntity.maskCoverage].
     */
    val maskCoverage: Double? = null
)

data class CaptureUiState(
    val matchPercentage: Int = 0,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isAnalyzing: Boolean = false,
    val pendingCapture: PendingCapture? = null,
    val targetTheme: TargetState = TargetState("", 0, isFallback = true),
    /**
     * Post-shutter extraction animation mode. Set on shutter press when the
     * mask provider result is known, and reset to [ExtractionMode.NONE] when
     * the animation completes and the confirm sheet is shown.
     */
    val extractionMode: ExtractionMode = ExtractionMode.NONE,
    /**
     * The captured color's RGB for the extraction bloom effect. Set when
     * [extractionMode] is non-NONE; the bloom uses this color as the
     * blooming radial gradient's center.
     */
    val extractedColor: Int? = null
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val colorAnalyzer: ColorAnalyzer,
    private val bitmapStorage: BitmapStorage,
    private val themeMatcher: ThemeMatcher,
    private val captureConfidencePolicy: CaptureConfidencePolicy,
    private val subjectMaskProvider: SubjectMaskProvider,
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
        val best = themeMatcher.bestMatch(_themes.value, captured.rgb)
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
            Trace.beginSection("capture.total")
            val t0 = SystemClock.elapsedRealtime()
            Log.i("CapturePerf", "shutter bitmap=${bitmap.width}x${bitmap.height}")
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val saveJob = async(Dispatchers.IO) {
                traceSection("capture.save") { bitmapStorage.saveCapture(bitmap) }
            }
            // Subject-mask extraction and color analysis in parallel after save
            val maskJob = async(Dispatchers.Default) {
                traceSection("capture.mask") {
                    val r = subjectMaskProvider.provideMask(bitmap)
                    r
                }
            }
            val imagePath = saveJob.await()
            val mask = maskJob.await()
            // Degeneracy check: if mask coverage is outside the valid range,
            // fall back to whole-photo (null mask). The confidence policy has
            // a corresponding degeneracy arm on maskCoverage as a safety net.
            val (effectiveMask, maskCoverage) = if (mask != null) {
                val total = DOWNSCALE_SIZE * DOWNSCALE_SIZE
                val coverage = mask.count { it }.toDouble() / total
                if (coverage in 0.05..0.95) mask to coverage else null to null
            } else {
                null to null
            }
            // Set extraction mode and preview color as soon as the mask and
            // dominant are known — the UI starts the extraction animation.
            val mode = if (effectiveMask != null) ExtractionMode.SUBJECT_LOCKED else ExtractionMode.FALLBACK
            _uiState.value = _uiState.value.copy(
                extractionMode = mode
            )
            val captured = traceSection("capture.analyze") {
                colorAnalyzer.extractCapturedColor(bitmap, effectiveMask)
            }
            // Attach maskCoverage to the captured color (the analyzer doesn't
            // know about coverage; we fill it here from the provider's output).
            val capturedWithCoverage = captured.copy(maskCoverage = maskCoverage)
            _uiState.value = _uiState.value.copy(
                extractedColor = capturedWithCoverage.rgb
            )
            Log.i("CapturePerf", "save+mask+analyze done +${SystemClock.elapsedRealtime() - t0}ms")
            val matched = traceSection("capture.match") {
                themeRepository.findMatchingTheme(capturedWithCoverage.rgb)
            }
            Log.i("CapturePerf", "match done +${SystemClock.elapsedRealtime() - t0}ms")
            val isLowConfidence = captureConfidencePolicy.isLowConfidence(capturedWithCoverage)
            // Fire-and-forget: dump is debug-only, best-effort, and PNG-compress
            // of the shutter bitmap (~1.3s on the Main thread) would otherwise
            // delay the confirmation sheet. Detached child of viewModelScope so
            // it is cancelled with the ViewModel and never escapes capture.
            viewModelScope.launch(Dispatchers.IO) { debugFrameDumper.dump(bitmap, captured, "shutter") }
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                pendingCapture = PendingCapture(
                    imagePath, capturedWithCoverage.rgb, matched, isLowConfidence,
                    capturedWithCoverage.populationShare, capturedWithCoverage.topVsSecondRatio,
                    maskCoverage = maskCoverage
                )
            )
            Log.i("CapturePerf", "sheet shown +${SystemClock.elapsedRealtime() - t0}ms")
        }
    }

    /**
     * Wrap [block] in a Perfetto trace section. Traces are no-ops when the host
     * process hasn't enabled tracing, so this is free in release. ADR-0018.
     */
    private inline fun <T> traceSection(name: String, block: () -> T): T {
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    fun confirmCapture() {
        val pending = _uiState.value.pendingCapture ?: return
        viewModelScope.launch {
            val matched = pending.matchedTheme
            if (matched != null) {
                themeRepository.savePhotoToTheme(
                    matched.id, pending.imagePath, pending.dominantRgb,
                    pending.populationShare, pending.topVsSecondRatio, pending.isLowConfidence,
                    pending.maskCoverage
                )
            } else {
                themeRepository.createThemeAndSave(
                    pending.imagePath, pending.dominantRgb,
                    pending.populationShare, pending.topVsSecondRatio, pending.isLowConfidence,
                    pending.maskCoverage
                )
            }
            _uiState.value = _uiState.value.copy(pendingCapture = null)
        }
    }

    fun saveAsNewTheme() {
        val pending = _uiState.value.pendingCapture ?: return
        viewModelScope.launch {
            themeRepository.createThemeAndSave(
                pending.imagePath, pending.dominantRgb,
                pending.populationShare, pending.topVsSecondRatio, pending.isLowConfidence,
                pending.maskCoverage
            )
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
        val pending = _uiState.value.pendingCapture ?: return
        _uiState.value = _uiState.value.copy(pendingCapture = null)
        viewModelScope.launch {
            bitmapStorage.deleteCapture(pending.imagePath)
        }
    }

    /**
     * Called by the extraction animation composable when the animation
     * finishes playing. Resets the extraction UI state so the confirm
     * sheet becomes the active layer.
     */
    fun onExtractionAnimationEnd() {
        _uiState.value = _uiState.value.copy(
            extractionMode = ExtractionMode.NONE,
            extractedColor = null
        )
    }

    @androidx.annotation.VisibleForTesting
    internal fun setPending(pending: PendingCapture) {
        _uiState.value = _uiState.value.copy(pendingCapture = pending)
    }
}
