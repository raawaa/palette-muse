# Palette Muse UI 各屏适配 实现计划（Plan 2）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 UI 接到 Plan 1 的新引擎（Theme/Photo），按 Stitch 设计稿实现首页（主题画廊）、主题详情、导出（合成海报），清理旧模板残留，完成 MVP 的 UI 层。

**Architecture:** `HomeViewModel` 改读 `ThemeRepository.getAllThemesWithPhotos()`；新建 `ThemeDetailScreen`（替代 `AnalyzeScreen`）；`ExportScreen` 适配新模型 + `PosterRenderer` 合成海报；`NavGraph` 更新路由；清理旧 `Project`/`ColorPalette` 体系（AppDatabase v3 destructive）。**UI 视觉按 Stitch 设计稿还原**：每个 UI 任务注明 screen ID，实现者用 `mcp__stitch__get_screen`（项目 `1601910886256765316`）取 HTML 作为视觉 source of truth。

**Tech Stack:** Kotlin · Jetpack Compose Material3 · Navigation3 · Hilt · Coil（图片加载）· Room · androidx.palette

## Global Constraints

- minSdk 26, compileSdk 36, JVM 17, Kotlin
- 设计系统 Aura Aesthetic（`DESIGN.md` + `docs/stitch-ui-brief.md`）：Rose Gold `#B76E79`、Pearl White `#FDFBF7`、Playfair Display 标题、Plus Jakarta Sans 正文、玻璃质感、超软圆角、禁用黑色阴影
- **复用 Plan 1 的引擎，不改**：`ThemeRepository` / `ThemeDao` / `PhotoDao` / `ColorAnalyzer` / `ColorMatcher` / `ColorNamer` / `PosterRenderer` / `CaptureViewModel` / `CaptureScreen`
- 图片加载用 **Coil**（`coil.compose`，已在依赖）：`AsyncImage(model = photo.imagePath, ...)`
- 删旧表用 **destructive migration**（version 3，原型无真实数据）
- **UI 视觉还原**：实现者用 `mcp__stitch__get_screen`（`name: projects/1601910886256765316/screens/<id>`）取设计稿 HTML，按其布局/组件/文案/Aura 样式实现 Composable
- 测试：数据/ViewModel 用 unit/androidTest；UI 关键状态用 Compose UI 测试（`createAndroidComposeRule`）；视觉还原度用 `android` CLI（`screen capture` + `layout`）冒烟
- 命令：androidTest 用 `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQN>`（AGP 不支持 `--tests`）

## Stitch 设计稿 screen ID（实现时 get_screen 取 HTML）

| 屏幕 | screen ID |
|------|-----------|
| 首页（主题画廊） | `c19a8027cb134e4aab451b7535c21ca7` |
| 主题详情 | `d973641053bd4c7580d0e83a9477bbe5` |
| 海报导出 | `4202dc1622fd44369a5f0e0c8952a8d4` |
| 相机捕捉（Plan 1 已实现，Plan 2 仅微调） | `de6a201506554ba7a62eac041eda032a` |

---

## File Structure

| 文件 | 动作 | 职责 |
|------|------|------|
| `data/repository/ThemeRepository.kt` | Modify | 加 `getThemeWithPhotos(id): ThemeWithPhotos?` |
| `ui/home/HomeViewModel.kt` | Modify | 改读 `ThemeRepository.getAllThemesWithPhotos()` |
| `ui/home/HomeScreen.kt` | Modify | 按首页设计稿重写（主题画廊大图卡 + 真实色板 + 空状态 + FAB） |
| `ui/theme/ThemeDetailViewModel.kt` | Create | 读单主题 + photos + palette |
| `ui/theme/ThemeDetailScreen.kt` | Create | 主题详情（色板头 + 起点Hero + 瀑布流 + 导出FAB） |
| `ui/analyze/AnalyzeScreen.kt` + `AnalyzeViewModel.kt` | Delete | 被 ThemeDetail 替代 |
| `ui/export/ExportViewModel.kt` | Modify | 适配 Theme + Photos（合成海报数据源） |
| `ui/export/ExportScreen.kt` | Modify | 3:4 合成海报 + 模板选择 + 分享/保存 |
| `ui/navigation/NavGraph.kt` | Modify | `Routes.Analyze` → `Routes.ThemeDetail` |
| `data/model/ProjectEntity.kt` / `ColorPaletteEntity.kt` | Delete | 旧体系 |
| `data/local/ProjectDao.kt` / `ColorPaletteDao.kt` | Delete | 旧体系 |
| `data/repository/ProjectRepository.kt` | Delete | 旧体系 |
| `ui/main/MainScreen.kt` / `MainScreenViewModel.kt` / `data/DataRepository.kt` | Delete | 模板残留 |
| `data/local/AppDatabase.kt` | Modify | 去 ProjectEntity/ColorPaletteEntity，version 3 |
| `data/local/Converters.kt` | Modify | 去 ProjectType/ColorRole converter |
| `di/AppModule.kt` | Modify | 去 provideProjectDao/ColorPaletteDao/ProjectRepository |
| `data/local/PhotoDao.kt` | Modify | 排序加 `id` 次键（triage） |
| `di/AppModule.kt` (provideDatabase) | Modify | `fallbackToDestructiveMigration(dropAllTables = true)` |

