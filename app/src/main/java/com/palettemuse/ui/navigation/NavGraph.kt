package com.palettemuse.ui.navigation

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
