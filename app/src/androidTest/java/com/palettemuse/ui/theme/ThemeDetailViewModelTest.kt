package com.palettemuse.ui.theme

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeFactory
import com.palettemuse.data.repository.ThemeMatcher
import com.palettemuse.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
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

@RunWith(AndroidJUnit4::class)
class ThemeDetailViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ThemeRepository
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val inlineExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(inlineExecutor)
            .setQueryExecutor(inlineExecutor)
            .build()
        repo = ThemeRepository(db.themeDao(), db.photoDao(), ColorNamer(), ThemeMatcher(ColorMatcher()), ThemeFactory())
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun loadsThemeWithPhotos() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        repo.savePhotoToTheme(id, "/cap1.jpg", "#C99A92")
        val vm = ThemeDetailViewModel(Routes.ThemeDetail(id), repo)
        advanceUntilIdle()
        val state = vm.uiState.first { !it.isLoading }
        assertEquals("#DCA8A6", state.data?.theme?.representativeHex)
        assertEquals(2, state.data?.photos?.size)
        // Seed photo should be surfaced as the Hero origin.
        assertTrue(state.data?.photos?.any { it.isSeed } == true)
    }

    @Test fun missingTheme_emitsNullData() = runTest(dispatcher) {
        val vm = ThemeDetailViewModel(Routes.ThemeDetail("nope"), repo)
        advanceUntilIdle()
        val state = vm.uiState.first { !it.isLoading }
        assertNull(state.data)
    }

    @Test fun renameTheme_updatesState() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val vm = ThemeDetailViewModel(Routes.ThemeDetail(id), repo)
        advanceUntilIdle()
        vm.renameTheme("新主题名")
        advanceUntilIdle()
        val state = vm.uiState.first { !it.isLoading }
        assertEquals("新主题名", state.data?.theme?.name)
    }

    @Test fun deleteTheme_clearsState() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val vm = ThemeDetailViewModel(Routes.ThemeDetail(id), repo)
        advanceUntilIdle()
        vm.deleteTheme()
        advanceUntilIdle()
        val state = vm.uiState.first { !it.isLoading }
        assertNull(state.data)
    }
}
