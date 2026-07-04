package com.palettemuse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeWithPhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val themes: List<ThemeWithPhotos> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            themeRepository.getAllThemesWithPhotos().collect { themes ->
                _uiState.value = HomeUiState(themes = themes, isLoading = false)
            }
        }
    }

    fun enterDeleteMode() {
        _uiState.value = _uiState.value.copy(isDeleting = true)
    }

    fun exitDeleteMode() {
        _uiState.value = _uiState.value.copy(isDeleting = false)
    }

    fun deleteTheme(themeId: String) {
        viewModelScope.launch {
            themeRepository.deleteTheme(themeId)
        }
    }
}
