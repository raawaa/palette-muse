package com.palettemuse.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.palettemuse.camera.CameraManager
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.RoseGold

@Composable
fun CaptureScreen(
    onNavigateToAnalyze: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraManager = remember { CameraManager() }
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFCF9F8))) {
        // === Layer 1: Camera viewfinder ===
        if (hasCameraPermission) {
            CameraPreview(
                cameraManager = cameraManager,
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

        // === Layer 2: Viewfinder crosshair + pulsing ring ===
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Outer dim overlay (simulated via crosshair box-shadow equivalent)
            CrosshairView()
        }

        // === Layer 3: Top glass bar — close / target pill / flash ===
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).padding(horizontal = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                GlassCircleButton(
                    icon = { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1C1B1B)) },
                    onClick = onBack
                )

                // Target color pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(9999.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = "TARGET",
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF524345)
                            )
                            Text(
                                text = "Rose Gold",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoseGold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                .background(Color(android.graphics.Color.parseColor("#B76E79")))
                        )
                    }
                }

                // Flash button
                GlassCircleButton(
                    icon = { Icon(Icons.Default.FlashOn, contentDescription = "Flash", tint = Color(0xFF1C1B1B)) },
                    onClick = {}
                )
            }
        }

        // === Layer 4: Live match percentage ===
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset(y = (-80).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.6f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = RoseGold, modifier = Modifier.size(20.dp))
                    Text(
                        text = "${uiState.matchPercentage}% Match",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseGold
                    )
                }
            }
        }

        // === Layer 5: Bottom gradient overlay ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFCF9F8).copy(alpha = 0.5f),
                            Color(0xFFFCF9F8)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // === Layer 6: Bottom controls ===
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            // Captured gallery
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CAPTURED (${uiState.capturedSwatches.size})",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF524345)
                    )
                    Text(
                        text = "View All",
                        fontSize = 14.sp,
                        color = RoseGold,
                        modifier = Modifier.clickable { }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.capturedSwatches.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(android.graphics.Color.parseColor(swatch.hexColor)))
                                .border(
                                    0.5.dp,
                                    Color(0xFFD7C1C3).copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.8f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${swatch.matchPercentage}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoseGold
                                )
                            }
                        }
                    }

                    // Add placeholder
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                2.dp,
                                Color(0xFFD7C1C3).copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFFD7C1C3), modifier = Modifier.size(32.dp))
                    }
                }
            }

            // Shutter controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircleButton(
                    icon = { Icon(Icons.Default.FlipCameraIos, contentDescription = "Flip", tint = Color(0xFF1C1B1B)) },
                    onClick = { viewModel.flipCamera() }
                )

                // Main shutter button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(8.dp)
                        .clickable {
                            cameraManager.takePhoto(
                                context,
                                onPhotoTaken = { bmp -> viewModel.capturePhoto(bmp) },
                                onError = { /* TODO Plan 2: 错误提示 */ }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFA6606B),
                                        Color(0xFFFFB2BC)
                                    )
                                )
                            )
                    )
                }

                GlassCircleButton(
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Tune", tint = Color(0xFF1C1B1B)) },
                    onClick = {}
                )
            }
        }

        // === Layer 7: Post-capture confirmation sheet ===
        uiState.pendingCapture?.let { pending ->
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                CaptureConfirmSheet(
                    pending = pending,
                    onConfirm = viewModel::confirmCapture,
                    onSaveAsNew = viewModel::saveAsNewTheme,
                    onDismiss = viewModel::dismissPending
                )
            }
        }
    }
}

@Composable
private fun GlassCircleButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun CrosshairView() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Canvas(modifier = Modifier.size(140.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2

        // Outer dim overlay
        drawCircle(
            color = Color.Black.copy(alpha = 0.1f),
            radius = size.width / 2
        )

        // Circle outline
        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = size.width / 2 - 10f,
            style = Stroke(width = 2f)
        )

        // Pulsing ring
        drawCircle(
            color = RoseGold.copy(alpha = pulseAlpha),
            radius = size.width / 2 - 8f,
            style = Stroke(width = 3f)
        )

        // Crosshair horizontal
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = androidx.compose.ui.geometry.Offset(cx - 60f, cy),
            end = androidx.compose.ui.geometry.Offset(cx + 60f, cy),
            strokeWidth = 1f
        )

        // Crosshair vertical
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = androidx.compose.ui.geometry.Offset(cx, cy - 60f),
            end = androidx.compose.ui.geometry.Offset(cx, cy + 60f),
            strokeWidth = 1f
        )
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

@Composable
fun CaptureConfirmSheet(
    pending: PendingCapture,
    onConfirm: () -> Unit,
    onSaveAsNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val themeName = pending.matchedTheme?.name ?: "新主题"
    val action = if (pending.matchedTheme != null) "归入【$themeName】？" else "为这个颜色创建新主题？"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFCF9F8).copy(alpha = 0.9f))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(action, color = Color(0xFF8A4853), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A4853))
                ) { Text("确认", color = Color.White) }
                OutlinedButton(onClick = onSaveAsNew, modifier = Modifier.weight(1f)) { Text("另起新主题") }
            }
        }
    }
}
