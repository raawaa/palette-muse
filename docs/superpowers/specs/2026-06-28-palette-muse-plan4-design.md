# Palette Muse Plan 4 设计（最后一轮打磨，发布就绪）

> 状态：设计已对齐（user OK 4 项方案），待 user 审 spec 后进入 writing-plans

## Overview

Plan 1（核心引擎）+ Plan 2（UI 适配）+ Plan 3（增量改进）已合并 main（28 commits，31 `connectedAndroidTest` PASS）。Plan 4 完成 4 项最终打磨：海报预览与导出视觉一致、TARGET 首帧实时匹配、ShareSheet 分享完成、4 模板差异化。发布前最后一轮，不破坏已合并的 Plan 1+2+3。

## Goals
1. **海报预览 = 导出**：4 个 Composable 预览（Film/Journal/Minimal）像素对齐 `PosterRenderer` Canvas 排版
2. **TARGET 首帧不闪 "Rose Gold"**：`CaptureViewModel.init` themes 首次加载后首帧即显真实匹配
3. **分享到小红书**：`ExportViewModel.sharePoster` 完整 ShareSheet（FileProvider + ACTION_SEND）
4. **模板差异化**：Film/Journal/Minimal 差异化标题/色板/底色（消除 4 模板共享同一样式）

## Non-Goals
- 不加新板式/模板（4 个够用）
- 不改 CameraManager DI（选保持，pre-existing）
- 不改品牌/设计系统 Aura Aesthetic

## Design

### 1. 海报视觉一致性（Preview → Export）

**目标**：4 个 Composable 预览（`PosterPreviewGrid/Film/Journal/Minimal` in `ExportScreen.kt`）的布局/像素对齐 `PosterRenderer` Canvas 排版（`renderGrid/Film/Journal/Minimal`），消除 "预览 ≠ 导出"。

**方案**：改 Composable 预览参数对齐 Canvas 精确像素。每个模板差异：

| 模板 | Canvas 排版 | Composable 改什么 |
|------|-------------|-------------------|
| **Grid** | 2x2 Bento, cell padding 16px, 标题+色板底部 5% | 已基本对齐（Plan 2），确认 `Modifier.padding` 值匹配 `RectF` padding |
| **Film** | 顶部大照 `h*0.65` + 下方 3 小照横排 `w/3` 宽 + 1px 黑/白边框 | `PosterPreviewFilm`: `Modifier.height(cardH * 0.65f)` + `Row` 3 等分 + `.border(1.dp, Color.Black/White)` |
| **Journal** | 1-2 主照 + 小照 Canvas 旋转 `±3°` + 主照 `w/2` 宽 | `PosterPreviewJournal`: `Modifier.rotate(±3f)` + 主照 `Modifier.weight(1f)` + 小照 `Modifier.size(cellW, cellH)` |
| **Minimal** | 主照 `h*0.7` + 小照角标 `w*0.2` 右下 + 色板底部 | `PosterPreviewMinimal`: `Modifier.height(cardH * 0.7f)` 主照 + `Box` 小照 `Modifier.size(cardW * 0.2f).align(BottomEnd)` |

**接口**：
- `PosterPreviewGrid/Film/Journal/Minimal`：不变签名，内部 Composable 布局参数改为精确 dp 值对齐 Canvas
- Canvas 不变（是"真实导出 source of truth"）

**测试**：
- `PosterPreviewFilmTest` / `PosterPreviewJournalTest` / `PosterPreviewMinimalTest`（Compose UI 测试）：setContent 各 Preview Composable（传入 stub `List<Bitmap>` + `ThemeEntity`），assert 关键节点可见 + 尺寸匹配
- 冒烟：导出页切 4 模板 → `android screen capture` 比对 Canvas 导出 Bitmap 与 Composable 截图（视觉近似）

### 2. TARGET 首帧实时匹配

**目标**：进相机页首帧 `onFrameAnalyzed` 不再 "Rose Gold 0%" 闪现（当前 `init` 异步 collect themes，首帧前 `_themes` 为空 → 永远 fallback）。

