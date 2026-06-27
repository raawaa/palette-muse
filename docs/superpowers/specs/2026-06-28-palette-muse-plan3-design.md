# Palette Muse Plan 3 设计（增量改进 + 发布就绪）

> 状态：设计已对齐（user OK 段 1/2/3/4），待 user 审 spec 后进入 writing-plans

## Overview

Plan 1（核心引擎）+ Plan 2（UI 适配）已合并到 main，MVP 完整闭环。Plan 3 完成累积的 6 项 backlog：**TARGET 实时主题匹配 · 多照海报拼贴 · 正式 `Migration(2,3)` · 重命名/改色对话框 · 导出多模板 · CaptureScreen FQN 清理**。增量改进 + 发布前就绪，不破坏已合并的 Plan 1+2。

## Goals
- 相机体验增强：实时主题匹配（取代硬编码 Rose Gold）
- 主题管理增强：重命名 / 改色对话框（取代 Plan 2 final fix 的 "即将推出" Snackbar）
- 导出增强：4 模板（Grid/Film/Journal/Minimal）+ 多照拼贴
- 发布前就绪：正式 `Migration(2,3)` 替代 `fallbackToDestructiveMigration`
- 代码卫生：FQN 清理

## Non-Goals
- 不重做 Plan 1/2
- 不加社交 / AI 配色建议（Plan 4+）
- 不改 `ThemeRepository` 核心算法（仅 `onFrameAnalyzed` 客户端算最匹配，不引入新匹配逻辑）
- 不改 `ColorMatcher`（复用）

## Architecture

### 1. `CaptureViewModel` 改动（TARGET 实时匹配）

注入 `ThemeRepository`；`init` 缓存 `themes` 为 `StateFlow<List<ThemeEntity>>`；`onFrameAnalyzed` 算当前帧 vs 最匹配主题（`ColorMatcher.matchPercentage`，O(n) 内存纯函数，不 suspend）。

```kotlin
data class TargetState(val name: String, val matchPct: Int, val isFallback: Boolean)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val colorAnalyzer: ColorAnalyzer,
    private val photoStorage: PhotoStorage,
    private val colorMatcher: ColorMatcher
) : ViewModel() {
    private val _themes = MutableStateFlow<List<ThemeEntity>>(emptyList())

    init {
        viewModelScope.launch {
            themeRepository.getAllThemes().collect { _themes.value = it }
        }
    }

    fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
        val sampleHex = colorMatcher.extractCenterAverageColor(pixels, width, height)
        val best = _themes.value
            .map { it to colorMatcher.matchPercentage(it.representativeHex, sampleHex) }
            .filter { it.second >= THEME_MATCH_THRESHOLD }
            .maxByOrNull { it.second }
        val target = if (best != null) {
            TargetState(best.first.name, best.second, isFallback = false)
        } else {
            TargetState("Rose Gold", 0, isFallback = true)
        }
        _uiState.value = _uiState.value.copy(
            targetTheme = target,
            matchPercentage = target.matchPct
        )
    }

    companion object {
        const val THEME_MATCH_THRESHOLD = 60
    }
}
```

`CaptureUiState` 新增 `targetTheme: TargetState`（替代 `matchPercentage: Int` 单字段）。

### 2. `PosterRenderer` 改动（多照 + 4 模板）

`PosterConfig` 加 `photos: List<Bitmap>` + `template: TemplateType(GRID/FILM/JOURNAL/MINIMAL)`；`render` 内部 `when (config.template)` switch 调 4 个私有 `renderGrid/renderFilm/renderJournal/renderMinimal`。

