package com.palettemuse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeWithPhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val themes: List<ThemeWithPhotos> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        themeRepository.getAllThemesWithPhotos()
            .map { themes -> HomeUiState(themes = themes, isLoading = false) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun deleteTheme(themeId: String) {
        viewModelScope.launch { themeRepository.deleteTheme(themeId) }
    }
}
