package com.palettemuse.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeWithPhotos
import com.palettemuse.ui.navigation.Routes
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for the Export screen.
 *
 * @param data the loaded [ThemeWithPhotos] (theme + photos + palette) or null when missing / loading.
 * @param selectedTemplate the currently active template chip. All 4 templates are selectable in Plan 3.
 * @param previewBitmap the composed poster Bitmap used for save / share.
 *                       Built with [PosterRenderer.render] from photos picked per template +
 *                       theme name + primary color. May be null if no usable photo was found.
 * @param isLoading true while the initial load / render is in flight.
 * @param exportSuccess true once a save-to-gallery has succeeded (host shows a SnackBar).
 * @param error optional error message; null means no error.
 */
data class ExportUiState(
    val data: ThemeWithPhotos? = null,
    val selectedTemplate: PosterRenderer.TemplateType = PosterRenderer.TemplateType.GRID,
    val previewBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val exportSuccess: Boolean = false,
    val error: String? = null
)

// Navigation 3 passes arguments to ViewModels via assisted injection of the
// NavKey (see navigation-3 recipe "passingarguments/viewmodels/hilt"). The
// `@HiltViewModel(assistedFactory = ...)` form lets us receive the
// `Routes.Export` key and read `themeId` off it directly.
@HiltViewModel(assistedFactory = ExportViewModel.Factory::class)
class ExportViewModel @AssistedInject constructor(
    @Assisted private val navKey: Routes.Export,
    private val themeRepository: ThemeRepository,
    private val posterRenderer: PosterRenderer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val themeId: String = navKey.themeId

    @AssistedFactory
    interface Factory {
        fun create(navKey: Routes.Export): ExportViewModel
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Loads [ThemeWithPhotos] for [themeId] and renders the initial Grid preview. */
    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { themeRepository.getThemeWithPhotos(themeId) }
                .onSuccess { result ->
                    if (result == null) {
                        _uiState.value = ExportUiState(isLoading = false, error = "主题不存在")
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(
                        data = result,
                        isLoading = false,
                        error = null
                    )
                    generatePreview()
                }
                .onFailure { err ->
                    _uiState.value = ExportUiState(
                        isLoading = false,
                        error = err.message ?: "加载失败"
                    )
                }
        }
    }

    /**
     * Switches the active template chip and re-renders the preview with the photo count
     * appropriate for that template (GRID=4 / FILM=4 / JOURNAL=3 / MINIMAL=2,
     * capped to `data.photos.size`).
     */
    fun selectTemplate(template: PosterRenderer.TemplateType) {
        _uiState.value = _uiState.value.copy(selectedTemplate = template)
        viewModelScope.launch { generatePreview() }
    }

    /**
     * Builds the preview Bitmap via [PosterRenderer.render].
     *
     * Picks `count` photos (GRID=4 / FILM=4 / JOURNAL=3 / MINIMAL=2, capped to the
     * available photos), decodes each imagePath on [Dispatchers.IO], then renders
     * the chosen template on [Dispatchers.Default]. Missing/unreadable photo files
     * are silently skipped — we still render whatever we could decode.
     */
    private suspend fun generatePreview() {
        val data = _uiState.value.data ?: return
        val template = _uiState.value.selectedTemplate
        val count = when (template) {
            PosterRenderer.TemplateType.GRID -> 4
            PosterRenderer.TemplateType.FILM -> 4
            PosterRenderer.TemplateType.JOURNAL -> 3
            PosterRenderer.TemplateType.MINIMAL -> 2
        }.coerceAtMost(data.photos.size)
        val bitmaps = withContext(Dispatchers.IO) {
            data.photos.take(count).map { loadBitmap(it.imagePath) }
        }
        val photos = bitmaps.filterNotNull()
        val primary = parseHexOrGray(data.theme.representativeHex)
        val config = PosterRenderer.PosterConfig(
            title = data.theme.name.ifBlank { "Moodboard Color Harmony" },
            subtitle = "curated with Palette Muse",
            primaryColor = primary,
            secondaryColor = adjustLightness(primary, 0.85f),
            accentColor = adjustLightness(primary, 0.25f),
            photos = photos,
            template = template
        )
        val preview = withContext(Dispatchers.Default) {
            posterRenderer.render(null, config)
        }
        _uiState.value = _uiState.value.copy(previewBitmap = preview)
    }

    private fun loadBitmap(path: String): Bitmap? = try {
        File(path).takeIf { it.exists() }?.inputStream()?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    private fun parseHexOrGray(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (e: Exception) {
        Color.GRAY
    }

    /** Returns [base] with its luminance nudged by [factor] (0.0 = black, 1.0 = white). */
    private fun adjustLightness(base: Int, factor: Float): Int {
        val r = (Color.red(base) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    /** Clears the one-shot save-success flag after the host has shown confirmation. */
    fun consumeExportSuccess() {
        _uiState.value = _uiState.value.copy(exportSuccess = false)
    }

    /**
     * Shares the current preview Bitmap via [Intent.ACTION_SEND].
     *
     * The heavy PNG encoding + disk write happen on [Dispatchers.IO]; only the
     * ShareSheet launch runs on the calling (main) thread. No-op when there is
     * no poster to share.
     */
    fun sharePoster(context: Context) {
        val bitmap = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val file = File(context.cacheDir, "poster_share.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享到…"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "分享失败")
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun setPreviewBitmapForTest(bmp: Bitmap) {
        _uiState.value = _uiState.value.copy(previewBitmap = bmp)
    }

    /** Saves the current preview Bitmap to the gallery; flips [ExportUiState.exportSuccess]. */
    fun savePoster(context: Context) {
        val bitmap = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            runCatching { posterRenderer.saveToGallery(context, bitmap) }
                .onSuccess { uri ->
                    _uiState.value = _uiState.value.copy(exportSuccess = uri != null)
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(error = err.message ?: "保存失败")
                }
        }
    }
}