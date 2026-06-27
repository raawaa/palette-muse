package com.palettemuse.ui.export

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.repository.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ExportViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ThemeRepository
    private val renderer = PosterRenderer()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val inlineExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(inlineExecutor)
            .setQueryExecutor(inlineExecutor)
            .build()
        repo = ThemeRepository(db.themeDao(), db.photoDao(), ColorMatcher(), ColorNamer())
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun loadsTheme_selectsUpToFourPhotos_rendersPoster() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        repo.savePhotoToTheme(id, "/cap1.jpg", "#C99A92")
        repo.savePhotoToTheme(id, "/cap2.jpg", "#B98A82")

        val vm = ExportViewModel(SavedStateHandle(mapOf("themeId" to id)), repo, renderer)
        advanceUntilIdle()

        val state = vm.uiState.first { !it.isLoading }
        assertEquals("#DCA8A6", state.theme?.representativeHex)
        assertEquals(3, state.photos.size)
        // Bento 2x2 = up to 4 photos selected.
        assertEquals(3, state.selectedPhotos.size)
        // Default template is Grid.
        assertEquals(PosterTemplate.Grid, state.selectedTemplate)
        // No renderable Bitmap on emulator (no real photo files) — but state must be loaded.
        assertNull(state.error)
    }

    @Test fun selectTemplate_gridIsSelectable_othersEmitHint() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val vm = ExportViewModel(SavedStateHandle(mapOf("themeId" to id)), repo, renderer)
        advanceUntilIdle()

        // Non-Grid chip surfaces a SnackBar hint instead of switching.
        vm.selectTemplate(PosterTemplate.Film)
        assertEquals(PosterTemplate.Grid, vm.uiState.value.selectedTemplate)
        assertEquals("胶片模板即将推出", vm.uiState.value.unsupportedTemplateHint)

        vm.consumeUnsupportedHint()
        assertNull(vm.uiState.value.unsupportedTemplateHint)

        // Grid is selectable.
        vm.selectTemplate(PosterTemplate.Grid)
        assertEquals(PosterTemplate.Grid, vm.uiState.value.selectedTemplate)
        assertNull(vm.uiState.value.unsupportedTemplateHint)
    }

    @Test fun missingTheme_emitsError() = runTest(dispatcher) {
        val vm = ExportViewModel(SavedStateHandle(mapOf("themeId" to "nope")), repo, renderer)
        advanceUntilIdle()
        val state = vm.uiState.first { !it.isLoading }
        assertNull(state.theme)
        assertEquals("主题不存在", state.error)
    }

    @Test fun themeIdArgMissing_throws() {
        // checkNotNull contract — constructor must fail fast on missing arg.
        var threw = false
        try {
            ExportViewModel(SavedStateHandle(), repo, renderer)
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test fun selectedPhotos_cappedAtFour() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        repeat(6) { i -> repo.savePhotoToTheme(id, "/cap$i.jpg", "#C99A92") }

        val vm = ExportViewModel(SavedStateHandle(mapOf("themeId" to id)), repo, renderer)
        advanceUntilIdle()
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(7, state.photos.size) // seed + 6 captures
        assertEquals(4, state.selectedPhotos.size) // Bento 2x2 cap
    }
}
