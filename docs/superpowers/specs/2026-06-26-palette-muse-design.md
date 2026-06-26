# Palette Muse — 架构设计文档

> **项目**: Palette Muse (ChromaMuse)
> **类型**: Android 时尚色彩个人工具 App
> **日期**: 2026-06-26
> **状态**: 设计确认，待实现

---

## 1. 项目概述

Palette Muse 是一款完全本地化的 Android 时尚色彩发现应用。用户通过手机相机拍摄穿搭或实物，自动提取并命名色彩，生成个人色彩作品画廊，并可导出为 moodboard 海报。

### 核心功能

- **实物色彩捕捉** — 相机实时比对目标色，显示匹配度，捕捉色样
- **穿搭色彩分析** — 拍照 → AI 自动提取主色/辅色并赋予语义名称
- **个人作品画廊** — 所有色彩作品以网格画廊展示，支持编辑/删除
- **海报导出** — 将作品合成为 moodboard 风格海报，保存或分享

---

## 2. 架构方案

### 2.1 整体架构: 单模块 MVVM

采用 Android 官方推荐的 Single Activity + Jetpack Compose + MVVM 架构。

```
PaletteMuse App
 ├── PaletteMuseApp.kt          (@HiltAndroidApp)
 ├── MainActivity.kt            (Single Activity)
 ├── di/                        (Hilt 依赖注入)
 ├── data/                      (Data Layer)
 ├── domain/                    (Domain Layer — 可选，留空)
 ├── ui/                        (UI Layer)
 ├── camera/                    (CameraX 封装)
 └── core/                      (核心算法/工具)
```

### 2.2 技术栈

| 组件 | 选型 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 导航 | Compose Navigation |
| 依赖注入 | Hilt |
| 本地存储 | Room |
| 相机 | CameraX |
| 取色算法 | `androidx.palette:palette` + K-Means 聚类 |
| 异步 | Kotlin Coroutines + Flow |
| 图片加载 | Coil (Compose 原生) |
| 最小 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 15 (API 35) |

### 2.3 架构分层

```
┌───────────────────────────────────┐
│           UI Layer                │
│  Compose Screens → ViewModels     │
│       (StateFlow / UDF)          │
├───────────────────────────────────┤
│         Domain Layer (可选)       │
│           UseCases                │
├───────────────────────────────────┤
│          Data Layer               │
│  Repository → Room DAO / File IO  │
├───────────────────────────────────┤
│        Camera / Core / Utils      │
│  ColorAnalyzer / ColorNamer /     │
│  ColorMatcher / PosterRenderer    │
└───────────────────────────────────┘
```

### 2.4 Hilt 模块设计

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun provideDatabase(): AppDatabase
    @Provides fun provideProjectDao(db: AppDatabase): ProjectDao
    @Provides fun provideProjectRepository(dao: ProjectDao): ProjectRepository
    @Provides @Singleton fun provideColorAnalyzer(): ColorAnalyzer
    @Provides @Singleton fun provideColorNamer(): ColorNamer
    @Provides @Singleton fun provideColorMatcher(): ColorMatcher
    @Provides @Singleton fun providePosterRenderer(): PosterRenderer
}
```

---

## 3. 导航流

```
NavGraph
 ├── HomeScreen (startDestination)
 │   ├── → CaptureScreen
 │   │   └── → AnalyzeScreen(projectId)
 │   │       └── → ExportScreen(projectId)
 │   └── → AnalyzeScreen(projectId)     // 从已有作品进入详情
 │       └── → ExportScreen(projectId)
```

- **单 Activity**，所有页面通过 NavHost 管理
- 参数传递以 `projectId` 为主，ViewModel 通过 Repository 取数据

---

## 4. 数据模型

### 4.1 Room 数据库

```kotlin
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val imagePath: String,
    val type: ProjectType
)

@Entity(tableName = "color_palettes",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = CASCADE
    )])
data class ColorPaletteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val role: ColorRole,
    val hexColor: String,
    val semanticName: String,
    val matchPercentage: Int? = null
)

