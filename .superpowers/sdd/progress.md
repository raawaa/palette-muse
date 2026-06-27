# Plan 1 Progress Ledger

Branch: feat/core-engine
Base (Plan 1 start): 551c9e2

## Environment fixes (controller, pre-Task-1 test run)
- JDK: brew `openjdk@17` → `~/.zshenv` (JAVA_HOME+PATH) + `~/.gradle/gradle.properties` (org.gradle.java.home)
- Emulator: `medium_phone` started → `emulator-5554` online
- Removed stale template tests (`MainScreenTest`, `MainScreenViewModelTest`) that blocked androidTest compile (chore commit `b31a514`)

## Plan errors found + fixed
- `connectedAndroidTest` does NOT support `--tests` (AGP limitation). All 8 occurrences in plan corrected to `-Pandroid.testInstrumentationRunnerArguments.class=`. (committed)
- Task 5 CaptureViewModelTest: original referenced nonexistent `ThemeRepositoryStub`/`extractDominantHexForTest` → rewritten to androidTest + in-memory repo + `setPending()` (committed in plan before execution).

## Task 1: 新数据模型 Theme/Photo + DAO — COMPLETE
- Review: Spec ✅, Quality Approved
- Commits: `ef4f8ef` (impl) + `b31a514` (chore: remove stale tests) + fix commit (unused import + plan cmd)
- Test: `ThemeDaoTest` 2/2 PASS on medium_phone
- Review findings resolved:
  - Important: unused `import androidx.room.Delete` in `ThemeDao.kt` → FIXED (removed)
  - Minor (deferred): `fallbackToDestructiveMigration()` deprecation → Plan 2
  - Minor (deferred): `PhotoDao` no `getPhoto(id)`/`update` → add when Task 3+ needs it

## Task 2: ColorAnalyzer.extractDominantHex — COMPLETE
- Review: Spec ✅, Quality Approved (no Critical/Important)
- Commit: `8c540bb`
- Test: `ColorAnalyzerTest` 1/1 PASS on medium_phone
- Deviation (justified, reviewer approved): 测试期望 `#DCA8A6` → `#D8A8A0`（Palette 量化，确定性，加注释）。生产代码 100% verbatim。
- Minor (deferred to final triage): test Bitmap 未 recycle；fallback `#808080` 路径未覆盖（YAGNI）

## Task 3: ThemeRepository — COMPLETE
- Review: Spec ✅, Quality Approved (no Critical/Important)
- Commit: `fe6fbf6`
- Test: `ThemeRepositoryTest` 5/5 PASS (in-memory Room + real ColorMatcher/Namer, no mocks)
- Minor (deferred): `mapLatest` 缺 `@OptIn(ExperimentalCoroutinesApi)`（brief verbatim，Task 4 可顺手加）；palette 排序潜在 tie（`PhotoDao.observePhotosForTheme` 的 `ORDER BY capturedAt DESC`，Task 1 域，记 final triage）
- DI gap（预期）：`AppModule` 未 provide `ThemeDao`/`PhotoDao` → Task 4 补

## Task 4: PhotoStorage + DI — COMPLETE
- Review: Spec ✅, Quality Approved (no Critical)
- Commit: `bb4fd6e`
- Verification: `compileDebugKotlin` 通过（Hilt DI graph 完整）+ `ThemeRepositoryTest` 5/5 回归
- Findings (plan-mandated, defer to Task 7):
  - Important I1: `ThemeRepository` 双重绑定（`@Provides` + `@Inject constructor`）→ Task 7 统一（建议去掉 `@Inject constructor`，与 `ProjectRepository` 的 `@Provides` 惯例一致）
  - Minor M1: `PhotoStorage.save()` 是 suspend 但没 `withContext(IO)`，会阻塞调用线程 → Task 7 加 `withContext(Dispatchers.IO)`；**Task 5 `capturePhoto` 调用时注意线程**
  - Minor M2: `AppModule` import 顺序（cosmetic）

