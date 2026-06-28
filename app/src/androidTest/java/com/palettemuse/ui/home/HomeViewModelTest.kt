package com.palettemuse.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ThemeRepository
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Run Room queries inline so suspend DAO calls complete within the
        // test coroutine's virtual clock (same harness as CaptureViewModelTest).
        val inlineExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(inlineExecutor)
            .setQueryExecutor(inlineExecutor)
            .build()
        repo = ThemeRepository(db.themeDao(), db.photoDao(), ColorNamer(), ThemeMatcher(ColorMatcher()))
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun emptyDb_showsEmptyThemes() = runTest(dispatcher) {
        val vm = HomeViewModel(repo)
        advanceUntilIdle()
        val state = vm.uiState.first()
        assertTrue(state.themes.isEmpty())
    }

    @Test fun themesLoaded_intoState() = runTest(dispatcher) {
        repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val vm = HomeViewModel(repo)
        advanceUntilIdle()
        val state = vm.uiState.first()
        assertEquals(1, state.themes.size)
        assertEquals("#DCA8A6", state.themes[0].theme.representativeHex)
    }
}
