package com.palettemuse.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.palettemuse.ui.capture.CaptureScreen
import com.palettemuse.ui.export.ExportScreen
import com.palettemuse.ui.export.ExportViewModel
import com.palettemuse.ui.home.HomeScreen
import com.palettemuse.ui.theme.ThemeDetailScreen
import com.palettemuse.ui.theme.ThemeDetailViewModel
import kotlinx.serialization.Serializable

object Routes {
    @Serializable
    data object Home : NavKey

    @Serializable
    data object Capture : NavKey

    @Serializable
    data class ThemeDetail(val themeId: String) : NavKey

    @Serializable
    data class Export(val themeId: String) : NavKey
}

@Composable
fun PaletteMuseNavGraph() {
    val backStack = rememberNavBackStack(Routes.Home)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Forward navigation: new content slides in from right, old slides out 1/3 to left
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
                slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut()
        },
        // Back navigation: returning content slides in from left, current slides 1/3 to right
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() togetherWith
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Routes.Home> {
                HomeScreen(
                    onNavigateToCapture = { backStack.add(Routes.Capture) },
                    // HomeScreen keeps its onNavigateToAnalyze(themeId) signature from Task 1;
                    // the lambda body now routes into ThemeDetail.
                    onNavigateToAnalyze = { themeId ->
                        backStack.add(Routes.ThemeDetail(themeId))
                    }
                )
            }
            entry<Routes.Capture> {
                CaptureScreen(
                    onNavigateToAnalyze = { themeId ->
                        backStack.removeAll { it !is Routes.Home }
                        backStack.add(Routes.ThemeDetail(themeId))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Routes.ThemeDetail> { key ->
                // Navigation 3: pass the NavKey to the ViewModel via its
                // AssistedFactory (see passingarguments/viewmodels/hilt recipe).
                // rememberViewModelStoreNavEntryDecorator scopes the VM to this key.
                val viewModel = hiltViewModel<ThemeDetailViewModel, ThemeDetailViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key) }
                )
                ThemeDetailScreen(
                    onNavigateToExport = { backStack.add(Routes.Export(key.themeId)) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel
                )
            }
            entry<Routes.Export> { key ->
                val viewModel = hiltViewModel<ExportViewModel, ExportViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key) }
                )
                ExportScreen(
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel
                )
            }
        }
    )
}