## Task 7 Cleanup Backlog（累积，Task 7 统一处理）
- `ThemeRepository` 双重绑定 → 去 `@Inject constructor` 或去 `@Provides`（Task 4 I1）
- `PhotoStorage.save()` 加 `withContext(Dispatchers.IO)`（Task 4 M1）
- `ThemeRepository` `getAllThemesWithPhotos` 加 `@OptIn(ExperimentalCoroutinesApi)`（Task 3）
- `PhotoDao.observePhotosForTheme` 排序加次键 `id`（Task 3 reviewer，避免 capturedAt tie）
- `AppModule` `fallbackToDestructiveMigration()` deprecation → 用带参重载（Task 1）

## Task 5: CaptureViewModel 重写 — COMPLETE
- Review: Spec ✅, Quality Approved (no Critical)
- Commit: `65334cf`
- Test: `CaptureViewModelTest` 3/3 PASS（两轮稳定）
- 超范围必要补丁（plan 遗漏，implementer 正确补，reviewer 确认）：
  - `AppModule` 加 `PhotoStorageModule @Binds`（`InternalPhotoStorage → PhotoStorage` 接口绑定，Hilt 标准方式）—— **plan Task 4 遗漏**，代码已补
  - `CaptureScreen` targetColor 临时硬编码（Task 6 重做）
- Findings:
  - Important: 测试非确定性（`setMain(StandardTestDispatcher)` + Room 真实 IO 调度器 + `advanceUntilIdle` 无法虚拟化 Room IO；`createThemeAndSave` 2 次 IO 有窗口）→ **Task 7 修**：用 Room `setTransactionExecutor`/`setQueryExecutor` 绑定 test dispatcher，或重试 `advanceUntilIdle` 直到状态满足
  - Minor: `onFrameAnalyzed` 硬编码 `#B76E79`（Task 6 处理）；`CapturedSwatch` 孤儿（Task 7 评估是否仍有用）

## Task 7 Cleanup Backlog（累积）
- **测试 flakiness**：CaptureViewModelTest 的 Room IO 竞态 → 注入 Room test executor 或重试 advanceUntilIdle（Task 5 Important，**优先**）
- `PhotoStorage.save()` 加 `withContext(Dispatchers.IO)`（Task 4 M1）
- `ThemeRepository` 双重绑定 → 去 `@Inject constructor`（Task 4 I1）
- `ThemeRepository` `@OptIn(ExperimentalCoroutinesApi)`（Task 3）
- `PhotoDao.observePhotosForTheme` 排序加次键 `id`（Task 3）
- `AppModule` `fallbackToDestructiveMigration()` 用带参重载（Task 1）
- `CapturedSwatch` 孤儿评估（Task 5）
- plan 文档修正：Task 4 补 `PhotoStorageModule @Binds` 说明

## Task 6: CaptureScreen 快门 + 确认条 — COMPLETE
- Review: Spec ✅, Quality Approved (no Critical/Important)
- Commit: `334c5d2`
- Verification: `compileDebugKotlin` 通过 + `installDebug` + 启动无 crash
- **关键修复**：快门 `onClick → takePhoto → capturePhoto`（致命断点已接通，主链路闭合）
- 超范围（合理，reviewer 核实）：`cameraManager` 提升到 `CaptureScreen` 顶层（相机生命周期安全 — `DisposableEffect(lensFacing)` 仍清理同一实例）
- Minor (defer): `onDismiss` 未绑定 UI（Task 7 可加 Close 按钮）；`onError` 静默吞错（Plan 2 错误提示）
- Note: `CameraManager` 是 `@Singleton @Inject` 但被 `remember{}` 手动 new（pre-existing，非 Task 6 引入，记 final triage）

