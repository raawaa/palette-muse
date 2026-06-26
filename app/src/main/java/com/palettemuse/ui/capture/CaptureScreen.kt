package com.palettemuse.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.palettemuse.camera.CameraManager
import com.palettemuse.theme.Dimens

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
    // Lifted camera manager reference so the FAB can access it for takePhoto
    var cameraManager by remember { mutableStateOf(CameraManager()) }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White.copy(alpha = 0.6f)
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = {
                    cameraManager.takePhoto(
                        context = context,
                        onPhotoTaken = { bitmap ->
                            viewModel.capturePhoto(bitmap) { projectId ->
                                onNavigateToAnalyze(projectId)
                            }
                        },
                        onError = { /* todo: show error snackbar */ }
                    )
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                if (uiState.isAnalyzing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
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
                    // key() ensures CameraPreview is fully torn down and recreated
                    // when lens facing changes, so a new CameraManager starts with
                    // the correct facing
                    key(uiState.lensFacing) {
                        val cm = remember { CameraManager() }
                        // Sync the composable-scoped manager to the lift variable
                        SideEffect { cameraManager = cm }
                        CameraPreview(
                            cameraManager = cm,
                            lensFacing = uiState.lensFacing,
                            onFrameAnalyzed = { pixels, width, height ->
                                viewModel.onFrameAnalyzed(pixels, width, height)
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("需要相机权限", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Match percentage overlay with glassmorphism
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Dimens.stackMd)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            RoundedCornerShape(Dimens.chipCorner)
                        )
                        .padding(horizontal = Dimens.stackMd, vertical = Dimens.stackSm)
                ) {
                    Text(
                        text = "${uiState.matchPercentage}% MATCH",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Camera controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Dimens.stackMd)
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
                    .padding(Dimens.stackMd)
            ) {
                Text(
                    text = "已捕捉 (${uiState.capturedSwatches.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.stackSm))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gutter)
                ) {
                    uiState.capturedSwatches.forEach { swatch ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(swatch.hexColor)))
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${swatch.matchPercentage}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lensFacing) {
        onDispose { cameraManager.cleanup() }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                cameraManager.startCamera(
                    context = ctx,
                    lifecycleOwner = lifecycleOwner,
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