---

## Task 1: HomeViewModel + HomeScreen（主题画廊）

**Files:**
- Modify: `ui/home/HomeViewModel.kt`、`ui/home/HomeScreen.kt`
- Test: `app/src/androidTest/java/com/palettemuse/ui/home/HomeViewModelTest.kt`

**设计稿:** `c19a8027cb134e4aab451b7535c21ca7`（get_screen 取 HTML，按其布局实现：TopAppBar「PALETTE MUSE」+ Hero「探索你的色彩」+ 主题大图卡列表 + 空状态「开启新收藏」+ 底部导航 工作室/FAB/我的）

**Interfaces:**
- Consumes: `ThemeRepository.getAllThemesWithPhotos(): Flow<List<ThemeWithPhotos>>`（Plan 1 已有，`ThemeWithPhotos(theme, photos, palette: List<String>)`）
- Produces: `HomeUiState(themes: List<ThemeWithPhotos>, isLoading)`；`HomeScreen` 每张卡用 `theme.representativeHex` + `theme.palette` 显示真实色板（不再硬编码 `#F5F2EB` 等）

- [ ] **Step 1: 写 HomeViewModelTest（androidTest，in-memory repo）**

Create `app/src/androidTest/java/com/palettemuse/ui/home/HomeViewModelTest.kt`:
```kotlin
package com.palettemuse.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.repository.ThemeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ThemeRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ThemeRepository(db.themeDao(), db.photoDao(), ColorMatcher(), ColorNamer())
    }
    @After fun close() = db.close()

    @Test fun emptyDb_showsEmptyThemes() = runTest {
        val vm = HomeViewModel(repo)
        val state = vm.uiState.first()
        assertTrue(state.themes.isEmpty())
    }

    @Test fun themesLoaded_intoState() = runTest {
        repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val vm = HomeViewModel(repo)
        val state = vm.uiState.first()
        assertEquals(1, state.themes.size)
        assertEquals("#DCA8A6", state.themes[0].theme.representativeHex)
    }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.home.HomeViewModelTest`
Expected: 编译失败 — `HomeViewModel(repo)` 签名变了（当前注入 ProjectRepository）。

- [ ] **Step 3: 重写 `HomeViewModel`（改注入 ThemeRepository）**

Replace `ui/home/HomeViewModel.kt`:
```kotlin
package com.palettemuse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.data.repository.ThemeRepository
import com.palettemuse.data.repository.ThemeWithPhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        MutableStateFlow(HomeUiState()).let { ms ->
            themeRepository.getAllThemesWithPhotos()
                .map { themes -> HomeUiState(themes = themes, isLoading = false) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())
        }

    fun deleteTheme(themeId: String) {
        viewModelScope.launch { themeRepository.deleteTheme(themeId) }
    }
}
```
（需 `import kotlinx.coroutines.flow.map`。）

- [ ] **Step 4: 重写 `HomeScreen`（主题画廊，按设计稿 c19a8027）**

