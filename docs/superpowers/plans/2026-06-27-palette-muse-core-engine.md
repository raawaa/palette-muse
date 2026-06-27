# Palette Muse 核心引擎 + 相机主链路 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构数据模型为「色系主题 + 照片」，打通「拍照 → 取色 → 自动归类 → 存库」闭环，并接通快门按钮和拍完确认条，让 MVP 的核心链路端到端可跑通。

**Architecture:** 新建 `ThemeEntity`/`PhotoEntity` 替代旧 `ProjectEntity`/`ColorPaletteEntity`（旧表本计划保留不删，待 Plan 2 清理，避免 Home/Analyze/Export 编译断裂）。新增 `ThemeRepository` 承载归类逻辑（找匹配主题 / 创建新主题 / 色板生成）。`CaptureViewModel.capturePhoto()` 重写为「取主色 → 找匹配 → 进入待确认态」，确认后才落库。相机快门接 `onClick`，拍完弹轻量确认条。

**Tech Stack:** Kotlin · Room · Hilt · CameraX · androidx.palette · Coroutines/Flow · Jetpack Compose · Navigation3

## Global Constraints

- minSdk 26, targetSdk/compileSdk 36, JVM 17, Kotlin（`app/build.gradle.kts` 已配置）
- 设计系统 Aura Aesthetic：主色 Rose Gold `#B76E79`（仅 UI 用，本计划触及 UI 少）
- 取色细粒度：复用 `ColorMatcher` 的 CIELAB ΔE；`matchPercentage >= 60` 判为同主题（`ThemeRepository.MATCH_THRESHOLD`）
- 数据库迁移：`fallbackToDestructiveMigration()`（原型无真实数据，可直接重建）
- 取色全自动、聚焦主体：MVP 用 `Palette.dominantSwatch`（面积最大色）；人像/前景分割为未来扩展点，本计划不实现
- 测试分层：数据层/算法用 `androidTest`（in-memory Room、Bitmap）；ViewModel 用 unit test（fake repository + coroutines-test）

---

## File Structure

| 文件 | 动作 | 职责 |
|------|------|------|
| `data/model/ThemeEntity.kt` | Create | 色系主题表（id, name, representativeHex, createdAt, updatedAt） |
| `data/model/PhotoEntity.kt` | Create | 照片表（themeId FK CASCADE, imagePath, dominantHex, isSeed, capturedAt） |
| `data/local/ThemeDao.kt` | Create | 主题 CRUD + Flow |
| `data/local/PhotoDao.kt` | Create | 照片 CRUD + count + cascade |
| `data/local/AppDatabase.kt` | Modify | 加 Theme/Photo entities + DAO，version 2，destructive migration |
| `data/repository/ThemeRepository.kt` | Create | 归类（findMatchingTheme）/ 存照 / 创建主题 / 色板生成 / CRUD |
| `data/repository/PhotoStorage.kt` | Create | `PhotoStorage` 接口 + `InternalPhotoStorage` 实现（bitmap→文件路径，便于测试） |
| `core/ColorAnalyzer.kt` | Modify | 加 `extractDominantHex(bitmap)` |
| `ui/capture/CaptureViewModel.kt` | Modify | 重写 capturePhoto：取色→归类→待确认；加 confirmCapture / saveAsNewTheme |
| `ui/capture/CaptureScreen.kt` | Modify | 快门接 `onClick` + 拍完确认条 Composable |
| `di/AppModule.kt` | Modify | provide ThemeDao/PhotoDao/ThemeRepository/PhotoStorage |

**旧文件保留不动**（Plan 2 删）：`ProjectEntity` / `ColorPaletteEntity` / `ProjectDao` / `ColorPaletteDao` / `ProjectRepository` / `MainScreen*` / `DataRepository`。

---

## Task 1: 新数据模型（ThemeEntity + PhotoEntity + DAO）