enum class ProjectType { CAPTURE, OUTFIT }
enum class ColorRole { PRIMARY, SECONDARY, ACCENT }
```

### 4.2 Repository

```kotlin
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val paletteDao: ColorPaletteDao
) {
    fun getAllProjects(): Flow<List<ProjectEntity>>
    suspend fun getProject(id: String): ProjectEntity?
    suspend fun getPalettesForProject(projectId: String): List<ColorPaletteEntity>
    suspend fun saveProject(project: ProjectEntity, palettes: List<ColorPaletteEntity>)
    suspend fun deleteProject(id: String)
}
```

---

## 5. 核心色彩引擎

### 5.1 ColorAnalyzer — 取色

使用 AndroidX Palette 库原生 API，无需自实现聚类算法：

```kotlin
suspend fun analyze(bitmap: Bitmap): List<ColorPaletteEntity> = withContext(Dispatchers.Default) {
    val palette = Palette.from(bitmap)
        .maximumColorCount(12)     // 提取 12 个候选色
        .clearFilters()            // 不过滤黑白
        .generate()

    listOf(
        ColorPaletteEntity(role = PRIMARY, hexColor = palette.vibrantSwatch?.rgb?.toHex(), ...),
        ColorPaletteEntity(role = SECONDARY, hexColor = palette.lightVibrantSwatch?.rgb?.toHex(), ...),
        ColorPaletteEntity(role = ACCENT, hexColor = palette.mutedSwatch?.rgb?.toHex(), ...)
    )
}
```

- Palette 内置 6 个 Profile: **Vibrant / LightVibrant / DarkVibrant / Muted / LightMuted / DarkMuted**
- 映射方案: `Vibrant → PRIMARY` / `LightVibrant → SECONDARY` / `Muted → ACCENT`
- CIELAB 色差计算用于后续的 ColorMatcher 匹配度
- `Swatch.titleTextColor` 和 `Swatch.bodyTextColor` 可用于 UI 配色

### 5.2 ColorNamer — 语义命名

- HSL 色相映射到 6 个色相区 (红/橙/黄/绿/蓝/紫)
- 每区配 6-8 个氛围词 (Dusty, Morning, Urban, Golden, Misty, Deep...)
- 组合模版: `[氛围词] [色名]` → "Morning Sand", "Urban Mist"

### 5.3 ColorMatcher — 实时匹配

- 相机预览帧中心区域提取平均色
- CIELAB ΔE 色差计算
- `Match% = max(0, (100 - ΔE × 2.5))`

### 5.4 PosterRenderer — 海报合成

- Android Canvas API 绘制
- 模板: 照片(上) + 色板条(中) + 标题(下)
- 输出: PNG Bitmap → 系统 ShareSheet / 保存到相册

---

## 6. 屏幕 UI 详细设计

### 6.1 HomeScreen

- **TopAppBar**: 标题 ChromaMuse + 菜单按钮 + 用户头像
- **标题**: "我的作品集"
- **内容**: `LazyVerticalGrid(2列)`
  - 每个卡片: 缩略图 + 底部色板条 + 标题
  - 空状态: 引导插画 + "拍摄你的第一个色彩灵感"
  - 点击: 跳转到详情/分析页
- **FAB**: "+" 按钮 → 进入 CaptureScreen
- **BottomNav**: 首页 / 相机 / 个人 — 3 个 tab
- **ViewModel**: `HomeViewModel` → `StateFlow<HomeUiState>`

### 6.2 CaptureScreen

- **顶部**: 返回按钮 + 目标色名（可预设/选择）
- **中部**: CameraX 取景器预览
  - 实时匹配度标签 (92% Match)
- **色样历史**: 水平滚动 Captured Swatches 圆环 (显示匹配%)
- **底部**: 拍摄按钮 + 翻转相机 + 微调
- 按下快门:
  1. 冻结帧
  2. ColorAnalyzer 取色
  3. ColorNamer 命名
  4. 添加到当前会话色样列表
  5. 存为 ProjectEntity (type=CAPTURE)
- **ViewModel**: `CaptureViewModel` → `StateFlow<CaptureUiState>`

### 6.3 AnalyzeScreen

- **顶部**: 返回 + "穿搭色彩分析"
- **照片**: 全宽展示
- **自动标签**: SAGE / BEIGE 风格 chips
- **色板**: 三段式色条 (Primary / Secondary / Accent) + 语义名称
- **CTA**: "开始色彩探索" → 以此配色进入 ExportScreen
- 进入方式:
  - 从 CaptureScreen 自动跳转
  - 从 HomeScreen 点击已有作品
- **ViewModel**: `AnalyzeViewModel` → `StateFlow<AnalyzeUiState>`

### 6.4 ExportScreen

- **模态/全屏**: 海报预览 (Canvas 合成)
- **自动标题**: "Moodboard Color Harmony Vol. XX"
- **操作**: 
  - [📤 共享] → 系统 ShareSheet
  - [💾 保存海报] → MediaStore 写入相册
- **ViewModel**: `ExportViewModel` → `StateFlow<ExportUiState>`

---

## 7. 设计系统映射 (从 Stitch 设计稿)

| 元素 | 值 |
|------|-----|
| 标题字体 | Playfair Display (serif, 时尚杂志感) |
| 正文字体 | Plus Jakarta Sans (sans-serif, 现代可读性) |
| 主色 | Rose Gold #b76e79 |
| 辅色 | Soft Lavender #e6e6fa |
| 背景 | Pearl White #FDFBF7 |
| 圆角 | 超软 Pill (24-32px radii) |
| 玻璃质感 | 背景模糊 20px + 半透明白色填充 |
| 阴影 | 主色/中性色 5-8% 透明度, blur 20-30px |

---

## 8. 文件结构 (最终)

```
app/src/main/java/com/palettemuse/
 ├── PaletteMuseApp.kt
 ├── MainActivity.kt
 ├── di/
 │   └── AppModule.kt
 ├── data/
 │   ├── local/
 │   │   ├── AppDatabase.kt
 │   │   ├── ProjectDao.kt
 │   │   ├── ColorPaletteDao.kt
 │   │   └── Converters.kt
 │   ├── model/
 │   │   ├── ProjectEntity.kt
 │   │   └── ColorPaletteEntity.kt
 │   └── repository/
 │       └── ProjectRepository.kt
 ├── domain/
 │   └── model/       (domain 模型，可选)
 ├── ui/
 │   ├── navigation/
 │   │   └── NavGraph.kt
 │   ├── home/
 │   │   ├── HomeScreen.kt
 │   │   └── HomeViewModel.kt
 │   ├── capture/
 │   │   ├── CaptureScreen.kt
 │   │   └── CaptureViewModel.kt
 │   ├── analyze/
 │   │   ├── AnalyzeScreen.kt
 │   │   └── AnalyzeViewModel.kt
 │   ├── export/
 │   │   ├── ExportScreen.kt
 │   │   └── ExportViewModel.kt
 │   └── theme/
 │       └── Theme.kt
 ├── camera/
 │   └── CameraManager.kt
 └── core/
     ├── ColorAnalyzer.kt
     ├── ColorNamer.kt
     ├── ColorMatcher.kt
     └── PosterRenderer.kt