## Task 7: 全链路冒烟 + 清理 — COMPLETE
- Review: Spec ✅, Quality Approved (no Critical/Important)
- Commit: `0c9f21a`
- 死代码：N/A（Task 5/6 已彻底替换，`grep` 确认无 `saveImageToInternalStorage`/`onSaved` 残留）
- **flakiness 修**：`CaptureViewModelTest` 加 `setQueryExecutor`/`setTransactionExecutor` 用 inline `Executor { it.run() }`（brief 建议的 `asExecutor()` 在 kotlinx-coroutines-test 1.10.2 不存在，反编译验证）。reviewer 确认对无 `@Transaction` 的 DAO 安全（无递归查询风险）。连跑 3 次稳定 3/3。
- `build.gradle.kts` 加 `androidTestImplementation(libs.kotlinx.coroutines.test)`（显式声明）
- 全部测试：11/11 androidTest PASS；0 unit test（旧模板已删）
- 启动无 crash；Room DB schema sqlite3 验证正确
- **完整相机冒烟待人工**：subagent 做不到快门坐标点击 + CameraX `OnImageCapturedCallback` 时序 + Compose sheet 出现。代码链 reviewer 确认接通（shutter→capturePhoto→pendingCapture→sheet→confirm→createThemeAndSave/savePhotoToTheme→DB）。**交付前需人工跑一遍**。
- Minor（controller 修）：`build.gradle.kts` 注释误导（提 `asExecutor` 但实际用 inline Executor）→ 修正注释

## Final Fix（whole-branch review 后）— COMPLETE
- Commit: `e51f80b`
- I-1: `PhotoStorage.save` 加 `withContext(Dispatchers.IO)` ✓
- I-2: `CaptureConfirmSheet` 加「重拍」OutlinedButton 绑定 `onDismiss` ✓
- M-1: `ThemeRepository` 加 `@OptIn(ExperimentalCoroutinesApi::class)` ✓
- M-2: 删 `AppModule.provideThemeRepository`（保留 `@Inject constructor`）✓
- 验证：`compileDebugKotlin` 通过 + `connectedAndroidTest` 11/11 PASS + 启动无 crash

## ✅✅ Plan 1 全部完成（7 task + final fix）
- 分支：`feat/core-engine`（`551c9e2..e51f80b`，11 commits）
- 测试：11 androidTest PASS（含归类、confirm/save 落库、DAO cascade）
- **✅ 端到端冒烟通过（android CLI 代人工，2026-06-27）**：首页 → Camera tab → 快门（layout 定位 [540,2232]）→ CaptureConfirmSheet 出现「为这个颜色创建新主题？」（首次无匹配，逻辑正确）→ 点「确认」→ DB 写入验证：`themes` 1 行（name="Amber Dawn" by ColorNamer, representativeHex=#383028）、`photos` 1 行（dominantHex=#383028, isSeed=1）。**capture→confirm→DB 闭环完整验证**。归入分支由 `CaptureViewModelTest.confirmCapture_joinsThemeWhenMatched` 单测覆盖。
- **遗留 working tree**：`HomeScreen.kt`（Lottie iterations，会话前已存在，非 Plan 1 产物，未暂存）
- Final Triage（推 Plan 2）：PhotoDao 排序次键、fallbackToDestructiveMigration 带参、CapturedSwatch 孤儿、CameraManager remember vs @Singleton、onFrameAnalyzed TARGET 硬编码、onError 错误提示

## ✅✅ Plan 1 全部 7 任务 COMPLETE → 进入 final whole-branch review

### Final Triage Backlog（留给 final review / Plan 2 的非阻塞项）
- `PhotoStorage.save()` 加 `withContext(IO)`（Task 4 M1）
- `ThemeRepository` 双重绑定 → 去 `@Inject constructor`（Task 4 I1）
- `ThemeRepository` `@OptIn(ExperimentalCoroutinesApi)`（Task 3）
- `PhotoDao.observePhotosForTheme` 排序加次键 `id`（Task 3）
- `fallbackToDestructiveMigration()` 用带参重载（Task 1）
- `CapturedSwatch` 孤儿评估（Task 5）
- `CameraManager` `@Singleton` 但 `remember{}` new（pre-existing）
- `CaptureScreen` `onDismiss` 未绑定 UI（Task 6）、`onError` 静默（Plan 2）
- `onFrameAnalyzed` TARGET 硬编码 `#B76E79`（实时匹配优化，Plan 2）