**Files:**
- Create: `app/src/main/java/com/palettemuse/data/model/ThemeEntity.kt`
- Create: `app/src/main/java/com/palettemuse/data/model/PhotoEntity.kt`
- Create: `app/src/main/java/com/palettemuse/data/local/ThemeDao.kt`
- Create: `app/src/main/java/com/palettemuse/data/local/PhotoDao.kt`
- Modify: `app/src/main/java/com/palettemuse/data/local/AppDatabase.kt`
- Test: `app/src/androidTest/java/com/palettemuse/data/ThemeDaoTest.kt`

**Interfaces:**
- Produces: `ThemeEntity(id, name, representativeHex, createdAt, updatedAt)` · `PhotoEntity(id, themeId, imagePath, dominantHex, isSeed, capturedAt)` · `ThemeDao.getAllThemes(): Flow<List<ThemeEntity>>` · `PhotoDao.getPhotosForThemeOnce(themeId): List<PhotoEntity>`

- [ ] **Step 1: 写失败测试 `ThemeDaoTest`**

Create `app/src/androidTest/java/com/palettemuse/data/ThemeDaoTest.kt`:
```kotlin
package com.palettemuse.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThemeDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun insertAndQueryTheme() = runTest {
        db.themeDao().insert(ThemeEntity(id = "t1", name = "Dusty Rose", representativeHex = "#DCA8A6"))
        val themes = db.themeDao().getAllThemes().first()
        assertEquals(1, themes.size)
        assertEquals("Dusty Rose", themes[0].name)
    }

    @Test
    fun cascadeDeleteRemovesPhotos() = runTest {
        db.themeDao().insert(ThemeEntity(id = "t1", name = "T", representativeHex = "#000000"))
        db.photoDao().insert(PhotoEntity(id = "p1", themeId = "t1", imagePath = "/x", dominantHex = "#000000"))
        db.themeDao().deleteById("t1")
        assertEquals(0, db.photoDao().getPhotosForThemeOnce("t1").size)
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.data.ThemeDaoTest`（需连接设备/模拟器）
Expected: 编译失败 — `ThemeEntity` / `PhotoEntity` / `themeDao()` / `photoDao()` 未定义。

- [ ] **Step 3: 创建 `ThemeEntity`**

Create `app/src/main/java/com/palettemuse/data/model/ThemeEntity.kt`:
```kotlin
package com.palettemuse.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val representativeHex: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 4: 创建 `PhotoEntity`**

Create `app/src/main/java/com/palettemuse/data/model/PhotoEntity.kt`:
```kotlin
package com.palettemuse.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(
        entity = ThemeEntity::class,
        parentColumns = ["id"],
        childColumns = ["themeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("themeId")]
)
data class PhotoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val themeId: String,
    val imagePath: String,
    val dominantHex: String,
    val isSeed: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 5: 创建 `ThemeDao`**

Create `app/src/main/java/com/palettemuse/data/local/ThemeDao.kt`:
```kotlin
package com.palettemuse.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.palettemuse.data.model.ThemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes ORDER BY updatedAt DESC")
    fun getAllThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun getTheme(id: String): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(theme: ThemeEntity)

    @Update
    suspend fun update(theme: ThemeEntity)

    @Query("DELETE FROM themes WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

- [ ] **Step 6: 创建 `PhotoDao`**

Create `app/src/main/java/com/palettemuse/data/local/PhotoDao.kt`:
```kotlin
package com.palettemuse.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.palettemuse.data.model.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE themeId = :themeId ORDER BY capturedAt DESC")
    fun observePhotosForTheme(themeId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE themeId = :themeId ORDER BY capturedAt DESC")
    suspend fun getPhotosForThemeOnce(themeId: String): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE themeId = :themeId")
    suspend fun deleteByThemeId(themeId: String)
}
```

- [ ] **Step 7: 更新 `AppDatabase`（加 entities + DAO，version 2）**

Replace `app/src/main/java/com/palettemuse/data/local/AppDatabase.kt`:
```kotlin
package com.palettemuse.data.local

import androidx.room.Database
import androidx.room.TypeConverters
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ProjectEntity
import com.palettemuse.data.model.ThemeEntity

@Database(
    entities = [
        ThemeEntity::class,
        PhotoEntity::class,
        ProjectEntity::class,
        ColorPaletteEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun themeDao(): ThemeDao
    abstract fun photoDao(): PhotoDao
    abstract fun projectDao(): ProjectDao
    abstract fun colorPaletteDao(): ColorPaletteDao
}
```

- [ ] **Step 8: 更新 `AppModule.provideDatabase` 加 destructive migration**

In `app/src/main/java/com/palettemuse/di/AppModule.kt`, 修改 `provideDatabase`:
```kotlin
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "palette_muse.db")
            .fallbackToDestructiveMigration()
            .build()
    }
