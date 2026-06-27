package com.palettemuse.ui.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.repository.InternalPhotoStorage
import com.palettemuse.data.repository.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ThemeRepository
    private lateinit var storage: InternalPhotoStorage
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Run Room queries/transactions inline so DAO suspend calls complete
        // synchronously within the calling coroutine. This eliminates the race
        // where Room's real IO executor escapes runTest's virtual clock and
        // advanceUntilIdle() cannot drain pending DB work (test flakiness).
        // (asExecutor() is not available in kotlinx-coroutines-test 1.10.2.)
        val inlineExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(inlineExecutor)
            .setQueryExecutor(inlineExecutor)
            .build()
        repo = ThemeRepository(db.themeDao(), db.photoDao(), ColorMatcher(), ColorNamer())
        storage = InternalPhotoStorage(ctx)
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test
    fun confirmCapture_createsThemeWhenNoMatch() = runTest(dispatcher) {
        val vm = CaptureViewModel(repo, ColorAnalyzer(), storage, ColorMatcher())
        vm.setPending(PendingCapture("/cap.jpg", "#DCA8A6", matchedTheme = null))
        vm.confirmCapture()
        advanceUntilIdle()
        assertEquals(1, db.themeDao().getAllThemes().first().size)
        assertNull(vm.uiState.value.pendingCapture)
    }

    @Test
    fun confirmCapture_joinsThemeWhenMatched() = runTest(dispatcher) {
        val seedId = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val matched = repo.findMatchingTheme("#DCB0A8")!!
        val vm = CaptureViewModel(repo, ColorAnalyzer(), storage, ColorMatcher())
        vm.setPending(PendingCapture("/cap.jpg", "#DCB0A8", matched))
        vm.confirmCapture()
        advanceUntilIdle()
        assertEquals(1, db.themeDao().getAllThemes().first().size)
        assertEquals(2, db.photoDao().getPhotosForThemeOnce(seedId).size)
    }

    @Test
    fun saveAsNewTheme_createsSeparateThemeEvenWhenMatched() = runTest(dispatcher) {
        repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val matched = repo.findMatchingTheme("#DCB0A8")!!
        val vm = CaptureViewModel(repo, ColorAnalyzer(), storage, ColorMatcher())
        vm.setPending(PendingCapture("/cap.jpg", "#DCB0A8", matched))
        vm.saveAsNewTheme()
        advanceUntilIdle()
        assertEquals(2, db.themeDao().getAllThemes().first().size)
        assertNull(vm.uiState.value.pendingCapture)
    }

    @Test
    fun onFrameAnalyzed_picksClosestTheme() = runTest(dispatcher) {
        repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val vm = CaptureViewModel(repo, ColorAnalyzer(), storage, ColorMatcher())
        advanceUntilIdle()  // drain init's themes collector before analyzing a frame
        val pixels = IntArray(100) { 0xFFDCA8A6.toInt() }  // exact hex match
        vm.onFrameAnalyzed(pixels, 10, 10)
        advanceUntilIdle()
        val target = vm.uiState.value.targetTheme
        // Theme name comes from ColorNamer.hash(#DCA8A6) — assert behaviour, not label.
        assertEquals(ColorNamer().nameColor("#DCA8A6"), target.name)
        assertTrue(target.matchPct >= 60)
        assertFalse(target.isFallback)
    }

    @Test
    fun onFrameAnalyzed_fallbackRoseGoldWhenNoMatch() = runTest(dispatcher) {
        repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val vm = CaptureViewModel(repo, ColorAnalyzer(), storage, ColorMatcher())
        advanceUntilIdle()  // drain init's themes collector before analyzing a frame
        val pixels = IntArray(100) { 0xFF00FF00.toInt() }  // 亮绿 vs Dusty Rose → 大 ΔE → fallback
        vm.onFrameAnalyzed(pixels, 10, 10)
        advanceUntilIdle()
        val target = vm.uiState.value.targetTheme
        assertTrue(target.isFallback)
        assertEquals("Rose Gold", target.name)
        assertEquals(0, target.matchPct)
    }
}