`mcp__stitch__get_screen`（`name: projects/1601910886256765316/screens/c19a8027cb134e4aab451b7535c21ca7`）取 HTML。按其结构重写 `ui/home/HomeScreen.kt`：
- **TopAppBar**：`PALETTE MUSE`（Playfair Display, tracking 0.2em, Rose Gold）+ menu 图标（**去掉 search**——brief 决策）
- **Hero**：「探索你的色彩」标题 + 副文案（文案改贴合「色彩灵感收集」）
- **主题列表** `LazyColumn`（单列大图卡，按设计稿的 `grid grid-cols-1`）：
  - 每张卡：`Box`（高 256dp，`RoundedCornerShape(28.dp)`，`soft-shadow`）含
    - `AsyncImage`（Coil，`model = photos.firstOrNull()?.imagePath ?: ""`，`contentScale = Crop`）作封面
    - 底部 `Box`（`Modifier.background(Brush.verticalGradient(from=surface/90 to=transparent))`）
    - 信息条：色点（`theme.representativeHex` 圆点 + `theme.palette` 其余色点）+ 主题名 `theme.name` + 「`${photos.size} 捕捉 • ${相对时间}`」+ 右侧 `arrow_forward` 圆钮（点击导航详情）
    - **色点用真实 `theme.palette`**（替代写死的 `#F5F2EB/#D1BCAE/#8C7A72`）
  - 点击卡 → `onNavigateToDetail(theme.id)`
- **空状态卡**（`themes.isEmpty()`）：虚线边框 + 「开启新收藏」+「拍下你的穿搭，创建首个色彩主题」+ 点击触发 FAB
- **底部导航**：工作室（grid_view, 激活 Rose Gold）+ 中间 FAB（`add_a_photo`，渐变凸起，点击 `onCapture`）+ 我的（person）
- 保留现有 `onNavigateToCapture` / `onNavigateToAnalyze` 签名（签名 Task 4 统一改 `onNavigateToDetail`）

> Aura 样式（DESIGN.md）：圆角 24–32dp、玻璃质感 `Color.White.copy(alpha=0.6f)` + blur 概念、阴影用 `primary` 色低透明度（非黑）、Playfair 标题 / Jakarta Sans 正文。

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 运行 ViewModel 测试 + android CLI 冒烟**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.home.HomeViewModelTest`
冒烟（需先 Task 5 删旧表后端到端跑，或本任务用已存主题验证）：装 app → 建一个主题 → `android screen capture` + `android layout` 确认首页显示卡 + 真实色板。

- [ ] **Step 7: Commit**

```bash
git add ui/home/ app/src/androidTest/java/com/palettemuse/ui/home/HomeViewModelTest.kt
git commit -m "feat(home): rewrite HomeScreen as theme gallery with real palettes"
```

---

## Task 2: ThemeDetailScreen + ViewModel（替代 AnalyzeScreen）

**Files:**
- Create: `ui/theme/ThemeDetailViewModel.kt`、`ui/theme/ThemeDetailScreen.kt`
- Test: `app/src/androidTest/java/com/palettemuse/ui/theme/ThemeDetailViewModelTest.kt`
- 依赖：Task 1 加的 `ThemeRepository.getThemeWithPhotos(id)`

**设计稿:** `d973641053bd4c7580d0e83a9477bbe5`（色板头 3 色点 + 主题名 + 「N 次捕捉」+ Origin Post Hero「主题起点」+ 瀑布流 masonry 2 列 + 右下导出 FAB）

- [ ] **Step 1: ThemeRepository 加 `getThemeWithPhotos`**

In `ThemeRepository.kt` 加：
```kotlin
suspend fun getThemeWithPhotos(id: String): ThemeWithPhotos? {
    val theme = themeDao.getTheme(id) ?: return null
    val photos = photoDao.getPhotosForThemeOnce(id)
    return ThemeWithPhotos(theme, photos, buildPalette(theme, photos))
}
```

- [ ] **Step 2: 写 ThemeDetailViewModelTest（androidTest）**

验证：传入 themeId → 加载 ThemeWithPhotos（theme + photos + palette）。
```kotlin
@Test fun loadsThemeWithPhotos() = runTest {
    val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
    repo.savePhotoToTheme(id, "/cap1.jpg", "#C99A92")
    val vm = ThemeDetailViewModel(SavedStateHandle(mapOf("themeId" to id)), repo)
    val state = vm.uiState.first { !it.isLoading }
    assertEquals("#DCA8A6", state.data?.theme?.representativeHex)
    assertEquals(2, state.data?.photos?.size)
}
```
（`SavedStateHandle` from `androidx.lifecycle`，`ThemeDetailUiState(data: ThemeWithPhotos?, isLoading, error)`）

- [ ] **Step 3: 实现 `ThemeDetailViewModel`**（SavedStateHandle 取 `themeId`，调 `repo.getThemeWithPhotos`，暴露 StateFlow）

- [ ] **Step 4: 实现 `ThemeDetailScreen`（按设计稿 d9736410）**

`get_screen` 取 HTML，按结构实现：
- TopAppBar：`arrow_back` + `PALETTE MUSE` + `more_horiz`（菜单：重命名 / 改主题色 / 删除——调 VM 对应方法）
- 头部：色板（`theme.palette` 横排色点）+ 主题名 `theme.name`（Playfair）+ 「`${photos.size} 次捕捉 • ${时间}`」
- **主题起点 Hero**：找 `photos.firstOrNull { it.isSeed }`（或 photos.first()），`AsyncImage` 全宽大图（高 ~530dp），左上角玻璃 pill「主题起点」（star 图标）
- **瀑布流** `LazyVerticalStaggeredGrid`（2 列，`StaggeredCells.Adaptive`）：其余 photos，每张 `AsyncImage` + 圆角；MVP 先不做 hover 标签（细节/配饰等，推 Plan 3）
- **导出 FAB**：右下玻璃 pill「导出海报」+ `ios_share`，点击 `onNavigateToExport(themeId)`

- [ ] **Step 5: 测试 + 冒烟**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.theme.ThemeDetailViewModelTest`
冒烟：进详情页 → `android layout` 确认色板头 + Hero + 瀑布流。