```

- [ ] **Step 9: 运行测试，确认通过**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.data.ThemeDaoTest`
Expected: 2 tests PASS。

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/palettemuse/data/model/ThemeEntity.kt \
  app/src/main/java/com/palettemuse/data/model/PhotoEntity.kt \
  app/src/main/java/com/palettemuse/data/local/ThemeDao.kt \
  app/src/main/java/com/palettemuse/data/local/PhotoDao.kt \
  app/src/main/java/com/palettemuse/data/local/AppDatabase.kt \
  app/src/main/java/com/palettemuse/di/AppModule.kt \
  app/src/androidTest/java/com/palettemuse/data/ThemeDaoTest.kt
git commit -m "feat(data): add Theme/Photo entities with DAOs (core engine)"
```

---

## Task 2: ColorAnalyzer 提取单主色

**Files:**
- Modify: `app/src/main/java/com/palettemuse/core/ColorAnalyzer.kt`
- Test: `app/src/androidTest/java/com/palettemuse/core/ColorAnalyzerTest.kt`

**Interfaces:**
- Produces: `suspend fun ColorAnalyzer.extractDominantHex(bitmap: Bitmap): String`（返回 `#RRGGBB`，无显著色时 `#808080`）

- [ ] **Step 1: 写失败测试**

Create `app/src/androidTest/java/com/palettemuse/core/ColorAnalyzerTest.kt`:
```kotlin
package com.palettemuse.core

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorAnalyzerTest {
    private val analyzer = ColorAnalyzer()

    @Test
    fun extractDominantHexOfSolidColor() = runTest {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#DCA8A6"))
        }
        assertEquals("#DCA8A6", analyzer.extractDominantHex(bmp))
    }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.core.ColorAnalyzerTest`
Expected: 编译失败 — `extractDominantHex` 未定义。

- [ ] **Step 3: 加 `extractDominantHex`**

In `ColorAnalyzer.kt` 的 `@Singleton class ColorAnalyzer` 内加（保留现有 `analyze`/`analyzeToEntities` 不动，Plan 2 清理）:
```kotlin
    suspend fun extractDominantHex(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap)
            .maximumColorCount(12)
            .clearFilters()
            .resizeBitmapArea(96 * 96)
            .generate()
        palette.dominantSwatch?.rgb?.toHex() ?: "#808080"
    }
```

- [ ] **Step 4: 运行，确认通过**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.core.ColorAnalyzerTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/palettemuse/core/ColorAnalyzer.kt \
  app/src/androidTest/java/com/palettemuse/core/ColorAnalyzerTest.kt
