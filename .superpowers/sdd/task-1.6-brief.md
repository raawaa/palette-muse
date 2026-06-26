### Task 1.6: 创建 Navigation 和占位屏幕

**Files:**
- Modify: `app/src/main/java/com/palettemuse/MainActivity.kt` — 使用 Hilt + Compose
- Create: `app/src/main/java/com/palettemuse/ui/navigation/NavGraph.kt`
- Create: `app/src/main/java/com/palettemuse/ui/home/HomeScreen.kt` — 占位
- Create: `app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt` — 占位
- Create: `app/src/main/java/com/palettemuse/ui/analyze/AnalyzeScreen.kt` — 占位
- Create: `app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt` — 占位

- [ ] **Step 1: 更新 MainActivity**

`app/src/main/java/com/palettemuse/MainActivity.kt`：

```kotlin
package com.palettemuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.palettemuse.theme.PaletteMuseTheme
import com.palettemuse.ui.navigation.PaletteMuseNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaletteMuseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PaletteMuseNavGraph()
                }
            }
        }
    }
}
```

- [ ] **Step 2: 创建 Navigation graph**

`app/src/main/java/com/palettemuse/ui/navigation/NavGraph.kt`：

```kotlin
package com.palettemuse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.NavGraph
import androidx.navigation3.NavHost
import androidx.navigation3.compose.ComposableScene
import androidx.navigation3.compose.rememberViewModelStoreNavEntryDecorator
import com.palettemuse.ui.analyze.AnalyzeScreen
import com.palettemuse.ui.capture.CaptureScreen
import com.palettemuse.ui.export.ExportScreen
import com.palettemuse.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val ANALYZE = "analyze/{projectId}"
    const val EXPORT = "export/{projectId}"

    fun analyze(projectId: String) = "analyze/$projectId"
    fun export(projectId: String) = "export/$projectId"
}

@Composable
fun PaletteMuseNavGraph() {
    NavHost(
        startDestination = Routes.HOME,
        entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator())
    ) {
        scene(Routes.HOME) {
            HomeScreen(
                onNavigateToCapture = { navigate(Routes.CAPTURE) },
                onNavigateToAnalyze = { projectId -> navigate(Routes.analyze(projectId)) }
            )
        }
        scene(Routes.CAPTURE) {
            CaptureScreen(
                onNavigateToAnalyze = { projectId ->
                    navigate(Routes.analyze(projectId)) { popUpTo(Routes.HOME) }
                },
                onBack = { popBackStack() }
            )
        }
        scene(Routes.ANALYZE) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@scene
            AnalyzeScreen(
                projectId = projectId,
                onNavigateToExport = { navigate(Routes.export(projectId)) },
                onBack = { popBackStack() }
            )
        }
        scene(Routes.EXPORT) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@scene
            ExportScreen(
                projectId = projectId,
                onBack = { popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 3: 创建占位屏幕**

`app/src/main/java/com/palettemuse/ui/home/HomeScreen.kt`：

```kotlin
package com.palettemuse.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen(
    onNavigateToCapture: () -> Unit,
    onNavigateToAnalyze: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home - 作品画廊")
    }
}
```

`app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt`：

```kotlin
package com.palettemuse.ui.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun CaptureScreen(
    onNavigateToAnalyze: (String) -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Capture - 相机取色")
    }
}
```

`app/src/main/java/com/palettemuse/ui/analyze/AnalyzeScreen.kt`：

```kotlin
package com.palettemuse.ui.analyze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AnalyzeScreen(
    projectId: String,
    onNavigateToExport: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Analyze - 穿搭分析 ($projectId)")
    }
}
```

`app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt`：

```kotlin
package com.palettemuse.ui.export

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Export - 海报导出 ($projectId)")
    }
}
```

- [ ] **Step 4: 删除旧的 Navigation.kt 和 NavigationKeys.kt（如果存在）**

```bash
rm -f app/src/main/java/com/palettemuse/Navigation.kt
rm -f app/src/main/java/com/palettemuse/NavigationKeys.kt
```

- [ ] **Step 5: 更新 string resources**

`app/src/main/res/values/strings.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Palette Muse</string>
    <string name="home_title">我的作品集</string>
    <string name="capture_title">色彩捕捉</string>
    <string name="analyze_title">穿搭色彩分析</string>
    <string name="export_title">海报导出</string>
    <string name="empty_gallery">拍摄你的第一个色彩灵感</string>
    <string name="share">共享</string>
    <string name="save_poster">保存海报</string>
</resources>
```

- [ ] **Step 6: 验证编译和导航**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add navigation graph with placeholder screens"
```

---

## Phase 2: 核心色彩引擎