- [ ] **Step 6: Commit**

```bash
git add ui/theme/ data/repository/ThemeRepository.kt app/src/androidTest/java/com/palettemuse/ui/theme/
git commit -m "feat(theme): add ThemeDetailScreen replacing AnalyzeScreen"
```

---

## Task 3: ExportScreen + ViewModel（合成海报）

**Files:**
- Modify: `ui/export/ExportViewModel.kt`、`ui/export/ExportScreen.kt`
- Test: `app/src/androidTest/java/com/palettemuse/ui/export/ExportViewModelTest.kt`

**设计稿:** `4202dc1622fd44369a5f0e0c8952a8d4`（3:4 海报预览：Bento 2×2 拼贴 + 标题 + 色板 tag + 模板选择 网格/胶片/日记/极简 + 分享小红书 / 保存相册）

**Interfaces:**
- Consumes: `ThemeRepository.getThemeWithPhotos(themeId)`、`PosterRenderer.render(photo, config)` / `saveToGallery`
- Produces: 海报预览 Bitmap + 模板选择状态 + 分享/保存动作

- [ ] **Step 1: 写 ExportViewModelTest**（验证：传入 themeId → 加载主题 → 选图 → 生成海报 Bitmap）

- [ ] **Step 2: 重写 `ExportViewModel`**（改注入 `ThemeRepository`，从 `themeId` 取 `ThemeWithPhotos`，选 N 张 photo（默认 4 张凑 Bento，或全部），调 `PosterRenderer.render` 生成预览；`sharePoster` 发 Intent.ACTION_SEND；`savePoster` 调 `saveToGallery`）
  - 海报标题用 `theme.name`；色板用 `theme.palette`
  - 模板：MVP 先做「网格」（其余模板 chip 显示但点击提示「即将推出」或直接只有网格可选）

- [ ] **Step 3: 重写 `ExportScreen`（按设计稿 4202dc16）**

`get_screen` 取 HTML：
- TopAppBar：`close` + `PALETTE MUSE` + `tune`
- **海报预览** `Box`（`aspectRatio(3f/4f)`，白底，圆角，软阴影）：Bento 2×2 拼贴（4 张 `AsyncImage` 或合成 Bitmap 显示）+ 底部标题 `theme.name` + 色板 tag（`theme.palette` 色点）
- **模板选择** 横滑 chip 行：网格（激活）/ 胶片 / 日记 / 极简
- **底部操作**（玻璃面板）：「分享到小红书」（Rose Gold 渐变主钮）+ 「保存到相册」（outline，download 图标）

