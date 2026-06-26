### Task 5.2: ExportScreen + ExportViewModel

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt`
- Create: `app/src/main/java/com/palettemuse/ui/export/ExportViewModel.kt`

- [ ] **Step 1: 实现 ExportViewModel**

`app/src/main/java/com/palettemuse/ui/export/ExportViewModel.kt`：

```kotlin
package com.palettemuse.ui.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val previewBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val exportSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val posterRenderer: PosterRenderer
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        generatePreview()
    }

    private fun generatePreview() {
        viewModelScope.launch {
            try {
                val project = projectRepository.getProject(projectId)
                val palettes = projectRepository.getPalettesForProject(projectId)

                if (project == null) {
                    _uiState.value = ExportUiState(isLoading = false, error = "项目不存在")
                    return@launch
                }

                val photo = BitmapFactory.decodeFile(project.imagePath) ?: run {
                    _uiState.value = ExportUiState(isLoading = false, error = "图片加载失败")
                    return@launch
                }

                val config = PosterRenderer.PosterConfig(
                    title = project.title.ifEmpty { "Moodboard Color Harmony" },
                    primaryColor = palettes.firstOrNull()?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.GRAY }
                    } ?: Color.GRAY,
                    secondaryColor = palettes.getOrNull(1)?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.LTGRAY }
                    } ?: Color.LTGRAY,
                    accentColor = palettes.getOrNull(2)?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.DKGRAY }
                    } ?: Color.DKGRAY
                )

                val preview = posterRenderer.render(photo, config)
                _uiState.value = ExportUiState(previewBitmap = preview, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = ExportUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun savePoster(context: Context) {
        viewModelScope.launch {
            val bitmap = _uiState.value.previewBitmap ?: return@launch
            val uri = posterRenderer.saveToGallery(context, bitmap)
            _uiState.value = _uiState.value.copy(exportSuccess = uri != null)
        }
    }
}
```

- [ ] **Step 2: 实现 ExportScreen**

`app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt`：

```kotlin
package com.palettemuse.ui.export

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moodboard 海报导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("生成失败: ${uiState.error}", color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Preview
                uiState.previewBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "海报预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Moodboard Color Harmony",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Share button
                OutlinedButton(
                    onClick = {
                        uiState.previewBitmap?.let { bitmap ->
                            val file = File(context.cacheDir, "poster_share.png")
                            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "共享海报"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("📤 共享", modifier = Modifier.padding(vertical = 4.dp), fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.savePoster(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB76E79))
                ) {
                    Text(
                        "💾 保存海报",
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontSize = 16.sp
                    )
                }

                if (uiState.exportSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ 已保存到相册",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: 添加 FileProvider 配置**

创建 `app/src/main/res/xml/file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="/" />
</paths>
```

并在 `AndroidManifest.xml` 的 `<application>` 内添加：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- [ ] **Step 4: 验证编译和完整导航流程**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add ExportScreen with poster preview, share, and save to gallery"
```

---

## Phase 6: 视觉打磨