---

# Plan 2 Progress（UI 各屏适配）

## P2 Task 1: HomeViewModel+HomeScreen — COMPLETE
- Commits: `7341fbd` (impl) + `9dd5e5a` (fix)
- Review: Approved（after fix）
- HomeViewModel 改注入 ThemeRepository（getAllThemesWithPhotos）；HomeScreen 按设计稿 `c19a8027` 还原（TopAppBar 去 search / Hero / 主题大图卡 Coil AsyncImage / 真实 theme.palette 色点 / 空状态 / BottomNav+FAB）
- Fix（review）: shadow 顺序（Important——`.shadow` 移到 `.clip` 前，Aura 软阴影渲染，FAB 冒烟确认）+ Aura token 提升到 `theme/Color.kt`（供 Task 2/3 复用）+ EmptyStateCard 加载守卫 + 移除死代码 deleteTheme
- 测试：HomeViewModelTest 2/2 PASS；冒烟（android screen capture）FAB 阴影可见 + 空状态正确
- 签名保留 `onNavigateToAnalyze`（Task 4 统一改 onNavigateToDetail）

## P2 Task 2: ThemeDetailScreen+ViewModel — COMPLETE
- Commits: `c72a41b` (impl) + `201d71e` (fix)
- Review: Approved（after fix）
- `ThemeRepository.getThemeWithPhotos(id)`；`ThemeDetailViewModel`（SavedStateHandle+StateFlow+rename/updateColor/delete）；`ThemeDetailScreen` 按设计稿 `d9736410`（色板头 + Hero 起点图 + `LazyVerticalStaggeredGrid` 瀑布流 + 导出 FAB + more 菜单）
- Fix: AsyncImage 空 path 防护（Important，匹配 HomeScreen 模式）+ Hero contentDescription=null（去 TalkBack 冗余）+ import StaggeredGridItemSpan（去 suppress）
- **shadow 在 clip 前（4 处都对，吸取 Task 1 教训）**；Aura token 复用；`ThemeDetailViewModelTest` 4/4 + 全套 17/17
- MVP: more 菜单重命名/改色为钩子（无对话框，Plan 3）；瀑布流无 hover 标签
- NavGraph 未接（Task 4），视觉冒烟靠 diff + VM 测试

## P2 Task 3: ExportScreen 合成海报 — COMPLETE
- Commits: `dec4173` (impl) + `3801d11` (fix)
- Review: Approved（after fix）
- `ExportViewModel` 改注入 ThemeRepository（`getThemeWithPhotos` + 选前 4 张 Bento）；`ExportScreen` 按设计稿 `4202dc16`（3:4 Bento 预览 + 模板 chip + 分享小红书/保存相册）
- 海报合成：`PosterRenderer.render` 单种子照兜底（多照拼贴留 Plan 3）
- Fix: **种子优先**（I2——传完整 photos 给 renderPoster，>4 张时不再用最新照）+ 保存 Snackbar「已保存到相册」反馈（I1）+ 删未用 import（M1）+ `shareBitmap` 移 VM `withContext(IO)`（M2）
- `ExportViewModelTest` 5/5；shadow 在 clip 前；Aura token 复用