- [ ] **Step 4: 测试 + 冒烟**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.export.ExportViewModelTest`
冒烟：进导出页 → `android screen capture` 确认海报预览 + chip + 按钮；点「保存到相册」→ 查 MediaStore 有图。

- [ ] **Step 5: Commit**

```bash
git add ui/export/ app/src/androidTest/java/com/palettemuse/ui/export/
git commit -m "feat(export): adapt ExportScreen to Theme/Photos with poster composer"
```

---

## Task 4: NavGraph + 相机确认后导航

**Files:**
- Modify: `ui/navigation/NavGraph.kt`、`ui/capture/CaptureScreen.kt`（可选：确认后导航）

- [ ] **Step 1: NavGraph 路由改名**

`Routes.Analyze(projectId)` → `Routes.ThemeDetail(themeId)`。`entry<Routes.Analyze>` → `entry<Routes.ThemeDetail>`，渲染 `ThemeDetailScreen`。首页卡的 `onNavigateToDetail` → `backStack.add(Routes.ThemeDetail(id))`。详情的 `onNavigateToExport` → `Routes.Export(themeId)`。

- [ ] **Step 2: 相机确认后导航到详情（可选增强）**

`CaptureScreen` 的 `confirmCapture` 成功后，可导航到 `ThemeDetail(newThemeId)`（让用户立即看到归类结果）。或保持 brief「留在相机继续拍」。**默认：留在相机**（brief），导航作为可选。

- [ ] **Step 3: 编译 + 全链路冒烟**

Run: `./gradlew :app:compileDebugKotlin`
冒烟：首页点主题卡 → 进详情 → 点导出 → 进导出页。`android layout` 确认各页。

- [ ] **Step 4: Commit**

```bash
git add ui/navigation/ ui/capture/CaptureScreen.kt
git commit -m "feat(nav): route Analyze → ThemeDetail; wire gallery→detail→export"
```

---

## Task 5: 清理旧模板残留 + AppDatabase v3

**Files:**
- Delete: `data/model/ProjectEntity.kt`、`data/model/ColorPaletteEntity.kt`、`data/local/ProjectDao.kt`、`data/local/ColorPaletteDao.kt`、`data/repository/ProjectRepository.kt`、`ui/main/MainScreen.kt`、`ui/main/MainScreenViewModel.kt`、`data/DataRepository.kt`
- Modify: `data/local/AppDatabase.kt`、`data/local/Converters.kt`、`di/AppModule.kt`

- [ ] **Step 1: 删除旧文件**

```bash
git rm app/src/main/java/com/palettemuse/data/model/ProjectEntity.kt \
  app/src/main/java/com/palettemuse/data/model/ColorPaletteEntity.kt \
  app/src/main/java/com/palettemuse/data/local/ProjectDao.kt \
  app/src/main/java/com/palettemuse/data/local/ColorPaletteDao.kt \
  app/src/main/java/com/palettemuse/data/repository/ProjectRepository.kt \
  app/src/main/java/com/palettemuse/ui/main/MainScreen.kt \
  app/src/main/java/com/palettemuse/ui/main/MainScreenViewModel.kt \
  app/src/main/java/com/palettemuse/data/DataRepository.kt