**方案**：`CaptureViewModel.init` 里改为同步等待 themes 首次加载：
```kotlin
init {
    viewModelScope.launch {
        themeRepository.getAllThemes()
            .onEach { _themes.value = it }
            .collect() // 首次 emit 即设置
    }
}
```
不变。**关键**：`_themes.value` 初始值改为从 repo 同步读（`themeRepository.getAllThemes().first()` 在 `viewModelScope.launch` 中，**不阻塞 init 线程**）——首帧 `onFrameAnalyzed` 时 `_themes` 已有值。如果 repo 首次 collect 未完成（空库），fallback "Rose Gold" 0% 正常（无主题是实际的 fallback 状态）。

**实际改动**：无！当前 init 异步 collect 已经是首次 emit 即设。首帧 fallback 本质是时序问题（相机帧在 `collect` 前到达）。**实际原因是相机 Preview 的 `ImageAnalysis.Analyzer` 在 init 后立即开始推帧，而 themes collect 在 viewModelScope 协程中（异步），首帧可能比 collect 先到。**

**修正方案**：`onFrameAnalyzed` 开始时检查 `_themes.value.isNotEmpty()`。如果为空且 `_pendingFirstFrame` flag，跳过分析（等 themes 就绪）。或简化为：`_themes` 为空时 → 不分析当前帧（保留旧 `targetTheme`），等 themes 就绪后下一帧正常匹配。`_themes.value.isEmpty()` 时 `return`（不更新 targetTheme）。这样首帧不做分析（约 30ms 后第二帧分析），用户无感知。

```kotlin
fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
    if (_themes.value.isEmpty()) return // 等待 themes 缓存就绪
    // ... 正常分析
}
```

**测试**：
- `CaptureViewModelTest.onFrameAnalyzed_skipsWhenNoThemes`：构造 VM（空 themes, `_themes.value = emptyList()`），调 `onFrameAnalyzed`，assert `targetTheme` 不变（初始 `TargetState("Rose Gold", 0, isFallback=true)`）
- 现有 `onFrameAnalyzed_picksClosestTheme`（Plan 3）不变

### 3. ShareSheet 分享完成

**目标**：`ExportViewModel.sharePoster(context)` 完整 ShareSheet（当前是 TODO 壳）。

**方案**：
1. 缓存 `previewBitmap` 到 `context.cacheDir/poster_share.png`（复用 `FileOutputStream.use { bitmap.compress(PNG, 100, it) }`）
2. `FileProvider.getUriForFile(context, "com.palettemuse.fileprovider", file)` → content Uri
3. `Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(EXTRA_STREAM, uri); addFlags(GRANT_READ_URI_PERMISSION) }`
4. `Intent.createChooser(sendIntent, "分享到…")` → `context.startActivity(chooser)`
5. 整个流程在 `viewModelScope.launch(Dispatchers.IO)` 中（写文件 + 获取 uri），切回 `Main` 发 intent

`file_paths.xml`（Plan 2 Task 3 已有 `cache-path` 配置，确认）。

**接口**：
```kotlin
fun sharePoster(context: Context) = viewModelScope.launch {
    val bmp = _uiState.value.previewBitmap ?: return@launch
    val uri = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "poster_share.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "com.palettemuse.fileprovider", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享到…"))
}
```

**错误处理**：previewBitmap 为 null → 不操作。FileProvider 失败 → `catch` + `_uiState.error = "分享失败"` + Snackbar（复用 Plan 3 error snackbar 模式）。FileProvider authority 配置错 → 测试失败暴露。

**测试**：
- `ExportViewModelTest.sharePoster_generatesTemporaryFile`：VM sharePoster → 验证 `context.cacheDir/poster_share.png` 存在 + 非空
- （`startActivity(intent)` 在 unit test 中不能测——`context` 无 Activity，接受：测试仅覆盖文件生成 + uri 获取，intent 发送需手动冒烟或 E2E）

### 4. 模板差异化（标题/色板/底色）