```kotlin
enum class TemplateType { GRID, FILM, JOURNAL, MINIMAL }

data class PosterConfig(
    val title: String = "Moodboard Color Harmony",
    val subtitle: String = "curated with Palette Muse",
    val primaryColor: Int = Color.GRAY,
    val secondaryColor: Int = Color.LTGRAY,
    val accentColor: Int = Color.DKGRAY,
    val photos: List<Bitmap> = emptyList(),
    val template: TemplateType = TemplateType.GRID
)

fun render(photo: Bitmap?, config: PosterConfig, width: Int = 1080, height: Int = 1920): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    when (config.template) {
        TemplateType.GRID -> renderGrid(canvas, config, width, height)
        TemplateType.FILM -> renderFilm(canvas, config, width, height)
        TemplateType.JOURNAL -> renderJournal(canvas, config, width, height)
        TemplateType.MINIMAL -> renderMinimal(canvas, config, width, height)
    }
    return bitmap
}

private fun renderGrid(canvas: Canvas, config: PosterConfig, w: Int, h: Int) { /* 2x2 Bento + 标题 + 色板（Plan 2 已有，扩展 photos 来自 config） */ }
private fun renderFilm(canvas: Canvas, config: PosterConfig, w: Int, h: Int) { /* 顶部大照（config.photos[0]）+ 下方 3 小照横排 + 黑/白边框 */ }
private fun renderJournal(canvas: Canvas, config: PosterConfig, w: Int, h: Int) { /* 散落拼贴，1-2 主照 + 小照倾斜 + 标题 */ }
private fun renderMinimal(canvas: Canvas, config: PosterConfig, w: Int, h: Int) { /* 主照 3/4 + 主题名/色板底部 + 1 小图角标 */ }
```

各模板视觉定义精确像素（边距/字号/间距）由 implementer 按 brief 调，单元测试 + 冒烟（android screen capture）验证。

### 3. `ExportViewModel` 改动（多模板 + 选图）

```kotlin
data class ExportUiState(
    val data: ThemeWithPhotos? = null,
    val selectedTemplate: TemplateType = TemplateType.GRID,
    val previewBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val exportSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val posterRenderer: PosterRenderer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val themeId: String = checkNotNull(savedStateHandle["themeId"])
    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val data = themeRepository.getThemeWithPhotos(themeId) ?: return@launch
            _uiState.value = _uiState.value.copy(data = data, isLoading = false)
            generatePreview()
        }
    }

    fun selectTemplate(template: TemplateType) {
        _uiState.value = _uiState.value.copy(selectedTemplate = template)
        generatePreview()
    }

    private fun generatePreview() {
        val data = _uiState.value.data ?: return
        val photos = data.photos.map { /* loadBitmap from photo.imagePath */ }
        val count = when (_uiState.value.selectedTemplate) {
            TemplateType.GRID -> 4
            TemplateType.FILM -> 4   // 1 大 + 3 小
            TemplateType.JOURNAL -> 3
            TemplateType.MINIMAL -> 2  // 1 大 + 1 accent
        }.coerceAtMost(photos.size)
        val config = PosterConfig(
            title = data.theme.name,
            primaryColor = parseColor(data.theme.representativeHex),
            photos = photos.take(count),
            template = _uiState.value.selectedTemplate
        )
        val preview = posterRenderer.render(null, config)
        _uiState.value = _uiState.value.copy(previewBitmap = preview)
    }

    fun sharePoster(context: Context) { /* Plan 2 已有 */ }
    fun savePoster(context: Context) { /* Plan 2 已有 */ }
}
```

选图策略：按模板取前 N 张（Grid=4/Film=4/Journal=3/Minimal=2），不足时取实际数量。

### 4. `ExportScreen` 改动（4 预览 Composable + 模板 chip）

模板 chip（4 个：网格/胶片/日记/极简，点击切换 `selectTemplate`）+ 4 预览 Composable（按 `selectedTemplate` 条件渲染对应 Composable）。

- `PosterPreviewGrid`（Plan 2 已有，扩展接收 `photos: List<Bitmap>`）
- `PosterPreviewFilm`（新增：顶部大照 + 下方 3 小照横排 + 黑/白边框）
- `PosterPreviewJournal`（新增：散落拼贴）
- `PosterPreviewMinimal`（新增：主照 3/4 + 主题名/色板底部 + 1 小图角标）

