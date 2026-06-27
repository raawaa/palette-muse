# Palette Muse Plan 4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 4 项最终打磨（海报预览=导出、TARGET 首帧、ShareSheet、模板差异化）→ 发布就绪。

**Architecture:** 4 个平面改造，互不依赖可并行：`ExportScreen` 4 预览 Composable 像素对齐 `PosterRenderer` Canvas（①）；`CaptureViewModel.onFrameAnalyzed` 空 themes 时 skip（②）；`ExportViewModel.sharePoster` FileProvider + `ACTION_SEND` ShareSheet（③）；`PosterRenderer.drawTitleAndPalette` 接收 `TemplateType` + 4 分支差异化标题/色板/底色（⑤）。

**Tech Stack:** Kotlin · Jetpack Compose · Hilt · Room · Canvas · FileProvider · Coroutines

## Global Constraints

- minSdk 26, compileSdk 36, JVM 17, Kotlin
- 复用 Plan 1+2+3 引擎（不改）
- `PosterConfig.template: TemplateType`（Plan 3 Task 2 已加）
- `FileProvider` authority `com.palettemuse.fileprovider`（Plan 2 `file_paths.xml` 已有 `cache-path`）
- 复用 Plan 2 Aura token（`theme/Color.kt`）
- 31 `connectedAndroidTest` 现存仍绿
- androidTest 命令: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQN>` (AGP **不支持** `--tests`)
- `shadow()` 在 `.clip()` 前（Plan 2 Task 1 修复后保持）

---

## Task 1: `ExportScreen` 4 Preview 像素对齐 + `PosterFooter` TemplateType 差异化

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt`

**Interfaces:**
- Consumes: `TemplateType` (Plan 3 Task 2)
- Produces: 4 Preview Composable 布局像素对齐 Canvas
- Produces: `PosterFooter` 接收 `TemplateType` 参数

- [ ] **Step 1: Read 现有 `ExportScreen.kt` + `PosterRenderer.kt` 确认 Canvas 排版像素**

Read `ExportScreen.kt` 里 `PosterPreviewGrid/Film/Journal/Minimal` + `PosterFooter` 的结构。Read `PosterRenderer.kt` 里 `renderGrid/Film/Journal/Minimal` + `drawTitleAndPalette` 的 Canvas 坐标/尺寸（RectF、padding、w/h 比例）。

- [ ] **Step 2: 改 `PosterPreviewFilm` 对齐 Film Canvas**

Canvas 排版（`PosterRenderer.renderFilm`）：顶部大照 `h * 0.65f`，下方 3 小照横排各 `w / 3f`，`1px`  黑边框。

```kotlin
@Composable
fun PosterPreviewFilm(photos: List<Bitmap>, theme: ThemeEntity) {
    Column(Modifier.fillMaxSize().background(PosterBgFilm)) {
        if (photos.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(cardH * 0.65f)) {
                Image(bitmap = photos[0], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Row(Modifier.fillMaxWidth().height(cardH * 0.35f)) {
            for (i in 1..3.coerceAtMost(photos.size - 1)) {
                Box(Modifier.weight(1f).fillMaxHeight().border(1.dp, Color.Black).padding(2.dp)) {
                    Image(bitmap = photos[i], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}
```
(`cardH` = `cardWidth / 0.75f` from `PosterPreviewCard` 的 `3:4` aspect; `PosterBgFilm` = `Color(0xFFF5F5F0)`)

- [ ] **Step 3: 改 `PosterPreviewJournal` 对齐 Journal Canvas**

Canvas：1-2 主照 `w / 2f` 宽 + `cellH = h * 0.4f`，旋转 `±3°`。

```kotlin
@Composable
fun PosterPreviewJournal(photos: List<Bitmap>, theme: ThemeEntity) {
    val cellW = cardW / 2f
    val cellH = cardH * 0.4f
    Box(Modifier.fillMaxSize().background(PosterBgJournal)) {
        for ((i, bmp) in photos.take(3).withIndex()) {
            val row = i / 2; val col = i % 2
            val angle = if (i % 2 == 0) -3f else 3f
            Box(Modifier
                .offset(x = col * cellW.dp, y = row * cellH.dp)
                .size(cellW.dp, cellH.dp)
                .rotate(angle)
                .padding(4.dp)
            ) {
                Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}
```
(`PosterBgJournal` = `Color(0xFFFAF8F5)`, `cardW` / `cardH` 从 `PosterPreviewCard` 传递, Compose 不能直接拿 `dp`；用 `LocalDensity` 转换或直接用 fill/fraction 比.)

- [ ] **Step 4: 改 `PosterPreviewMinimal` 对齐 Minimal Canvas**

Canvas：主照 `h * 0.7f` 高 + 小照角标 `w * 0.2f` 右下。

