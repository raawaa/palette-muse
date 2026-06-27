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
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeWithPhotos
import com.palettemuse.ui.navigation.Routes
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which poster template the user has selected.
 *
 * MVP: only [Grid] produces a real poster; the others are surfaced as chips but
 * selecting them only emits a user-facing message (the host shows a SnackBar).
 */
enum class PosterTemplate(val label: String) {
    Grid("网格"),
    Film("胶片"),
    Diary("日记"),
    Minimal("极简")
}

/**
 * UI state for the Export screen.
 *
 * @param theme      the loaded theme, or null while loading / when missing.
 * @param photos     all photos for the theme (preview renders up to the first 4).
 * @param palette    the 3-color palette built by [ThemeRepository] (hex strings).
 * @param selectedPhotos  the (up to 4) photos chosen for the Bento 2x2 preview.
 * @param selectedTemplate the currently active template chip (MVP: only Grid).
 * @param posterBitmap    the composed poster Bitmap used for save / share.
 *                       Built with [PosterRenderer.render] from the seed photo +
 *                       theme name + palette. May be null if no usable photo.
 * @param isLoading  true while the initial load / render is in flight.
 * @param exportSuccess true once a save-to-gallery has succeeded (host shows a toast).
 * @param unsupportedTemplateHint non-null when the user tapped a non-Grid chip;
 *                       the host surfaces this as a SnackBar then clears it.
 * @param error      optional error message; null means no error.
 */
data class ExportUiState(
    val theme: ThemeEntity? = null,
    val photos: List<PhotoEntity> = emptyList(),
    val palette: List<String> = emptyList(),
    val selectedPhotos: List<PhotoEntity> = emptyList(),
    val selectedTemplate: PosterTemplate = PosterTemplate.Grid,
    val posterBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val exportSuccess: Boolean = false,
    val unsupportedTemplateHint: String? = null,
    val error: String? = null
)

// Navigation 3 passes arguments to ViewModels via assisted injection of the
// NavKey (see navigation-3 recipe "passingarguments/viewmodels/hilt"). The
// `@HiltViewModel(assistedFactory = ...)` form lets us receive the
// `Routes.Export` key and read `themeId` off it directly — there is no
// `SavedStateHandle` populated with route args in Navigation 3.
@HiltViewModel(assistedFactory = ExportViewModel.Factory::class)
class ExportViewModel @AssistedInject constructor(
    @Assisted private val navKey: Routes.Export,
    private val themeRepository: ThemeRepository,
    private val posterRenderer: PosterRenderer
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

    /** Loads [ThemeWithPhotos] for [themeId], selects up to 4 photos and renders the poster. */
    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { themeRepository.getThemeWithPhotos(themeId) }
                .onSuccess { result ->
                    if (result == null) {
                        _uiState.value = ExportUiState(isLoading = false, error = "主题不存在")
                        return@launch
                    }
                    applyThemeWithPhotos(result)
                }
                .onFailure { err ->
                    _uiState.value = ExportUiState(
                        isLoading = false,
                        error = err.message ?: "加载失败"
                    )
                }
        }
    }

    private fun applyThemeWithPhotos(result: ThemeWithPhotos) {
        val selected = result.photos.take(BENTO_PHOTO_COUNT)
        // Pass the FULL photo list to renderPoster so the seed-photo-first
        // selection logic can find the seed even when the theme has >4
        // photos (PhotoDao orders by capturedAt DESC, so the seed photo —
        // inserted at theme-creation time and oldest — sorts last and is
        // dropped by `take(4)`). The Bento UI still uses the capped `selected`.
        val bitmap = renderPoster(result.theme, result.photos, result.palette)
        _uiState.value = ExportUiState(
            theme = result.theme,
            photos = result.photos,
            palette = result.palette,
            selectedPhotos = selected,
            posterBitmap = bitmap,
            isLoading = false
        )
    }

    /**
     * Builds the poster Bitmap via [PosterRenderer.render] from the seed photo
     * (or the first available photo) + theme name + palette.
     *
     * MVP note: PosterRenderer renders a *single* hero photo + title + palette
     * swatches. The 4-image Bento collage is a UI-only preview; the exported
     * file currently uses this single-photo composition. Multi-photo collages
     * are deferred to Plan 3.
     */
    private fun renderPoster(
        theme: ThemeEntity,
        photos: List<PhotoEntity>,
        palette: List<String>
    ): Bitmap? {
        val seedPhoto = photos.firstOrNull { it.isSeed } ?: photos.firstOrNull() ?: return null
        val photo = runCatching { BitmapFactory.decodeFile(seedPhoto.imagePath) }.getOrNull()
            ?: return null

        val config = PosterRenderer.PosterConfig(
            title = theme.name.ifBlank { "Moodboard Color Harmony" },
            subtitle = "curated with Palette Muse",
            primaryColor = palette.parseColorOr(0, Color.GRAY),
            secondaryColor = palette.parseColorOr(1, Color.LTGRAY),
            accentColor = palette.parseColorOr(2, Color.DKGRAY)
        )
        return posterRenderer.render(photo, config)
    }

    /** Selects a template chip. MVP: only [PosterTemplate.Grid] is supported. */
    fun selectTemplate(template: PosterTemplate) {
        if (template == PosterTemplate.Grid) {
            _uiState.value = _uiState.value.copy(
                selectedTemplate = template,
                unsupportedTemplateHint = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                unsupportedTemplateHint = "${template.label}模板即将推出"
            )
        }
    }

    /** Clears the one-shot SnackBar hint after the host has shown it. */
    fun consumeUnsupportedHint() {
        _uiState.value = _uiState.value.copy(unsupportedTemplateHint = null)
    }

    /** Clears the one-shot save-success flag after the host has shown confirmation. */
    fun consumeExportSuccess() {
        _uiState.value = _uiState.value.copy(exportSuccess = false)
    }

    /**
     * Shares the current poster Bitmap via [Intent.ACTION_SEND].
     *
     * The heavy PNG encoding + disk write happen on [Dispatchers.IO]; only the
     * ShareSheet launch runs on the calling (main) thread. No-op when there is
     * no poster to share.
     */
    fun sharePoster(context: Context) {
        val bitmap = _uiState.value.posterBitmap ?: return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.cacheDir, "poster_share.png")
                    file.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrNull()
            } ?: return@launch
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享海报"))
        }
    }

    /** Saves the current poster Bitmap to the gallery; flips [ExportUiState.exportSuccess]. */
    fun savePoster(context: Context) {
        val bitmap = _uiState.value.posterBitmap ?: return
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

    private fun List<String>.parseColorOr(index: Int, fallback: Int): Int =
        getOrNull(index)?.let { hex -> runCatching { Color.parseColor(hex) }.getOrNull() } ?: fallback

    companion object {
        /** Number of photos surfaced in the Bento 2x2 preview grid. */
        const val BENTO_PHOTO_COUNT = 4
    }
}