### 5. `ThemeDetailScreen` 改动（重命名 / 改色 AlertDialog）

`more_horiz` 菜单（Plan 2 已有）三项：重命名 / 改主题色 / 删除。**替代 Plan 2 final fix 的 "即将推出" Snackbar**——改为真实 AlertDialog：

```kotlin
// 重命名
var showRename by remember { mutableStateOf(false) }
DropdownMenuItem(text = { Text("重命名") }, onClick = { showRename = true; expanded = false })
if (showRename) {
    RenameDialog(
        initial = state.data?.theme?.name ?: "",
        onConfirm = { vm.renameTheme(it); showRename = false },
        onDismiss = { showRename = false }
    )
}
// 改色类似 EditColorDialog
```

`RenameDialog` / `EditColorDialog` 用 Material3 `AlertDialog`：
- **重命名**: `OutlinedTextField`（initial=当前名）+ 确认/取消 → `vm.renameTheme(newName)`（ViewModel 已暴露 `renameTheme` Plan 2 实现）
- **改色**: hex `OutlinedTextField`（initial=`theme.representativeHex`）+ 48dp 圆形预览色点 + 确认/取消 → `vm.updateThemeColor(newHex)`（ViewModel 已暴露 `updateThemeColor` Plan 2 实现）

### 6. `AppDatabase` + `AppModule` 改动（`Migration(2,3)`）

```kotlin
@Database(
    entities = [ThemeEntity::class, PhotoEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun themeDao(): ThemeDao
    abstract fun photoDao(): PhotoDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `color_palettes`")
                db.execSQL("DROP TABLE IF EXISTS `projects`")
            }
        }
    }
}
```

`AppModule.provideDatabase`：`Room.databaseBuilder.addMigrations(AppDatabase.MIGRATION_2_3).build()`（**移除 `fallbackToDestructiveMigration`**）。

### 7. `CaptureScreen` FQN 清理

`import androidx.compose.runtime.mutableStateOf` + `import androidx.compose.material3.SnackbarHost` / `SnackbarHostState`，去掉 Plan 2 Task 6 遗留的全限定名。

## Data Flow（端到端）

```
拍照 → onFrameAnalyzed(缓存 themes 算最匹配) → TARGET pill 更新
  → 确认条 → 确认 → DB(createThemeAndSave / savePhotoToTheme)
  → 详情 → 点 more → 重命名/改色 AlertDialog → vm.renameTheme / updateThemeColor → DB
  → 导出 → 选模板 chip + 选图 → PosterRenderer.render(theme, photos, template) → saveToGallery
  → Migration(2,3) on app update from v2
```

## File Structure

| 文件 | 动作 | 职责 |
|------|------|------|
| `ui/capture/CaptureViewModel.kt` | Modify | 注入 ThemeRepository + 缓存 themes + `onFrameAnalyzed` 算最匹配 + `TargetState` |
| `ui/capture/CaptureScreen.kt` | Modify | TARGET pill 显示 `targetTheme` + 删 Match badge + FQN 清理 |
| `core/PosterRenderer.kt` | Modify | `PosterConfig` 加 `photos: List<Bitmap>` + `template: TemplateType` + 4 模板 render |
| `ui/export/ExportViewModel.kt` | Modify | 选图（按模板 N 张）+ `PosterRenderer.render(theme, photos, template)` + `selectTemplate` |
| `ui/export/ExportScreen.kt` | Modify | 模板 chip + 4 预览 Composable（Film/Journal/Minimal 新增） |
| `ui/theme/ThemeDetailScreen.kt` | Modify | more 菜单 → `RenameDialog` / `EditColorDialog` AlertDialog（替代 Snackbar） |
| `data/local/AppDatabase.kt` | Modify | v3 entities=[Theme,Photo] + `MIGRATION_2_3` 删旧表 |
| `di/AppModule.kt` | Modify | `addMigrations(MIGRATION_2_3)` 移除 `fallbackToDestructiveMigration` |
| `ui/capture/CaptureViewModelTest.kt` | Modify | 加实时匹配测试（注入 fake themes + 推进 frame） |
| `core/PosterRendererTest.kt` | New | 4 模板生成 Bitmap + size/dominant color assert |
| `data/local/AppDatabaseMigrationTest.kt` | New | Room `migrationTestHelper` 测 v2→v3（删旧表，保 Theme/Photo） |
| `ui/theme/ThemeDetailScreenTest.kt` | Modify | 加对话框 Compose UI 测试 |
| `ui/export/ExportViewModelTest.kt` | Modify | 加多模板/选图测试 |