```

- [ ] **Step 2: AppDatabase 去 ProjectEntity/ColorPaletteEntity，version 3**

```kotlin
@Database(
    entities = [ThemeEntity::class, PhotoEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun themeDao(): ThemeDao
    abstract fun photoDao(): PhotoDao
}
```

- [ ] **Step 3: Converters 去 ProjectType/ColorRole**

删 `fromProjectType/toProjectType/fromColorRole/toColorRole`（若无其他枚举需 converter，Converters 可为空类或删 `@TypeConverters`）。

- [ ] **Step 4: AppModule 去 provideProjectDao/ColorPaletteDao/ProjectRepository**

删这 3 个 provide 方法 + 相关 import。`provideDatabase` 用 `fallbackToDestructiveMigration(dropAllTables = true)`（去 deprecation）。

- [ ] **Step 5: 编译 + 全套测试**

Run: `./gradlew :app:compileDebugKotlin :app:connectedAndroidTest`
Expected: 全绿（旧测试已删，新测试 Theme/Photo 相关）。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: remove legacy Project/ColorPalette/MainScreen scaffolding (db v3)"
```

---

## Task 6: Final triage（Plan 1 backlog）

**Files:**
- Modify: `data/local/PhotoDao.kt`（排序加 `id`）、`ui/capture/CaptureViewModel.kt`（CapturedSwatch 孤儿评估 + onFrameAnalyzed TARGET 实时匹配）、`ui/capture/CaptureScreen.kt`（onError 错误提示，可选）

- [ ] **Step 1: PhotoDao 排序加 `id` 次键**

`ORDER BY capturedAt DESC` → `ORDER BY capturedAt DESC, id DESC`（消除 capturedAt tie 的非确定性）。改 `observePhotosForTheme` + `getPhotosForThemeOnce`。

- [ ] **Step 2: CapturedSwatch 孤儿评估**

`CaptureViewModel` 的 `CapturedSwatch` + `capturedSwatches` 不再被读（Task 5 review 指出）。**删除** `CapturedSwatch` data class + `CaptureUiState.capturedSwatches` 字段（若 CaptureScreen 不再引用）。若 CaptureScreen 的「已捕捉预览条」要保留，改为从 `pendingCapture` 或新状态派生（MVP 可直接移除预览条，因确认条已覆盖）。

- [ ] **Step 3: onFrameAnalyzed TARGET 实时匹配（可选增强）**

当前 `onFrameAnalyzed` 硬编码 target `#B76E79`。改为：读所有主题的 `representativeHex`，算当前帧 vs 最匹配主题的 match%，显示「接近 ${theme.name} ${match}%」。注入 `ThemeRepository` 到 CaptureViewModel（或暴露一个 `currentTargetFlow`）。
> 若增加复杂度过高，本步可推 Plan 3，保持硬编码 + 标 TODO。

- [ ] **Step 4: onError 错误提示（可选，Plan 2 可推 Plan 3）**

`CaptureScreen` 快门 `onError` 空 lambda → 加 snackbar「拍照失败，请重试」。

- [ ] **Step 5: 测试 + 冒烟**

Run: `./gradlew :app:connectedAndroidTest`
冒烟：`android layout` 确认相机页 TARGET 显示动态主题名（若 Step 3 做了）。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: final triage (PhotoDao tiebreak, dead state cleanup, live target match)"
```

---

## Self-Review

**1. Spec coverage（对照 stitch-ui-brief v2 第 6 节）：**
- 首页主题画廊 + 真实色板 + 空状态 + FAB → Task 1 ✓
- 主题详情（起点 Hero + 瀑布流 + 色板 + 导出 FAB）→ Task 2 ✓
- 导出（合成海报 + 模板 + 分享/保存）→ Task 3 ✓
- NavGraph ThemeDetail 替代 Analyze → Task 4 ✓
- 清理旧模板残留 → Task 5 ✓
- 决策 8（MVP 精简：去 search、单模板网格等）→ 各 Task ✓

**2. Placeholder scan：** UI Composable 未给逐行代码——这是**设计稿驱动**（每 Task 注明 screen ID，实现者 `get_screen` 取 HTML 作视觉 source of truth）。数据绑定/ViewModel/Repository/测试代码完整。Task 6 Step 3/4 标「可选/推 Plan 3」，是显式范围边界，非占位符。

**3. Type consistency：** `ThemeWithPhotos(theme, photos, palette)`（Plan 1 定义）在 Task 1/2/3 一致；`getThemeWithPhotos(id): ThemeWithPhotos?`（Task 2 加）与 Task 3 ExportViewModel 消费一致；`Routes.ThemeDetail(themeId)` 在 Task 2/4 一致。

**已知边界：**
- UI 视觉还原度依赖实现者 `get_screen` + Aura Aesthetic 理解，**每个 UI Task 必须冒烟**（`android screen capture` + 对照设计稿 screenshot）。
- 模板选择器（Task 3）MVP 只实现「网格」，其余 chip 可选禁用。
- onFrameAnalyzed 实时匹配（Task 6 Step 3）可推 Plan 3。