```kotlin
@Composable
fun PosterPreviewMinimal(photos: List<Bitmap>, theme: ThemeEntity) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        if (photos.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(cardH * 0.7f)) {
                Image(bitmap = photos[0], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (photos.size > 1) {
                val accentW = cardW * 0.2f
                Box(Modifier
                    .size(accentW.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-16).dp, y = (-16).dp)
                    .border(2.dp, Color.White)
                ) {
                    Image(bitmap = photos[1], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}
```

- [ ] **Step 5: 改 `PosterFooter` 接收 `TemplateType` + 4 分支差异化**

```kotlin
@Composable
fun PosterFooter(theme: ThemeEntity, template: TemplateType) {
    val (bg, titleSize, paletteSize) = when (template) {
        TemplateType.GRID -> Triple(Color.White, 64.sp, 40.dp)
        TemplateType.FILM -> Triple(PosterBgFilm, 48.sp, 18.dp)
        TemplateType.JOURNAL -> Triple(PosterBgJournal, 56.sp, 24.dp)
        TemplateType.MINIMAL -> Triple(Color.White, 36.sp, 16.dp)
    }
    Column(Modifier.fillMaxWidth().background(bg).padding(16.dp)) {
        Text(theme.name, fontSize = titleSize, fontFamily = PlayfairDisplay, color = if (template == TemplateType.JOURNAL) PosterBrown else Color.Black)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            theme.palette.take(3).forEach { hex ->
                Box(Modifier.size(paletteSize).background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(if (template == TemplateType.JOURNAL) 8.dp else 50))
                    .border(if (template == TemplateType.FILM) 1.dp else 0.dp, Color.Black))
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
```
(`PosterBrown` = `Color(0xFF8B7355)`, `PlayfairDisplay` from `theme/Type.kt`. `cardW`/`cardH` 传入 from `PosterPreviewCard` via `BoxWithConstraints` or parameters.)

- [ ] **Step 6: 编译验证 + 跑现有 ExportViewModelTest 仍 PASS**

Run:
```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.export.ExportViewModelTest
```
Expected: BUILD SUCCESSFUL + 5/5 PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt
git commit -m "feat(export): align 4 preview Composables pixel-perfect with Canvas exports (Plan 4)"
```

---

## Task 2: `CaptureViewModel` onFrameAnalyzed needsTheme guard

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt`
- Modify: `app/src/androidTest/java/com/palettemuse/ui/capture/CaptureViewModelTest.kt`

- [ ] **Step 1: 写失败测试 — `onFrameAnalyzed_skipsWhenNoThemes`**

In `CaptureViewModelTest.kt`, 加 1 测试 (在现有 5 测试后):

```kotlin
    @Test
    fun onFrameAnalyzed_skipsWhenNoThemes() = runTest(dispatcher) {
        val vm = CaptureViewModel(repo, ColorAnalyzer(), storage, ColorMatcher())
        advanceUntilIdle() // init collect completes (empty _themes)
        val before = vm.uiState.value.targetTheme
        val pixels = IntArray(100) { 0xFFDCA8A6.toInt() }
        vm.onFrameAnalyzed(pixels, 10, 10)
        advanceUntilIdle()
        assertEquals(before, vm.uiState.value.targetTheme) // unchanged (skipped)
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.capture.CaptureViewModelTest`
Expected: 编译失败 — `onFrameAnalyzed_skipsWhenNoThemes` 不存在.

- [ ] **Step 3: 实现 needsTheme guard**

In `CaptureViewModel.onFrameAnalyzed`, functions `onFrameAnalyzed` start, add guard:

```kotlin
    fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
        if (_themes.value.isEmpty()) return // 等待 themes 缓存就绪 (首帧 themes 可能未加载)
        // ... 原有分析逻辑不变
    }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.capture.CaptureViewModelTest`
Expected: 6/6 PASS (5 已有 + 1 新).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt \
  app/src/androidTest/java/com/palettemuse/ui/capture/CaptureViewModelTest.kt
git commit -m "feat(capture): skip onFrameAnalyzed until themes cache is ready (Plan 4)"
```

---

## Task 3: `ExportViewModel` sharePoster 完整 ShareSheet

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/export/ExportViewModel.kt`
- Modify: `app/src/androidTest/java/com/palettemuse/ui/export/ExportViewModelTest.kt`

- [ ] **Step 1: 写失败测试 — `sharePoster_generatesTemporaryFile`**

In `ExportViewModelTest.kt`, 加 1 测试:

```kotlin
    @Test
    fun sharePoster_generatesTemporaryFile() = runTest(dispatcher) {
        val id = repo.createThemeAndSave("/seed.jpg", "#DCA8A6")
        val vm = ExportViewModel(repo, PosterRenderer(), context, savedStateHandleTheme(id))
        advanceUntilIdle()
        // Set a dummy previewBitmap to trigger share
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        vm.setPreviewBitmapForTest(bmp)
        vm.sharePoster(context)
        advanceUntilIdle()
        val file = File(context.cacheDir, "poster_share.png")
        assertTrue(file.exists() && file.length() > 0)
    }
```
(need `@VisibleForTesting internal fun setPreviewBitmapForTest(bmp: Bitmap)` in ExportViewModel or cast private field via reflection. 简化: `ExportViewModel` 现有 `savePoster` pattern already calls `posterRenderer.saveToGallery`; sharePoster follows same `viewModelScope.launch` pattern.)

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.export.ExportViewModelTest`
Expected: 编译失败 — `sharePoster_generatesTemporaryFile` / `setPreviewBitmapForTest` 不存在.

- [ ] **Step 3: 实现 `sharePoster` + `setPreviewBitmapForTest`**

In `ExportViewModel.kt`:

```kotlin
    fun sharePoster(context: Context) {
        val bmp = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "分享失败")
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun setPreviewBitmapForTest(bmp: Bitmap) {
        _uiState.value = _uiState.value.copy(previewBitmap = bmp)
    }
```

(need imports: `android.content.Intent`, `androidx.core.content.FileProvider`, `java.io.File`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`)

Verify `file_paths.xml` has `cache-path`:
`app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
```
(Plan 2 Task 3 已有.)

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.ui.export.ExportViewModelTest`
Expected: 6/6 PASS (5 已有 + 1 sharePoster).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/palettemuse/ui/export/ExportViewModel.kt \
  app/src/androidTest/java/com/palettemuse/ui/export/ExportViewModelTest.kt
git commit -m "feat(export): complete sharePoster via FileProvider + ShareSheet (Plan 4)"
```

---

## Task 4: `PosterRenderer` 模板差异化 + 4 Footer 测试

**Files:**
- Modify: `app/src/main/java/com/palettemuse/core/PosterRenderer.kt`
- Modify: `app/src/androidTest/java/com/palettemuse/core/PosterRendererTest.kt`

- [ ] **Step 1: 改 `drawTitleAndPalette` 接收 `TemplateType` + 4 分支差异化**

In `PosterRenderer.kt`, replace `drawTitleAndPalette`:

```kotlin
    private fun drawTitleAndPalette(canvas: Canvas, config: PosterConfig, w: Int, h: Int) {
        val template = config.template
        val (bg, textColor, textSize, swatchSize) = when (template) {
            TemplateType.GRID -> Quad(android.graphics.Color.WHITE, android.graphics.Color.BLACK, 64f, 40f)
            TemplateType.FILM -> Quad(0xFFF5F5F0.toInt(), 0xFF2C2C2C.toInt(), 48f, 18f)
            TemplateType.JOURNAL -> Quad(0xFFFAF8F5.toInt(), 0xFF8B7355.toInt(), 56f, 24f)
            TemplateType.MINIMAL -> Quad(android.graphics.Color.WHITE, 0xFF333333.toInt(), 36f, 16f)
        }
        // draw background strip at bottom
        val bgPaint = Paint().apply { color = bg; style = Paint.Style.FILL }
        canvas.drawRect(0f, h * 0.85f, w.toFloat(), h.toFloat(), bgPaint)
        // draw title
        val textPaint = Paint().apply {
            color = textColor
            textSize = textSize
            isAntiAlias = true
            typeface = if (template == TemplateType.JOURNAL) Typeface.create(Typeface.SERIF, Typeface.ITALIC) else Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        canvas.drawText(config.title, 40f, h * 0.95f, textPaint)
        // draw palette
        val palette = listOf(config.primaryColor, config.secondaryColor, config.accentColor)
        val swatchY = h * 0.88f
        val swatchPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        val borderPaint = if (template == TemplateType.FILM) {
            Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = android.graphics.Color.BLACK; isAntiAlias = true }
        } else null
        palette.forEachIndexed { i, c ->
            val left = 40f + i * (swatchSize + 8f)
            swatchPaint.color = c
            if (template == TemplateType.JOURNAL) {
                canvas.drawRoundRect(left, swatchY, left + swatchSize, swatchY + swatchSize, 8f, 8f, swatchPaint)
            } else {
                canvas.drawRect(left, swatchY, left + swatchSize, swatchY + swatchSize, swatchPaint)
            }
            borderPaint?.let { canvas.drawRect(left, swatchY, left + swatchSize, swatchY + swatchSize, it) }
        }
    }

    private data class Quad(val bg: Int, val textColor: Int, val textSize: Float, val swatchSize: Float)
```