## Error Handling
- **TARGET 实时匹配**: 无主题 / 全 `matchPct < THEME_MATCH_THRESHOLD(60)` → fallback `TargetState("Rose Gold", 0, isFallback=true)`
- **海报无照 / photos.size < count**: `coerceAtMost(photos.size)`（用实际数量）；0 张 → `render` 出白板（error state 显示）
- **对话框取消**: `onDismiss`，不调 VM
- **改色 hex 解析失败**: confirm 按钮禁用（或点击不调 VM）
- **Migration 失败**: Room 抛 `SQLiteException`（App 崩，dev 阶段可见，prod 应上报）
- **实时匹配 themes 缓存为空**（初始化前）: 首 frame 用 fallback "Rose Gold" 0%

## Testing
- `CaptureViewModelTest`: 实时匹配（注入 fake `ThemeRepository` 推进 themes + 推进 frame 验证 `targetTheme`）+ fallback（空主题 / 全低 match）
- `PosterRendererTest`: 4 模板生成 Bitmap（assert 非空 + size 正确 + dominant color 大致对）—— 4 个独立测试
- `AppDatabaseMigrationTest`: Room `migrationTestHelper` 测 v2→v3：建 v2 schema（Theme/Photo + Project/ColorPalette），插入数据，run MIGRATION_2_3，断言 v3 schema（无 Project/ColorPalette 表，Theme/Photo 数据保留）
- `ThemeDetailScreenTest`: 对话框 Compose UI（`composeTestRule.onNodeWithText("重命名")` + `performClick` + `onNodeWithText("新名字")` + `performTextInput` + confirm → 验证 VM `renameTheme` 调用）
- `ExportViewModelTest`: 多模板（4 个 selectTemplate 测试）+ 选图（4 个模板各自选图数量）
- 现有 21 connectedAndroidTest 仍绿

## Migration Strategy
- `Migration(2,3)`: `DROP TABLE IF EXISTS color_palettes; DROP TABLE IF EXISTS projects`（保 Theme/Photo）
- `fallbackToDestructiveMigration` 移除（用户从 v2 升级时走正式 Migration；v1 升级仍 fallback，但 v1 罕见）
- 数据库升级路径：v1（fallback destructive）→ v2（fallback destructive）→ v3（formal Migration 删旧表）
- 发布前需测：从 v2 数据库（含 Project/ColorPalette 数据）升级，验证 Theme/Photo 保留

## Risks
- **PosterRenderer 4 模板 Canvas 排版**: 无设计稿源，implementer 按 brief 像素调 + 单元测试 + android 冒烟（`screen capture`）验证视觉
- **实时匹配 themes Flow 延迟**: 用户删除主题后 `onFrameAnalyzed` 下一帧才生效——可接受（微秒级）
- **选图加载 Bitmap from path**: `File(path).inputStream().use { BitmapFactory.decodeStream(it) }`（IO）。`generatePreview` 在 `viewModelScope.launch` + 需 `withContext(Dispatchers.IO)`，但 `data: ThemeWithPhotos` 已有 `imagePath: String`，加载 Bitmap 需新方法（`loadBitmap(path): Bitmap` in PhotoDao 或 util）
- **4 预览 Composable 视觉一致**: 各模板预览 Composable 与 `PosterRenderer` 独立实现，**视觉可能略不一致**（MVP 可接受，Plan 4 收敛）

## Open Questions
无（已 user 对齐）。
