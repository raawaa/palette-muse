### Task 6.1: 设计系统主题化 + Glassmorphism + 自定义阴影

**Files:**
- Modify: `app/src/main/java/com/palettemuse/theme/Theme.kt` — 完整设计系统，含 glassmorphism 和阴影
- Modify: `app/src/main/java/com/palettemuse/theme/Color.kt` — 添加玻璃质感色
- Modify: 所有 Screen — 确保使用 Material 3 语义色

- [ ] **Step 1: 强化主题（确保所有屏幕使用 MaterialTheme.colorScheme 语义色）**

审核所有 Screen 中的硬编码颜色，替换为 `MaterialTheme.colorScheme.primary`、`MaterialTheme.colorScheme.surface` 等。确保：
- TopAppBar 使用 `MaterialTheme.colorScheme.surface`
- FAB 使用 `MaterialTheme.colorScheme.primary`
- 卡片使用 `MaterialTheme.colorScheme.surface` + `elevation`
- 背景使用 `MaterialTheme.colorScheme.background`

- [ ] **Step 2: 实现 Glassmorphism 浮层效果（对应 Stitch Floating Layer）**

在 `Theme.kt` 中添加 glassmorphism 辅助函数：

```kotlin
// Glassmorphism: 20px backdrop-blur + 60% white fill + 0.5px white border
val GlassBackground = Brush.verticalGradient(
    colors = listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.4f))
)

// 用于 TopAppBar / BottomBar / FAB 的 glassmorphic 背景
// Android 12+ 可使用 RenderEffect.createBlurEffect() 实现真实模糊
// 降级方案: 半透明白色 + 微妙渐变
```

应用到 TopAppBar、BottomBar、FAB 的 background 中，用 `Modifier.background(GlassBackground, shape)` 替代纯色背景。

- [ ] **Step 3: 实现自定义软阴影（对应 Stitch Shadow 规范）**

设计要求: 主色/中性色 5-8% 透明度 + blur 20-30px + 长偏移，"禁止黑色阴影"。

```kotlin
// 在 Theme.kt 中添加自定义阴影颜色
val AmbientShadowColor = Color(0xFFB76E79).copy(alpha = 0.06f)  // 主色 6% 透明度
val SpotShadowColor = Color(0xFFB76E79).copy(alpha = 0.08f)     // 主色 8% 透明度

// Card 使用:
Card(
    elevation = CardDefaults.cardElevation(
        defaultElevation = 4.dp,
        ambientShadowColor = AmbientShadowColor,
        spotShadowColor = SpotShadowColor
    )
)
```

- [ ] **Step 4: 使用 Dimens 全局圆角常量**

将所有 Screen 中的 `RoundedCornerShape(28.dp)` 硬编码替换为 `Dimens.pillShape` / `Dimens.cardCorner` 等常量引用，确保一致性。

- [ ] **Step 5: 添加边缘到边缘（Edge-to-Edge）适配**

确保 `MainActivity` 已有 `enableEdgeToEdge()`，且所有 Scaffold 正确使用 `contentWindowInsets` 参数（Navigation 3 自动处理）。

- [ ] **Step 6: 添加转场动画**

在 `NavGraph.kt` 中添加场景过渡动画：

```kotlin
ComposableScene(
    transitionIn = { it.slideInHorizontally { it } + it.fadeIn() },
    transitionOut = { it.slideOutHorizontally { -it / 3 } + it.fadeOut() }
) { }
```

- [ ] **Step 7: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "style: apply Stitch design system theming, glassmorphism, soft shadows, and Dimens constants"
```

---

## Phase 1 — 完整文件清单（汇总）

创建的项目文件：

```
app/src/main/java/com/palettemuse/
 ├── PaletteMuseApp.kt
 ├── MainActivity.kt
 ├── di/
 │   └── AppModule.kt
 ├── data/
 │   ├── model/
 │   │   ├── ProjectEntity.kt
 │   │   └── ColorPaletteEntity.kt
 │   ├── local/
 │   │   ├── AppDatabase.kt
 │   │   ├── Converters.kt
 │   │   ├── ProjectDao.kt
 │   │   └── ColorPaletteDao.kt
 │   └── repository/
 │       └── ProjectRepository.kt
 ├── ui/
 │   ├── navigation/
 │   │   └── NavGraph.kt
 │   ├── home/
 │   │   └── HomeScreen.kt (占位)
 │   ├── capture/
 │   │   └── CaptureScreen.kt (占位)
 │   ├── analyze/
 │   │   └── AnalyzeScreen.kt (占位)
 │   ├── export/
 │   │   └── ExportScreen.kt (占位)
 │   └── theme/
 │       ├── Color.kt
 │       ├── Theme.kt
 │       └── Type.kt
 ├── camera/
 │   └── CameraManager.kt (占位/空)
 └── core/
     ├── ColorAnalyzer.kt (占位/空)
     ├── ColorNamer.kt (占位/空)
     ├── ColorMatcher.kt (占位/空)
     └── PosterRenderer.kt (占位/空)
```

修改的模板文件：
- `gradle/libs.versions.toml` — 添加 Hilt/Room/CameraX/Palette/Coil 依赖
- `app/build.gradle.kts` — 应用插件、添加依赖、更新 minSdk/package
- `settings.gradle.kts` — 注册新插件
- `app/src/main/AndroidManifest.xml` — 权限、Application class、FileProvider
- `app/src/main/res/values/strings.xml` — 字符串资源
- `app/src/main/res/values/themes.xml` — App 主题
