package com.palettemuse.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.palettemuse.ui.analyze.AnalyzeScreen
import com.palettemuse.ui.capture.CaptureScreen
import com.palettemuse.ui.export.ExportScreen
import com.palettemuse.ui.home.HomeScreen
import kotlinx.serialization.Serializable

object Routes {
    @Serializable
    data object Home : NavKey

    @Serializable
    data object Capture : NavKey

    @Serializable
    data class Analyze(val projectId: String) : NavKey

    @Serializable
    data class Export(val projectId: String) : NavKey
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
                    onNavigateToAnalyze = { projectId ->
                        backStack.add(Routes.Analyze(projectId))
                    }
                )
            }
            entry<Routes.Capture> {
                CaptureScreen(
                    onNavigateToAnalyze = { projectId ->
                        backStack.removeAll { it !is Routes.Home }
                        backStack.add(Routes.Analyze(projectId))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Routes.Analyze> { key ->
                AnalyzeScreen(
                    projectId = key.projectId,
                    onNavigateToExport = { backStack.add(Routes.Export(key.projectId)) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Routes.Export> { key ->
                ExportScreen(
                    projectId = key.projectId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
