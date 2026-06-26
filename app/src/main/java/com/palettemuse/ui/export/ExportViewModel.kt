package com.palettemuse.ui.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val previewBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val exportSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val posterRenderer: PosterRenderer
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        generatePreview()
    }

    private fun generatePreview() {
        viewModelScope.launch {
            try {
                val project = projectRepository.getProject(projectId)
                val palettes = projectRepository.getPalettesForProject(projectId)

                if (project == null) {
                    _uiState.value = ExportUiState(isLoading = false, error = "项目不存在")
                    return@launch
                }

                val photo = BitmapFactory.decodeFile(project.imagePath) ?: run {
                    _uiState.value = ExportUiState(isLoading = false, error = "图片加载失败")
                    return@launch
                }

                val config = PosterRenderer.PosterConfig(
                    title = project.title.ifEmpty { "Moodboard Color Harmony" },
                    primaryColor = palettes.firstOrNull()?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.GRAY }
                    } ?: Color.GRAY,
                    secondaryColor = palettes.getOrNull(1)?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.LTGRAY }
                    } ?: Color.LTGRAY,
                    accentColor = palettes.getOrNull(2)?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.DKGRAY }
                    } ?: Color.DKGRAY
                )

                val preview = posterRenderer.render(photo, config)
                _uiState.value = ExportUiState(previewBitmap = preview, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = ExportUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun savePoster(context: Context) {
        viewModelScope.launch {
            val bitmap = _uiState.value.previewBitmap ?: return@launch
            val uri = posterRenderer.saveToGallery(context, bitmap)
            _uiState.value = _uiState.value.copy(exportSuccess = uri != null)
        }
    }
}
