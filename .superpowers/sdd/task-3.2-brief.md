### Task 3.2: CaptureScreen UI + ViewModel

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt`
- Create: `app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt`

- [ ] **Step 1: 实现 CaptureViewModel**

`app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt`：

```kotlin
package com.palettemuse.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.ColorRole
import com.palettemuse.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

data class CaptureUiState(
    val matchPercentage: Int = 0,
    val targetColor: String = "#B76E79",
    val targetColorName: String = "Rose Gold",
    val capturedSwatches: List<CapturedSwatch> = emptyList(),
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isAnalyzing: Boolean = false
)

data class CapturedSwatch(
    val hexColor: String,
    val semanticName: String,
    val matchPercentage: Int
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val colorAnalyzer: ColorAnalyzer,
    private val colorNamer: ColorNamer,
    private val colorMatcher: ColorMatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    // 由 CameraManager 每帧调用
    fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
        val sampleHex = colorMatcher.extractCenterAverageColor(pixels, width, height)
        val match = colorMatcher.matchPercentage(_uiState.value.targetColor, sampleHex)
        _uiState.value = _uiState.value.copy(matchPercentage = match)
    }

    fun capturePhoto(photoBitmap: Bitmap, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            // 保存图片到 App 内部存储
            val imagePath = saveImageToInternalStorage(photoBitmap)

            // 分析颜色
            val result = colorAnalyzer.analyze(photoBitmap)
            val targetMatch = colorMatcher.matchPercentage(
                _uiState.value.targetColor,
                result.primaryHex ?: "#808080"
            )

            val projectId = UUID.randomUUID().toString()
            val palettes = listOfNotNull(
                result.primaryHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.PRIMARY,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it),
                        matchPercentage = targetMatch
                    )
                },
                result.secondaryHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.SECONDARY,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it)
                    )
                },
                result.accentHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.ACCENT,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it)
                    )
                }
            )

            // 添加到临时色样列表
            val primaryHex = result.primaryHex ?: "#808080"
            val swatch = CapturedSwatch(
                hexColor = primaryHex,
                semanticName = colorNamer.nameColor(primaryHex),
                matchPercentage = targetMatch
            )
            _uiState.value = _uiState.value.copy(
                capturedSwatches = _uiState.value.capturedSwatches + swatch,
                isAnalyzing = false
            )

            onSaved(projectId)
        }
    }

    fun flipCamera() {
        _uiState.value = _uiState.value.copy(
            lensFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String {
        val file = File(context.filesDir, "captures")
        file.mkdirs()
        val imageFile = File(file, "capture_${System.currentTimeMillis()}.jpg")
        FileOutputStream(imageFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        return imageFile.absolutePath
    }
}
```

- [ ] **Step 2: 实现 CaptureScreen**

`app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt`：

```kotlin
package com.palettemuse.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.palettemuse.camera.CameraManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onNavigateToAnalyze: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "目标: ${uiState.targetColorName}",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { /* 拍摄 - 通过 CameraManager 触发 */ },
                shape = CircleShape
            ) {
                if (uiState.isAnalyzing) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text("📸", fontSize = 28.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        cameraManager = remember { CameraManager() },
                        lensFacing = uiState.lensFacing,
                        onFrameAnalyzed = { pixels, width, height ->
                            viewModel.onFrameAnalyzed(pixels, width, height)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("需要相机权限", color = Color.Gray)
                    }
                }

                // Match percentage overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${uiState.matchPercentage}% MATCH",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Camera controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* tune */ }) {
                        Icon(Icons.Default.Tune, "微调", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(48.dp))

                    IconButton(onClick = { viewModel.flipCamera() }) {
                        Icon(Icons.Default.Cameraswitch, "翻转", tint = Color.White)
                    }
                }
            }

            // Captured swatches
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "已捕捉 (${uiState.capturedSwatches.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.capturedSwatches.forEach { swatch ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(swatch.hexColor)))
                                    .border(2.dp, Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${swatch.matchPercentage}%",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    cameraManager: CameraManager,
    lensFacing: Int,
    onFrameAnalyzed: (IntArray, Int, Int) -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(lensFacing) {
        onDispose { cameraManager.cleanup() }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                cameraManager.startCamera(
                    context = ctx,
                    lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current,
                    previewView = previewView,
                    lensFacing = lensFacing,
                    frameAnalyzer = object : CameraManager.FrameAnalyzer {
                        override fun analyze(pixels: IntArray, width: Int, height: Int) {
                            onFrameAnalyzed(pixels, width, height)
                        }
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add CaptureScreen with camera preview, real-time matching, and color capture"
```

---

## Phase 4: 数据分析页