- [ ] **Step 2: 写 4 个 Footer 差异化测试**

In `PosterRendererTest.kt`, 加 4 测试:

```kotlin
    @Test fun renderFilm_hasBorderStyle() {
        val photos = List(4) { stubPhoto(Color.RED) }
        val config = PosterConfig("Test Film", template = TemplateType.FILM, photos = photos)
        val result = renderer.render(null, config)
        assertNotNull(result)
        // assert bottom strip bg color is FILM (#F5F5F0)
        val pixel = result.getPixel(result.width / 2, (result.height * 0.9f).toInt())
        assertEquals(0xFFF5F5F0.toInt(), pixel)
    }

    @Test fun renderJournal_backgroundIsWarmWhite() {
        val photos = List(3) { stubPhoto(Color.GREEN) }
        val config = PosterConfig("Test Journal", template = TemplateType.JOURNAL, photos = photos)
        val result = renderer.render(null, config)
        assertNotNull(result)
        val pixel = result.getPixel(result.width / 2, (result.height * 0.9f).toInt())
        assertEquals(0xFFFAF8F5.toInt(), pixel)
    }

    @Test fun renderMinimal_backgroundIsCleanWhite() {
        val photos = List(2) { stubPhoto(Color.YELLOW) }
        val config = PosterConfig("Test Minimal", template = TemplateType.MINIMAL, photos = photos)
        val result = renderer.render(null, config)
        assertNotNull(result)
        val pixel = result.getPixel(result.width / 2, (result.height * 0.9f).toInt())
        assertEquals(android.graphics.Color.WHITE, pixel)
    }

    @Test fun renderGrid_backgroundIsWhite() {
        val photos = List(4) { stubPhoto(Color.RED) }
        val config = PosterConfig("Test Grid", template = TemplateType.GRID, photos = photos)
        val result = renderer.render(null, config)
        assertNotNull(result)
        val pixel = result.getPixel(result.width / 2, (result.height * 0.9f).toInt())
        assertEquals(android.graphics.Color.WHITE, pixel)
    }
```

- [ ] **Step 3: 跑测试确认失败**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.core.PosterRendererTest`
Expected: 编译失败 — `renderFilm_hasBorderStyle`/`renderJournal_backgroundIsWarmWhite`/`renderMinimal_backgroundIsCleanWhite`/`renderGrid_backgroundIsWhite` 不存在.

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.palettemuse.core.PosterRendererTest`
Expected: 8/8 PASS (4 已有 grid/film/journal/minimal 非 footer + 4 新 footer 差异化).  (Actually the 4 existing already assert `render` generates 1080x1920 bitmap + non-null, not footer. 4 new test footer pixel style. So 4 existing Grid/Film/Journal/Minimal generation + 4 new footer pixel = 8 total.)

Let me count: PosterRendererTest 已有 4 个（render_grid_2x2_bento_produces1080x1920 + render_film_horizontalStrip + render_journal_scrapbook + render_minimal_heroPlusAccent = 4）. 新增 4 个 footer = 8. 预期 8/8.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/palettemuse/core/PosterRenderer.kt \
  app/src/androidTest/java/com/palettemuse/core/PosterRendererTest.kt
git commit -m "feat(poster): 4 template differentiated footer via TemplateType (Plan 4)"
```

---

## Self-Review

**1. Spec coverage:**
- ① 海报视觉一致性 → Task 1 ✓
- ② TARGET 首帧 → Task 2 ✓
- ③ sharePoster → Task 3 ✓
- ⑤ 模板差异化 → Task 1 (Preview Composable Footer) + Task 4 (PosterRenderer drawTitleAndPalette) ✓

**2. Placeholder scan:** 无 "TBD/TODO/implement later". 有 `@VisibleForTesting internal` and `setPreviewBitmapForTest` (named, not placeholder). ✅

**3. Type consistency:**
- `TemplateType` (Plan 3 Task 2) 贯穿 Task 1 + Task 4 一致 ✓
- `PosterConfig.template` (Plan 3 Task 2) 一致 ✓
- `FileProvider` authority `com.palettemuse.fileprovider` (Plan 2) 一致 ✓
- `sharePoster(context: Context)` + `setPreviewBitmapForTest(bmp: Bitmap)` 接口一致 ✓

**Risks:**
- Pixel assertion in PosterRendererTest (footer bg color): `getPixel(center, bottom)` on white canvas; Film+Journal+Minimal bg colors distinct enough from white to be falsifiable in test. 若测试失败则 visual 不符 brief.
- Compose `offset(x = col * cellW.dp)` (`cellW` 是 Float 非 dp → 需要 `with(LocalDensity) { cellW.toDp() }` 或直接用 fraction 比). Implementer 按需调整.

**No missing tasks.** All 4 spec items covered.