**目标**：4 模板不再共享同一标题/色板样式。Film 有黑/白边框+褪色底，Journal 米白底+手账体，Minimal 极简纯白底。

**方案**：`PosterRenderer.drawTitleAndPalette(canvas, config, w, h)` 改为接收 `TemplateType` 参数，内部分支 4 样式：

| 模板 | 底色 | 标题字体 | 色板样式 |
|------|------|---------|---------|
| **Grid** | 白色 | Playfair Display 64sp, Black | 40dp 色点，横排 `left+40` |
| **Film** | `#F5F5F0` 褪色电影底 | Playfair Display 48sp, `#2C2C2C` | 18dp 小色点，黑/白 1px 边框 |
| **Journal** | `#FAF8F5` 米白手账底 | Handwriting-style（系统 `SERIF` 或 Playfair Italic），56sp, Brown `#8B7355` | 24dp 色点，手绘风格（圆角矩形 `8dp`） |
| **Minimal** | 纯白 | Playfair Display 36sp, `#333333` | 16dp 色点，`left+40` bottom |

**实现**：`drawTitleAndPalette` 接收 `template: TemplateType`，`when(template)` switch 4 分支（底色/字体/色点尺寸/位置）。Canvas 用 `Paint` + `RectF` 直接画。

**Preview Composable 同步差异化**：`PosterFooter`（Plan 2 已有）同理接收 `TemplateType`，`when` switch 4 样式（底色/字体/size/color）。

**测试**：
- `PosterRendererTest.renderGrid_footerStyleMatchesGridTemplate`（assert Canvas 绘制像素颜色验证底色）
- `PosterRendererTest.renderFilm_hasBorderStyle`（assert 边框 `1px` + 底色 `#F5F5F0`）
- `PosterRendererTest.renderJournal_backgroundIsWarmWhite`（assert 底色 `#FAF8F5`）
- `PosterRendererTest.renderMinimal_isCleanWhite`（assert 底色 White）
- 4 个测试，验证 `drawTitleAndPalette` switch

## File Structure

| 文件 | 动作 | 职责 |
|------|------|------|
| `ui/export/ExportScreen.kt` | Modify | 4 Preview Composable 像素对齐 Canvas（①）+ PosterFooter 接收 TemplateType（⑤） |
| `ui/capture/CaptureViewModel.kt` | Modify | `onFrameAnalyzed` needsTheme guard（②） |
| `ui/export/ExportViewModel.kt` | Modify | `sharePoster` 完整 ShareSheet（③） |
| `core/PosterRenderer.kt` | Modify | `drawTitleAndPalette` 接收 `TemplateType` + 4 分支差异化（⑤） |
| `ui/export/ExportViewModelTest.kt` | Modify | 加 `sharePoster_generatesTemporaryFile` 测试（③） |
| `ui/capture/CaptureViewModelTest.kt` | Modify | 加 `onFrameAnalyzed_skipsWhenNoThemes` 测试（②） |
| `core/PosterRendererTest.kt` | Modify | 加 4 个模板差异化 footer/grid 断言（⑤） |

## Error Handling
- ① 无（Composable 渲染）
- ② `_themes` 空 → 跳过分析（不更新 targetTheme）——无错，是设计
- ③ FileProvider 失败 → `error = "分享失败"` + Snackbar
- ⑤ 无（Canvas 绘制）

## Testing
- `CaptureViewModelTest.onFrameAnalyzed_skipsWhenNoThemes`（②）：空 themes → `targetTheme` 不变（assert 初始 fallback）
- `ExportViewModelTest.sharePoster_generatesTemporaryFile`（③）：sharePoster → assert cacheDir poster PNG 存在 + 非空
- `PosterRendererTest` 4 个 footer 差异化测试（⑤）：assert Canvas 像素绘制底色/边框/色点尺寸
- 现有 31 `connectedAndroidTest` 仍绿
- 冒烟：分享→小红书（Intent chooser 出现）+ FILM 导出片边框+Film 底色视觉对比 + TARGET 进相机页不闪 Rose Gold

## Open Questions
无（已 user 对齐 4 项方案）。