```

---

## 9. 隐私与权限

| 权限 | 用途 | 时机 |
|------|------|------|
| `CAMERA` | 相机取景/拍照 | 首次进入 CaptureScreen (运行时请求) |
| `WRITE_EXTERNAL_STORAGE` (API 28-) | 保存海报到相册 | 仅 Android 9 及以下旧设备 |

- **Android 10+**: 不需要存储权限 — 海报是我们自己创建的文件，直接 `ContentResolver.insert()` 到 `MediaStore.Images` 即可
- 无需 `READ_MEDIA_IMAGES`：我们不读取用户相册，仅写入自己的作品
- 无需网络权限，完全本地运行

---

## 10. 实现路线图

| Phase | 内容 | 产出 |
|-------|------|------|
| **Phase 1: 脚手架** | Android 项目创建 + Hilt + Room + Compose 基础 + Navigation 骨架 | 空壳 App，可导航 4 个页面 |
| **Phase 2: Core 算法** | ColorAnalyzer + ColorNamer + ColorMatcher + PosterRenderer | 可运行的色彩引擎 |
| **Phase 3: 相机模块** | CameraX 集成 + CaptureScreen | 可拍照取色 |
| **Phase 4: 数据分析** | AnalyzeScreen + 色板展示 + 语义命名展示 | 完整的取色→分析流程 |
| **Phase 5: 作品集与导出** | HomeScreen + ExportScreen + 分享/保存 | MVP 闭环 |
| **Phase 6: 视觉打磨** | 设计系统主题化 + 动画 + 适配 | 符合 Stitch 设计稿 |
