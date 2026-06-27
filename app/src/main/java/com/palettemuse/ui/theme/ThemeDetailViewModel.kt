package com.palettemuse.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeWithPhotos
import com.palettemuse.ui.navigation.Routes
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the Theme Detail screen.
 *
 * @param data the loaded [ThemeWithPhotos] (theme + photos + palette) or null when missing.
 * @param isLoading true while the initial load is in flight.
 * @param error optional error message; null means no error.
 * @param isDeleted true once the user has deleted the theme — the host should pop back.
 */
data class ThemeDetailUiState(
    val data: ThemeWithPhotos? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDeleted: Boolean = false
)

// Navigation 3 passes arguments to ViewModels via assisted injection of the
// NavKey (see navigation-3 recipe "passingarguments/viewmodels/hilt"). The
// `@HiltViewModel(assistedFactory = ...)` form lets us receive the
// `Routes.ThemeDetail` key and read `themeId` off it directly — there is no
// `SavedStateHandle` populated with route args in Navigation 3.
@HiltViewModel(assistedFactory = ThemeDetailViewModel.Factory::class)
class ThemeDetailViewModel @AssistedInject constructor(
    @Assisted private val navKey: Routes.ThemeDetail,
    private val themeRepository: ThemeRepository
) : ViewModel() {

    val themeId: String = navKey.themeId

    @AssistedFactory
    interface Factory {
        fun create(navKey: Routes.ThemeDetail): ThemeDetailViewModel
    }

    private val _uiState = MutableStateFlow(ThemeDetailUiState())
    val uiState: StateFlow<ThemeDetailUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    /** Re-fetches [ThemeWithPhotos] for [themeId] from the repository. */
    fun reload() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { themeRepository.getThemeWithPhotos(themeId) }
                .onSuccess { result ->
                    _uiState.value = ThemeDetailUiState(
                        data = result,
                        isLoading = false,
                        error = if (result == null) "主题不存在" else null
                    )
                }
                .onFailure { err ->
                    _uiState.value = ThemeDetailUiState(
                        isLoading = false,
                        error = err.message ?: "加载失败"
                    )
                }
        }
    }

    /** Renames the theme and refreshes the UI state. */
    fun renameTheme(name: String) {
        viewModelScope.launch {
            runCatching { themeRepository.renameTheme(themeId, name) }
                .onSuccess { reload() }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(error = err.message ?: "重命名失败")
                }
        }
    }

    /** Updates the theme's representative color (and thus palette head) and refreshes. */
    fun updateThemeColor(hex: String) {
        viewModelScope.launch {
            runCatching { themeRepository.updateThemeColor(themeId, hex) }
                .onSuccess { reload() }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(error = err.message ?: "更新颜色失败")
                }
        }
    }

    /** Deletes the theme; host should observe [ThemeDetailUiState.isDeleted] and pop. */
    fun deleteTheme() {
        viewModelScope.launch {
            runCatching { themeRepository.deleteTheme(themeId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isDeleted = true, data = null)
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(error = err.message ?: "删除失败")
                }
        }
    }
}
