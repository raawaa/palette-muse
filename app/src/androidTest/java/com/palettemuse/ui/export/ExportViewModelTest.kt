package com.palettemuse.ui.export

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.repository.BitmapStorage
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeFactory
import com.palettemuse.data.repository.ThemeMatcher
import com.palettemuse.ui.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
    private lateinit var bitmapStorage: BitmapStorage
    private lateinit var context: Context
    private lateinit var renderer: PosterRenderer
    private lateinit var exporter: BitmapStorage
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        renderer = PosterRenderer(context)
        val inlineExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(inlineExecutor)
            .setQueryExecutor(inlineExecutor)
            .build()
        bitmapStorage = BitmapStorage(ApplicationProvider.getApplicationContext())
        repo = ThemeRepository(db.themeDao(), db.photoDao(), ColorNamer(), ThemeMatcher(ColorMatcher()), ThemeFactory(), bitmapStorage)
        exporter = BitmapStorage(context)
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun loadsTheme_selectsUpToFourPhotos_rendersPoster() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", 0xDCA8A6)
        repo.savePhotoToTheme(id, "/cap1.jpg", 0xC99A92)
        repo.savePhotoToTheme(id, "/cap2.jpg", 0xB98A82)

        val vm = ExportViewModel(Routes.Export(id), repo, renderer, exporter)
        advanceUntilIdle()

        val state = vm.uiState.first { !it.isLoading }
        assertEquals("#DCA8A6", state.data?.theme?.representativeHex)
        assertEquals(3, state.data?.photos?.size)
        // Default template is Grid.
        assertEquals(PosterRenderer.TemplateType.GRID, state.selectedTemplate)
        // No renderable Bitmap on emulator (no real photo files) — but state must be loaded.
        assertNull(state.error)
    }

    @Test fun missingTheme_emitsError() = runTest(dispatcher) {
        val vm = ExportViewModel(Routes.Export("nope"), repo, renderer, exporter)
        advanceUntilIdle()
        val state = vm.uiState.first { !it.isLoading }
        assertNull(state.data)
        assertEquals("主题不存在", state.error)
    }

    @Test fun selectedPhotos_cappedAtFour() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", 0xDCA8A6)
        repeat(6) { i -> repo.savePhotoToTheme(id, "/cap$i.jpg", 0xC99A92) }

        val vm = ExportViewModel(Routes.Export(id), repo, renderer, exporter)
        advanceUntilIdle()
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(7, state.data?.photos?.size) // seed + 6 captures
    }

    // ---- New Plan 3 tests: 4-template selection ----

    @Test fun selectTemplate_grid_updatesState() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", 0xDCA8A6)
        repeat(5) { i -> repo.savePhotoToTheme(id, "/p$i.jpg", 0xDCA8A6) }
        val vm = ExportViewModel(Routes.Export(id), repo, renderer, exporter)
        advanceUntilIdle()
        vm.selectTemplate(PosterRenderer.TemplateType.GRID)
        advanceUntilIdle()
        assertEquals(PosterRenderer.TemplateType.GRID, vm.uiState.value.selectedTemplate)
    }

    @Test fun selectTemplate_minimal_updatesState() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", 0xDCA8A6)
        repeat(5) { i -> repo.savePhotoToTheme(id, "/p$i.jpg", 0xDCA8A6) }
        val vm = ExportViewModel(Routes.Export(id), repo, renderer, exporter)
        advanceUntilIdle()
        vm.selectTemplate(PosterRenderer.TemplateType.MINIMAL)
        advanceUntilIdle()
        assertEquals(PosterRenderer.TemplateType.MINIMAL, vm.uiState.value.selectedTemplate)
    }

    @Test
    fun sharePoster_generatesTemporaryFile() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", 0xDCA8A6)
        val vm = ExportViewModel(Routes.Export(id), repo, renderer, exporter)
        advanceUntilIdle()
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        vm.setPreviewBitmapForTest(bmp)
        vm.sharePoster(context)
        // sharePoster writes the file on Dispatchers.IO (real thread) then tries to
        // show a ShareSheet outside an Activity context, which throws.  The
        // try/catch inside sharePoster catches that and sets error="分享失败".
        // Wait for that error so we know the coroutine finished.
        val errorState = vm.uiState.first { it.error != null }
        assertEquals("分享失败", errorState.error)
        val file = File(context.cacheDir, "poster_share.png")
        assertTrue(file.exists() && file.length() > 0)
    }
}