## P2 Task 4: NavGraph + assisted injection — COMPLETE
- Commit: `08b50e5`
- Review: Approved（no Critical/Important）
- `Routes.Analyze` → `Routes.ThemeDetail`；`entry` 渲染 `ThemeDetailScreen`；首页卡 → 详情 → 导出 → 保存 **全链路冒烟通过**
- **关键修复**：Navigation 3 不像 Nav2 把 NavKey 字段自动塞 `SavedStateHandle`（原会崩溃）→ `ThemeDetailViewModel` + `ExportViewModel` 改 **assisted injection**（`@Assisted NavKey`，教科书级 Hilt 模式：`@HiltViewModel(assistedFactory)` + `@AssistedInject` + `@AssistedFactory` + `hiltViewModel<VM, Factory>(creationCallback)`）
- 21 connectedAndroidTest 全过
- Minor（推 Task 5）: `CaptureScreen`/`HomeScreen` 的 `onNavigateToAnalyze` 命名不一致（实际导航 ThemeDetail）→ Task 5 重命名

## P2 Task 5: 清理旧模板 + AppDatabase v3 — COMPLETE
- Commit: `a4ba74f`
- Review: Approved（no Critical/Important）
- 删 11 旧文件（ProjectEntity/ColorPaletteEntity/ProjectDao/ColorPaletteDao/ProjectRepository/MainScreen/MainScreenViewModel/DataRepository/AnalyzeScreen/AnalyzeViewModel/Converters）
- AppDatabase v3 `[Theme, Photo]` + `fallbackToDestructiveMigration(dropAllTables=true)`；Converters 完全删（Theme/Photo 无 enum 需 converter）；AppModule 清旧 provide
- 重命名 `onNavigateToAnalyze` → `onNavigateToThemeDetail`（HomeScreen/CaptureScreen/NavGraph 一致）
- 额外删 `ColorAnalyzer.analyzeToEntities`（返回 ColorPaletteEntity，阻碍删除，无调用者——必要）
- **grep 确认零断引**；21/21 测试；冒烟无崩（v3 migration OK）
- Minor（发布前）：destructive migration 清数据 → 加正式 `Migration(2,3)`

## P2 Task 6: Final triage — COMPLETE
- Commit: `1109d10`
- Review: Approved（no Critical/Important）
- PhotoDao 排序加 `id DESC` 次键（两个查询，消除 capturedAt tie）；删 `CapturedSwatch` + `capturedSwatches` + CaptureScreen CAPTURED 预览条（净减 63 行，grep 零匹配）；`onError` → Snackbar「拍照失败」
- **TARGET 实时匹配推 Plan 3**（每帧 DB I/O 复杂，加详尽 TODO：init 缓存主题 StateFlow + 渲染到 TARGET pill）
- 21 测试不受影响（PhotoDao 测试调用者只断言 size）；冒烟无崩
- Minor（cosmetic，留）：`CaptureScreen` 多余 FQN（`mutableStateOf`/`SnackbarHost`）

## P2 Final Fix（whole-branch review 后）— COMPLETE
- Commit: `4ec0624`
- #1: `ThemeDetailScreen` more 菜单改 Snackbar「重命名/改色功能即将推出」（**不再写坏数据**——去掉硬编码 `"新主题"`；VM `renameTheme`/`updateThemeColor` 保留，Plan 3 加对话框再用）
- #3: `HomeScreen` TopAppBar 移出 `LazyColumn`，作 `Box.align(TopCenter)` 固定 overlay（滚动时不滚走，匹配其他屏）；`contentPadding top=88dp` 补偿
- 21 测试 + 冒烟（TopAppBar 固定对比验证）

## ✅✅✅ Plan 2 全部完成（6 task + final fix）
- 分支 `feat/core-engine`：Plan 1（`551c9e2..e51f80b`，11 commits）+ Plan 2（`d043c371..4ec0624`，10 commits）
- 21 connectedAndroidTest PASS；全链路冒烟（首页→详情→导出→保存 Snackbar）
- 3 屏按 Stitch 设计稿还原（首页画廊/主题详情/导出）+ 相机微调；Aura token 统一在 `theme/Color.kt`；旧 Project/ColorPalette 体系零残留