git commit -m "feat(core): add extractDominantHex for single-color extraction"
```

---

## Task 3: ThemeRepository（归类 / 存照 / 色板 / CRUD）

**Files:**
- Create: `app/src/main/java/com/palettemuse/data/repository/ThemeRepository.kt`
- Test: `app/src/androidTest/java/com/palettemuse/data/ThemeRepositoryTest.kt`

**Interfaces:**
- Consumes: `ThemeDao`, `PhotoDao`, `ColorMatcher.matchPercentage(target, sample): Int`, `ColorNamer.nameColor(hex): String`
- Produces:
  - `suspend fun findMatchingTheme(hex: String): ThemeEntity?`
  - `suspend fun savePhotoToTheme(themeId, imagePath, dominantHex, isSeed=false)`
  - `suspend fun createThemeAndSave(imagePath, dominantHex): String`（返回新 themeId）
  - `fun getAllThemesWithPhotos(): Flow<List<ThemeWithPhotos>>`
  - `suspend fun renameTheme(id, name)` / `updateThemeColor(id, newHex)` / `deleteTheme(id)`

- [ ] **Step 1: 写失败测试**

Create `app/src/androidTest/java/com/palettemuse/data/ThemeRepositoryTest.kt`:
```kotlin
package com.palettemuse.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ThemeRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ThemeRepository(db.themeDao(), db.photoDao(), ColorMatcher(), ColorNamer())
    }

    @After
    fun close() = db.close()

    @Test
    fun createThemeAndSave_createsSeedPhoto() = runTest {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val themes = db.themeDao().getAllThemes().first()
        assertEquals(1, themes.size)
        assertEquals(id, themes[0].id)
        val photos = db.photoDao().getPhotosForThemeOnce(id)
        assertEquals(1, photos.size)
        assertTrue(photos[0].isSeed)
    }

    @Test
    fun findMatchingTheme_returnsMatchAboveThreshold() = runTest {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val matched = repo.findMatchingTheme("#DCB0A8") // 接近的枯玫瑰
        assertEquals(id, matched?.id)
    }

    @Test
    fun findMatchingTheme_returnsNullBelowThreshold() = runTest {
        repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val matched = repo.findMatchingTheme("#00FF00") // 差异大的亮绿
        assertNull(matched)
    }

    @Test
    fun savePhotoToTheme_appendsPhoto() = runTest {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        repo.savePhotoToTheme(id, "/cap1.jpg", "#DCA8A6")
        repo.savePhotoToTheme(id, "/cap2.jpg", "#D5A09A")
        assertEquals(3, db.photoDao().getPhotosForThemeOnce(id).size)
    }

    @Test
    fun getAllThemesWithPhotos_buildsPalette() = runTest {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        repo.savePhotoToTheme(id, "/cap.jpg", "#C99A92")
        val list = repo.getAllThemesWithPhotos().first()
        assertEquals(1, list.size)
        val palette = list[0].palette
        assertEquals("#DCA8A6", palette.first()) // 代表色居首
        assertTrue(palette.size in 1..3)
    }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.data.ThemeRepositoryTest`
Expected: 编译失败 — `ThemeRepository` / `ThemeWithPhotos` 未定义。

- [ ] **Step 3: 创建 `ThemeRepository`**

Create `app/src/main/java/com/palettemuse/data/repository/ThemeRepository.kt`:
```kotlin
package com.palettemuse.data.repository

import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.data.local.PhotoDao
import com.palettemuse.data.local.ThemeDao
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

data class ThemeWithPhotos(
    val theme: ThemeEntity,
    val photos: List<PhotoEntity>,
    val palette: List<String>
)

@Singleton
class ThemeRepository @Inject constructor(
    private val themeDao: ThemeDao,
    private val photoDao: PhotoDao,
    private val colorMatcher: ColorMatcher,
    private val colorNamer: ColorNamer
) {
    suspend fun findMatchingTheme(hex: String): ThemeEntity? {
        val themes = themeDao.getAllThemes().first()
        return themes
            .map { it to colorMatcher.matchPercentage(it.representativeHex, hex) }
            .filter { it.second >= MATCH_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
    }

    suspend fun savePhotoToTheme(
        themeId: String,
        imagePath: String,
        dominantHex: String,
        isSeed: Boolean = false
    ) {
        photoDao.insert(
            PhotoEntity(
                id = UUID.randomUUID().toString(),
                themeId = themeId,
                imagePath = imagePath,
                dominantHex = dominantHex,
                isSeed = isSeed
            )
        )
        touchTheme(themeId)
    }

    suspend fun createThemeAndSave(imagePath: String, dominantHex: String): String {
        val themeId = UUID.randomUUID().toString()
        themeDao.insert(
            ThemeEntity(
                id = themeId,
                name = colorNamer.nameColor(dominantHex),
                representativeHex = dominantHex
            )
        )
        photoDao.insert(
            PhotoEntity(
                id = UUID.randomUUID().toString(),
                themeId = themeId,
                imagePath = imagePath,
                dominantHex = dominantHex,
                isSeed = true
            )
        )
        return themeId
    }

    fun getAllThemesWithPhotos(): Flow<List<ThemeWithPhotos>> =
        themeDao.getAllThemes().mapLatest { themes ->
            themes.map { theme ->
                val photos = photoDao.getPhotosForThemeOnce(theme.id)
                ThemeWithPhotos(theme, photos, buildPalette(theme, photos))
            }
        }

    suspend fun renameTheme(id: String, name: String) {
        themeDao.getTheme(id)?.let { themeDao.update(it.copy(name = name, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun updateThemeColor(id: String, newHex: String) {
        themeDao.getTheme(id)?.let {
            themeDao.update(it.copy(representativeHex = newHex, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteTheme(id: String) = themeDao.deleteById(id)

    private suspend fun touchTheme(themeId: String) {
        themeDao.getTheme(themeId)?.let {
            themeDao.update(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private fun buildPalette(theme: ThemeEntity, photos: List<PhotoEntity>): List<String> {
        val distinct = photos.map { it.dominantHex }
            .distinct()
            .filter { it != theme.representativeHex }
        return listOf(theme.representativeHex) + distinct.take(2)
    }

    companion object {
        const val MATCH_THRESHOLD = 60
    }
}
```

- [ ] **Step 4: 运行，确认通过**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.data.ThemeRepositoryTest`
Expected: 5 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/palettemuse/data/repository/ThemeRepository.kt \
  app/src/androidTest/java/com/palettemuse/data/ThemeRepositoryTest.kt
git commit -m "feat(data): add ThemeRepository with classify/palette logic"
```

---

## Task 4: PhotoStorage 接口（bitmap→文件路径，便于测试）

**Files:**
- Create: `app/src/main/java/com/palettemuse/data/repository/PhotoStorage.kt`
- Modify: `app/src/main/java/com/palettemuse/di/AppModule.kt`

**Interfaces:**
- Produces: `interface PhotoStorage { suspend fun save(bitmap: Bitmap): String }` · DI 提供 `InternalPhotoStorage`

- [ ] **Step 1: 创建 `PhotoStorage` 接口与实现**

Create `app/src/main/java/com/palettemuse/data/repository/PhotoStorage.kt`:
```kotlin
package com.palettemuse.data.repository

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

interface PhotoStorage {
    suspend fun save(bitmap: Bitmap): String
}

@Singleton
class InternalPhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : PhotoStorage {
    override suspend fun save(bitmap: Bitmap): String {
        val dir = File(context.filesDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        return file.absolutePath
    }
}
```

- [ ] **Step 2: 在 `AppModule` 提供 PhotoStorage + ThemeRepository + DAO**

In `app/src/main/java/com/palettemuse/di/AppModule.kt`，在 `AppModule` object 内加（保留现有 provide 不删）:
```kotlin
    @Provides
    fun provideThemeDao(database: AppDatabase): ThemeDao = database.themeDao()

    @Provides
    fun providePhotoDao(database: AppDatabase): PhotoDao = database.photoDao()

    @Provides @Singleton
    fun provideThemeRepository(
        themeDao: ThemeDao,
        photoDao: PhotoDao,
        colorMatcher: ColorMatcher,
        colorNamer: ColorNamer
    ): ThemeRepository = ThemeRepository(themeDao, photoDao, colorMatcher, colorNamer)
```
`InternalPhotoStorage` 用 `@Inject constructor`，Hilt 自动提供，无需 `@Provides`。

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/palettemuse/data/repository/PhotoStorage.kt \
  app/src/main/java/com/palettemuse/di/AppModule.kt
git commit -m "feat(di): provide PhotoStorage, ThemeDao/PhotoDao, ThemeRepository"
```

---

## Task 5: CaptureViewModel 重写（取色 → 归类 → 待确认）

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt`
- Test: `app/src/test/java/com/palettemuse/ui/capture/CaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `ThemeRepository.findMatchingTheme/savePhotoToTheme/createThemeAndSave` · `ColorAnalyzer.extractDominantHex` · `PhotoStorage.save` · `ColorMatcher`（onFrameAnalyzed 用，保留）
- Produces: `CaptureUiState.pendingCapture: PendingCapture?` · `capturePhoto(bitmap)` · `confirmCapture()` · `saveAsNewTheme()`

- [ ] **Step 1: 写失败测试**

Create `app/src/androidTest/java/com/palettemuse/ui/capture/CaptureViewModelTest.kt`:
```kotlin
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
import org.junit.Assert.assertNull
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
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
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
}
```
> 注：测试用 in-memory 真实 `ThemeRepository` + `setPending()` 入口（见 Step 3），稳定覆盖 `confirmCapture`/`saveAsNewTheme` 落库分支，避开 `extractDominantHex` 切 `Dispatchers.Default` 的调度不确定性。`capturePhoto` 本身靠 Task 7 相机冒烟验证。

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.capture.CaptureViewModelTest`
Expected: 编译失败 — `CaptureViewModel` 签名变了、`setPending` 未定义。

- [ ] **Step 3: 重写 `CaptureViewModel`**

Replace `app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt`:
```kotlin
package com.palettemuse.ui.capture

import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.data.repository.PhotoStorage
import com.palettemuse.data.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CapturedSwatch(
    val hexColor: String,
    val semanticName: String,
    val matchPercentage: Int
)

data class PendingCapture(
    val imagePath: String,
    val dominantHex: String,
    val matchedTheme: ThemeEntity?
)

data class CaptureUiState(
    val matchPercentage: Int = 0,
    val capturedSwatches: List<CapturedSwatch> = emptyList(),
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isAnalyzing: Boolean = false,
    val pendingCapture: PendingCapture? = null
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val colorAnalyzer: ColorAnalyzer,
    private val photoStorage: PhotoStorage,
    private val colorMatcher: ColorMatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
        val sampleHex = colorMatcher.extractCenterAverageColor(pixels, width, height)
        _uiState.value = _uiState.value.copy(
            matchPercentage = colorMatcher.matchPercentage("#B76E79", sampleHex)
        )
    }

    fun capturePhoto(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val imagePath = photoStorage.save(bitmap)
            val dominantHex = colorAnalyzer.extractDominantHex(bitmap)
            val matched = themeRepository.findMatchingTheme(dominantHex)
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                pendingCapture = PendingCapture(imagePath, dominantHex, matched)
            )
        }
    }

    fun confirmCapture() {
        val pending = _uiState.value.pendingCapture ?: return
        viewModelScope.launch {
            val matched = pending.matchedTheme
            if (matched != null) {
                themeRepository.savePhotoToTheme(matched.id, pending.imagePath, pending.dominantHex)
            } else {
                themeRepository.createThemeAndSave(pending.imagePath, pending.dominantHex)
            }
            _uiState.value = _uiState.value.copy(pendingCapture = null)
        }
    }

    fun saveAsNewTheme() {
        val pending = _uiState.value.pendingCapture ?: return
        viewModelScope.launch {
            themeRepository.createThemeAndSave(pending.imagePath, pending.dominantHex)
            _uiState.value = _uiState.value.copy(pendingCapture = null)
        }
    }

    fun flipCamera() {
        _uiState.value = _uiState.value.copy(
            lensFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }

    fun dismissPending() {
        _uiState.value = _uiState.value.copy(pendingCapture = null)
    }
}
```

**测试接缝**：在 `CaptureViewModel` 内 `dismissPending()` 之后，加一个 `@VisibleForTesting` 入口供测试注入待确认态：
```kotlin
    @androidx.annotation.VisibleForTesting
    internal fun setPending(pending: PendingCapture) {
        _uiState.value = _uiState.value.copy(pendingCapture = pending)
    }
```
> 说明：`capturePhoto(bitmap)` 内部 `extractDominantHex` 切到 `Dispatchers.Default`，单测调度不稳定；故测试用 `setPending()` 直接注入 pending，只验证 `confirmCapture`/`saveAsNewTheme` 落库分支（androidTest + in-memory 真实 repo）。`capturePhoto` 的取色归类靠 Task 7 相机冒烟验证。

- [ ] **Step 4: 运行，确认通过**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.capture.CaptureViewModelTest`
Expected: 3 tests PASS（confirm/save 各分支）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt \
  app/src/androidTest/java/com/palettemuse/ui/capture/CaptureViewModelTest.kt
git commit -m "feat(capture): rewrite CaptureViewModel with classify→confirm flow"
```

---

## Task 6: CaptureScreen 快门接 onClick + 拍完确认条

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt`（快门在 326-347 行）

**Interfaces:**
- Consumes: `CaptureViewModel.capturePhoto(bitmap)` · `confirmCapture()` · `saveAsNewTheme()` · `dismissPending()` · `uiState.pendingCapture`
- Produces: 可点击快门 + `CaptureConfirmSheet` Composable

- [ ] **Step 1: 给快门 Box 加 `clickable`，调用 `takePhoto`**

在 `CaptureScreen.kt` 找到快门按钮（约 325-347 行，外层 `Box(Modifier.size(80.dp)...)`），把外层 Box 的 modifier 链加上 `.clickable { cameraManager.takePhoto(context, onPhotoTaken = { bmp -> viewModel.capturePhoto(bmp) }, onError = {}) }`。

替换该 Box 为：
```kotlin
Box(
    modifier = Modifier
        .size(80.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.2f))
        .padding(8.dp)
        .clickable {
            cameraManager.takePhoto(
                context,
                onPhotoTaken = { bmp -> viewModel.capturePhoto(bmp) },
                onError = { /* TODO Plan 2: 错误提示 */ }
            )
        },
    contentAlignment = Alignment.Center
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFA6606B), Color(0xFFFFB2BC))
                )
            )
    )
}
```
确保 `import androidx.compose.foundation.clickable`、`cameraManager` 与 `context` 在作用域内（CameraPreview 已有 cameraManager；context 用 `LocalContext.current`）。

- [ ] **Step 2: 新增 `CaptureConfirmSheet` Composable**

在 `CaptureScreen.kt` 文件末尾加：
```kotlin
@Composable
fun CaptureConfirmSheet(
    pending: PendingCapture,
    onConfirm: () -> Unit,
    onSaveAsNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val themeName = pending.matchedTheme?.name ?: "新主题"
    val action = if (pending.matchedTheme != null) "归入【$themeName】？" else "为这个颜色创建新主题？"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFCF9F8).copy(alpha = 0.9f))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(action, color = Color(0xFF8A4853), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A4853))
                ) { Text("确认", color = Color.White) }
                OutlinedButton(onClick = onSaveAsNew, modifier = Modifier.weight(1f)) { Text("另起新主题") }
            }
        }
    }
}
```
（imports 按需补：`Column`, `Text`, `Spacer`, `Row`, `Button`, `OutlinedButton`, `ButtonDefaults`, `height`, `sp`, `FontWeight`。）

- [ ] **Step 3: 在 CaptureScreen 主体显示确认条**

在 `CaptureScreen` 的根布局内（Scaffold/Box 底部），加：
```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
state.pendingCapture?.let { pending ->
    Box(Modifier.align(Alignment.BottomCenter)) {
        CaptureConfirmSheet(
            pending = pending,
            onConfirm = viewModel::confirmCapture,
            onSaveAsNew = viewModel::saveAsNewTheme,
            onDismiss = viewModel::dismissPending
        )
    }
}
```
（`collectAsStateWithLifecycle` 需 `androidx.lifecycle:lifecycle-runtime-compose`，已在依赖里。）

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 手动/集成验证（需设备）**

Run app → 进入相机 → 对准物体按快门 → 确认条出现 → 点「确认」→ 确认条消失。用 Layout Inspector 或日志确认 `pendingCapture` 流转正确。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt
git commit -m "feat(capture): wire shutter onClick + post-capture confirm sheet"
```

---

## Task 7: 全链路冒烟 + 清理旧 capturePhoto 死代码

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt`（移除旧的 `saveImageToInternalStorage`、旧 `onSaved` 回调）
- Verify: 整体编译 + connectedAndroidTest 全绿

- [ ] **Step 1: 移除旧死代码**

在 `CaptureViewModel.kt` 删除：旧的 `saveImageToInternalStorage()` 私有方法（已被 `PhotoStorage` 替代）、旧 `capturePhoto` 签名里的 `onSaved: (String) -> Unit` 参数（如 NavGraph 调用处引用了 `onSaved`，改为确认后不自动导航——留在相机页继续拍）。

- [ ] **Step 2: 检查 NavGraph 对 CaptureScreen 的调用**

在 `ui/navigation/NavGraph.kt` 的 `entry<Routes.Capture>` 块，`CaptureScreen` 的 `onNavigateToAnalyze` 参数现在不会被自动触发（确认后留在相机页）。保持参数签名不变（Plan 2 再决定确认后是否导航到主题详情）。

- [ ] **Step 3: 跑全部 androidTest + unit test**

Run: `./gradlew :app:connectedAndroidTest :app:testDebugUnitTest`
Expected: 全部 PASS（旧的 `MainScreenViewModelTest` 模板测试可能因数据模型变化失败——若失败，本任务内将其标记 `@Ignore` 或删除，Plan 2 统一清理）。

- [ ] **Step 4: 手动端到端冒烟**

Run app → 拍第一张（穿搭）→ 确认条显示「创建新主题」→ 确认 → 拍第二张同色 → 确认条显示「归入【Dusty Rose】」→ 确认。用 Database Inspector 查 `themes` 和 `photos` 表，确认数据正确写入。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore(capture): remove dead capture code, pass full smoke test"
```

---

## Self-Review 结果

**1. Spec coverage（对照 brief v2 第 7 节交互规则）：**
- 取色全自动 → Task 2 `extractDominantHex` ✓
- 色系判定细粒度（色相+明度+饱和度）→ 复用 `ColorMatcher` CIELAB ΔE + `MATCH_THRESHOLD=60` ✓
- 归并交互（拍完轻确认）→ Task 5 `pendingCapture` + Task 6 `CaptureConfirmSheet` ✓
- 主题绑定色系、长期生长 → Task 1 Theme/Photo 模型 + Task 3 `savePhotoToTheme` 追加 ✓
- 事后可改主题色 → Task 3 `updateThemeColor` ✓（重新归类留 Plan 2）
- 穿搭/生活不区分（路线A）→ Task 3 `findMatchingTheme`/`createThemeAndSave` 不区分内容类型 ✓
- 主题色系=代表色+色板 → Task 3 `buildPalette` + `ThemeWithPhotos.palette` ✓
- 人像检测扩展点 → 架构上 `extractDominantHex` 是单点，未来可在其前插入分割器；本计划不实现（已在 Global Constraints 标注）✓

**2. Placeholder scan：** Task 6 Step 1 的 `onError = { /* TODO Plan 2 */ }` 是显式跨计划标记，非占位符；其余步骤均有完整代码。✓

**3. Type consistency：** `ThemeEntity.id: String`、`PhotoEntity.themeId: String`、`findMatchingTheme(hex: String): ThemeEntity?`、`savePhotoToTheme(themeId, imagePath, dominantHex, isSeed)`、`createThemeAndSave(imagePath, dominantHex): String`、`ThemeWithPhotos(theme, photos, palette)` 在 Task 1/3/5 间一致。✓

**已知简化（留 Plan 2）：** HomeScreen/AnalyzeScreen/ExportScreen 仍用旧 `ProjectRepository`（编译通过但功能断开，首页会空）；旧 `ProjectEntity`/`ColorPaletteEntity`/`MainScreen*` 未删除；`onFrameAnalyzed` 的 TARGET 仍硬编码 `#B76E79`（实时匹配优化留 Plan 2）；确认后不自动导航到主题详情。