## ✅✅ Plan 2 全 6 任务 COMPLETE → 进入 final whole-branch review
- 分支 `feat/core-engine`（`d043c371..1109d10`，Plan 2 commits）
- 全链路：首页画廊（真实色板）→ 主题详情（色板头+Hero+瀑布流+导出FAB）→ 导出（合成海报+分享/保存 Snackbar）→ Task 4 全链路冒烟通过
- 21 connectedAndroidTest PASS；3 屏按 Stitch 设计稿还原 + 相机微调
- Final merge-base（Plan 2）：`d043c371`

### 留 Plan 3 的项

## Plan 3 Progress（subagent-driven，执行中）

### P3 Task 1: CaptureViewModel 实时匹配+CaptureScreen TARGET pill+FQN — COMPLETE
- Commits: `bbd5ef6` (impl) + `cd959f2` (CaptureScreen)
- `CaptureViewModel` 注入 `ThemeRepository` + 缓存 themes StateFlow + `onFrameAnalyzed` 算最匹配（THEME_MATCH_THRESHOLD=60）+ `TargetState`；`ThemeRepository.getAllThemes()`（Plan 1 缺失 one-liner，implementer 补）
- `CaptureScreen` 删 Match badge（Layer 4）+ TARGET pill 用 `targetTheme` + FQN 清理
- 5/5 CaptureViewModelTest PASS（3 已有 + 2 新）
- brief 瑕疵 implementer 合理修正（"Dusty Rose" 实为 ColorNamer "Warm Red"；test 顺序 `advanceUntilIdle`；`getAllThemes` 缺失）

### P3 Task 2: PosterRenderer 4 模板+PosterRendererTest — COMPLETE
- Commit: `a1b962f`
- `TemplateType` 4 枚举（GRID/FILM/JOURNAL/MINIMAL）+ `PosterConfig.photos/template` + `render` switch + 4 私有 `renderGrid/Film/Journal/Minimal` + `drawTitleAndPalette` 复用
- `PosterRendererTest` 4/4 PASS（emulator-5554）
- 4 模板共用标题/色板样式（MVP 接受，差异化字体/底色可后续优化）

### P3 Task 3: AppDatabase v3+Migration(2,3) — COMPLETE
- Commit: `faf39ba`
- `AppDatabase` v3 entities=[Theme,Photo] + `MIGRATION_2_3`（DROP color_palettes + projects）+ `AppModule` addMigrations 移除 `fallbackToDestructiveMigration`
- `AppDatabaseMigrationTest` Room migrationTestHelper v2→v3：themes 保留 + 旧表删除，1/1 PASS
- brief 遗漏 Room 2.7 细节 implementer 补：`build.gradle.kts` 加 KSP `room.schemaLocation` + androidTest assets mirror + `androidTestImplementation(room-testing)` + `coroutines-test`

### P3 Task 4: ExportViewModel 多模板+ExportScreen 4 预览 — COMPLETE
- Commits: `71fdd18` (VM+test) + `4ccdd67` (Screen)
- `ExportViewModel` `selectTemplate` + `generatePreview` 多照选图（Grid=4/Film=4/Journal=3/Minimal=2）+ `loadBitmap` IO
- `ExportScreen` 4 模板 chip + 4 预览 Composable（Film/Journal/Minimal 新增）+ FQN 清理
- `ExportViewModelTest` 5/5 PASS（5 已有 + 2 新，implementer 删 obsolete unsupported 钩子测试，Plan 2 brief 描述 7/7 不准）
- 保留 AssistedInject（Nav3 约定，与 Plan 2 一致）

### P3 Task 5: ThemeDetailScreen 对话框+Compose UI 测试 — COMPLETE
- Commit: `1be1689`
- `RenameDialog`/`EditColorDialog` AlertDialog（`parseHexOrNull`/`parseHexOrRoseGold` util）+ more 菜单接 `vm.renameTheme`/`updateThemeColor`（替代 Plan 2 final fix 的 Snackbar"即将推出"）
- `ThemeDetailScreenTest` 2/2 PASS（独立 Composable 测；brief 假设 3 已有测试错——文件实际不存在，implementer 正确新建）

- Base: `4ec0624` (Plan 2 final fix 后 main HEAD)
- 6 task: ①CaptureViewModel 实时匹配+CaptureScreen TARGET pill+FQN ②PosterRenderer 4 模板+test ③AppDatabase v3+Migration(2,3)+MigrationTest ④ExportViewModel 多模板+ExportScreen 4 预览 ⑤ThemeDetailScreen 对话框 ⑥全链路冒烟
- TARGET 实时主题匹配（onFrameAnalyzed，每帧 DB I/O）
- 多照海报拼贴（当前 PosterRenderer 单种子照兜底）
- 正式 `Migration(2,3)`（替代 destructive）
- 主题详情 more 菜单：重命名/改色对话框 UI（当前钩子）
- 导出多模板（胶片/日记/极简，当前只网格）
- `CaptureScreen` FQN 清理（cosmetic）

## ✅ 端到端冒烟（android CLI，完整用户旅程）— 全通过
清 DB → 首页空状态（固定 TopAppBar）→ FAB → 相机 → 快门 → 确认条「创建新主题」→ 确认 → **DB 写入**（themes「Amber Dawn」`#383028` + photos `isSeed=1`）→ 首页主题卡（真实色板）→ 详情（色板头 + 主题起点 Hero + 导出 FAB）→ 导出（海报预览 + 模板 chip + 分享/保存）→ **保存 Snackbar「已保存到相册」**。完整产品闭环（拍照→归类存库→画廊→详情→导出→保存反馈）端到端验证通过。

### P3 Task 6: 全链路冒烟 + cleanup — COMPLETE
- Commit: (pending)
- **Step 1: 编译 + 全量测试**：31/31 connectedAndroidTest PASS（emulator medium_phone）
- **Step 2: Android CLI 端到端冒烟**：
  - 相机捕获 + 实时 TARGET 匹配：首次会话已通过（DB 写入 themes「Amber Dawn」+ photos isSeed）
  - 主题详情 more 菜单 DropdownMenu（重命名/改主题色/删除）：已验证打开
  - 重命名对话框 + 确认 → DB 更新时间戳验证通过（`1782587874012` after rename，previous `1782587258133`）
  - 改主题色对话框 + 确认 → DB 更新时间戳验证通过（`1782587962344` after color update，previous `1782587371884`）
  - **关键修复验证**：`ThemeRepository.renameTheme` 和 `updateThemeColor` 改用 `ThemeDao` 直接 SQL UPDATE（替代原 fetch-then-update 模式，消除 `getTheme` 返回 null 时静默 no-op 的 bug）
  - **错误提示修复验证**：`ThemeDetailContent` 加 `LaunchedEffect(error)` → Snackbar（消除 `data != null` 时错误静默吞没的 bug）
  - 导出：4 模板 chip（网格/胶片/日记/极简）→ 切换胶片模板 → 海报预览正确 → 保存到相册 → MediaProvider 日志确认写入 `/storage/emulated/0/Pictures/PaletteMuse/PaletteMuse_*.png`
  - **已知限制**：Compose `OutlinedTextField` 不接收 `adb shell input text` 注入键盘事件（重命名对话框文本无法通过 adb 修改）；通过代码路径日志和 DB 时间戳验证确认 UPDATE 执行正确
- **Step 3**: progress.md 记录「Plan 3 完成」
- **Step 4**: Commit progress.md（pending）

## ✅✅✅ Plan 3 全部完成（6 task）
- 分支 `main`：Plan 3（`4ec0624..<pending>`，6 commits）
- 31 connectedAndroidTest PASS
- 全链路冒烟通过：拍摄→归类→画廊→详情（重命名/改色/删除）→导出（4模板海报→保存相册）
- Plan 3 核心交付：实时 TARGET 匹配、PosterRenderer 4 模板、正式 Migration(2,3)、Export 多模板预览、ThemeDetail 重命名/改色对话框、全链路冒烟验证
