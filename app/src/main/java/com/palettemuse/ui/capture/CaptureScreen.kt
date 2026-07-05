package com.palettemuse.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.palettemuse.camera.CameraManager
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.HuiwenMincho
import com.palettemuse.theme.RoseGold

@Composable
fun CaptureScreen(
    onNavigateToThemeDetail: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraManager = remember { CameraManager() }
    var hasCameraPermission by remember { mutableStateOf(false) }
    // One-shot flag for capture failure feedback (consumed by the Snackbar LaunchedEffect below).
    val captureError = remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var shutterTick by remember { mutableStateOf(0) }

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

    // Surface capture failure as a transient Snackbar ("拍照失败，请重试").
    LaunchedEffect(captureError.value) {
        if (captureError.value) {
            snackbarHostState.showSnackbar("拍照失败，请重试")
            captureError.value = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFCF9F8))) {
        // === Layer 1: Camera viewfinder ===
        if (hasCameraPermission) {
            CameraPreview(
                cameraManager = cameraManager,
                lensFacing = uiState.lensFacing,
                onFrameAnalyzed = { bitmap ->
                    viewModel.onFrameAnalyzed(bitmap)
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

                // Target color pill — shows the live target theme + match percentage.
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
                            val target = uiState.targetTheme
                            Text(
                                text = if (target.isFallback) AnnotatedString("未匹配到主题")
                                       else buildAnnotatedString {
                                           withStyle(SpanStyle(fontFamily = HuiwenMincho)) {
                                               append(target.name)
                                           }
                                           append(" ${target.matchPct}% Match")
                                       },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoseGold
                            )
                        }
                    }
                }

                // Flash button
                GlassCircleButton(
                    icon = { Icon(Icons.Default.FlashOn, contentDescription = "Flash", tint = Color(0xFF1C1B1B)) },
                    onClick = {}
                )
            }
        }

        // === Layer 4 (removed): standalone Live match percentage Box — the
        // TARGET pill above now carries the same info, so the redundant badge
        // (always showing "0% Match" before Plan 3) is dropped.

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

                // Main shutter button — haptic + scale + flash overlay are wired
                // inside ShutterButton / ShutterFlashOverlay (issue #31 / ADR-0018).
                ShutterButton(
                    onShutter = {
                        cameraManager.takePhoto(
                            onPhotoTaken = { bmp -> viewModel.capturePhoto(bmp) },
                            onError = {
                                // Surface capture failure to the user via Snackbar.
                                captureError.value = true
                            }
                        )
                        shutterTick++
                    }
                )

                GlassCircleButton(
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Tune", tint = Color(0xFF1C1B1B)) },
                    onClick = {}
                )
            }
        }

        // === Layer 7: Post-capture extraction animation ===
        // Sits above the camera layer while the capture is being analyzed.
        // Plays for ~1200ms, then resets extractionMode so the confirm
        // sheet below becomes visible. Two visually distinct modes:
        //   SUBJECT_LOCKED — glow ring + radial bloom from center
        //   FALLBACK — radial bloom only (no ring)
        ExtractionAnimation(
            mode = uiState.extractionMode,
            colorArgb = uiState.extractedColor,
            onAnimationEnd = viewModel::onExtractionAnimationEnd
        )

        // === Layer 8: Post-capture confirmation sheet ===
        // Hidden while the extraction animation plays (no overlap); fades in
        // once the animation completes and extractionMode resets to NONE.
        AnimatedVisibility(
            visible = uiState.extractionMode == ExtractionMode.NONE &&
                uiState.pendingCapture != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 250)),
            exit = fadeOut(animationSpec = tween(durationMillis = 250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            uiState.pendingCapture?.let { pending ->
                CaptureConfirmSheet(
                    pending = pending,
                    onConfirm = viewModel::confirmCapture,
                    onSaveAsNew = viewModel::saveAsNewTheme,
                    onDismiss = viewModel::dismissPending
                )
            }
        }

        // === Layer 9: Capture failure feedback (Snackbar) ===
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp) // clear the shutter controls
        )

        // === Layer 10: Full-screen shutter flash overlay (issue #31 / ADR-0018) ===
        // Sits last in the outer Box so its zIndex(10f) wins over every other
        // layer. Driven by [shutterTick]; each press increments and re-fires
        // the alpha ramp (0 → 0.8 in 20ms, 0.8 → 0 in 100ms).
        ShutterFlashOverlay(triggerKey = shutterTick)
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

/**
 * Test seam: the current press-driven scale factor of [ShutterButton], read via
 * semantics from instrumented tests in [ShutterFeedbackTest]. Values: `1f`
 * resting, animates toward `0.9f` while the shutter is pressed.
 */
internal val ShutterScaleSemanticsKey = SemanticsPropertyKey<Float>("ShutterScale")

/**
 * Test seam: the current alpha of the [ShutterFlashOverlay] white flash,
 * read via semantics from instrumented tests in [ShutterFeedbackTest].
 * Ranges 0f–0.8f across the 0ms→120ms shutter feedback envelope.
 */
internal val ShutterFlashAlphaSemanticsKey = SemanticsPropertyKey<Float>("ShutterFlashAlpha")

/**
 * Shutter button — extracted from [CaptureScreen] for testability.
 *
 * Issues the three simultaneous UX feedback signals on press (issue #31 /
 * ADR-0018):
 * 1. Scale-down (1f → 0.9f) via [animateFloatAsState] over `tween(80)`,
 *    auto-spring-back when the press releases.
 * 2. [HapticFeedbackType.LongPress] fired from the click callback — the same
 *    [performHaptic] lambda runs **before** [onShutter], so the haptic
 *    precedes `cameraManager.takePhoto(...)` regardless of camera latency.
 * 3. (Flash overlay lives in [ShutterFlashOverlay] — sibling, driven by a
 *    `triggerKey` counter that the caller bumps in [onShutter].)
 *
 * Test seams:
 * - [performHaptic] defaults to the real `LocalHapticFeedback` but is
 *   injectable so tests can count invocations.
 * - [interactionSource] defaults to a fresh [MutableInteractionSource] but is
 *   injectable so tests can `tryEmit(PressInteraction.Press(...))` directly
 *   without driving a touch gesture.
 * - The current scale value is published via [ShutterScaleSemanticsKey].
 */
@Composable
@androidx.annotation.VisibleForTesting
internal fun ShutterButton(
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
    performHaptic: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val hapticFeedback = LocalHapticFeedback.current
    val performHapticFinal: () -> Unit = performHaptic
        ?: { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "shutter_scale",
    )
    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .padding(8.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    performHapticFinal()
                    onShutter()
                }
            )
            .semantics { set(ShutterScaleSemanticsKey, scale) }
            .testTag("shutter_button"),
        contentAlignment = Alignment.Center,
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
}

/**
 * Full-screen white flash that fires on each shutter press (issue #31 /
 * ADR-0018). Sits at `zIndex(10f)` inside [CaptureScreen]'s outer Box so it
 * paints above every other layer.
 *
 * Animation envelope per press: alpha `0 → 0.8` over 20ms, then `0.8 → 0`
 * over 100ms — total ~120ms, matching the spec's "decays to ≤ 0.05 within
 * 120ms" budget. Triggered by [triggerKey] changing; a counter that the
 * caller increments inside the shutter callback keeps successive presses
 * independent.
 *
 * No `clickable` / `pointerInput` is attached, so the overlay does not
 * intercept touch — taps still fall through to the layers below (notably the
 * shutter button).
 */
@Composable
fun ShutterFlashOverlay(
    triggerKey: Int,
    modifier: Modifier = Modifier,
) {
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(triggerKey) {
        if (triggerKey > 0) {
            flashAlpha.snapTo(0f)
            flashAlpha.animateTo(0.8f, animationSpec = tween(durationMillis = 20))
            flashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 100))
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.White.copy(alpha = flashAlpha.value))
            .semantics { set(ShutterFlashAlphaSemanticsKey, flashAlpha.value) }
            .testTag("shutter_flash"),
    )
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
    onFrameAnalyzed: (Bitmap) -> Unit
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
                        override fun analyze(bitmap: Bitmap) {
                            onFrameAnalyzed(bitmap)
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
    val action: AnnotatedString = if (pending.matchedTheme != null) {
        buildAnnotatedString {
            append("归入【")
            withStyle(SpanStyle(fontFamily = HuiwenMincho)) {
                append(themeName)
            }
            append("】？")
        }
    } else {
        AnnotatedString("为这个颜色创建新主题？")
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFCF9F8).copy(alpha = 0.9f))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                    Text(text = action, color = Color(0xFF8A4853), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val retakeBorder = if (pending.isLowConfidence) {
                    BorderStroke(1.5.dp, Color(0xFF8A4853))
                } else {
                    null
                }
                OutlinedButton(
                    onClick = onDismiss,
                    border = retakeBorder
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "重拍",
                        tint = Color(0xFF8A4853),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("重拍", color = Color(0xFF8A4853))
                }
            }
            if (pending.isLowConfidence) {
                LowConfidenceHint(visible = true)
                Spacer(Modifier.height(12.dp))
            }
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

/**
 * Soft, single-line nudge shown on the capture confirm sheet when the algorithm
 * flagged the photo as low-confidence (issue #20 / ADR-0014). Stateless by
 * design so it can be Previewed and tested in isolation from `PendingCapture`,
 * the camera, and the analyzer pipeline.
 *
 * - `visible == true`  → one line of soft rose-gold text, no background,
 *                       no icon, no bold weight.
 * - `visible == false` → emits no text node (asserted via semantics tree in
 *                       [com.palettemuse.ui.capture.LowConfidenceHintTest]).
 *
 * The text color is `Color(0xFF8A4853)`, the same tone already used by the
 * confirm sheet's title; reusing it keeps the sheet's visual register
 * consistent (PRD: "no background pill, no icon, no bold weight").
 */
@Composable
@androidx.annotation.VisibleForTesting
internal fun LowConfidenceHint(visible: Boolean) {
    if (visible) {
        Text(
            text = "颜色不太明显，要重拍吗？",
            color = Color(0xFF8A4853),
            fontSize = 14.sp,
        )
    }
}

/**
 * Post-shutter extraction animation: a vivid color bloom that plays during the
 * shutter-to-confirm-sheet transition, covering inference latency.
 *
 * Two visibly distinct modes:
 * - [ExtractionMode.SUBJECT_LOCKED]: a full-viewport glow ring + expanding
 *   radial bloom of the extracted color, signaling a subject mask was applied.
 * - [ExtractionMode.FALLBACK]: a whole-frame radial bloom with NO ring,
 *   signaling the fallback whole-photo extraction was used.
 *
 * The animation runs in three serialized phases — expand → hold → contract
 * (~1100ms total) — then calls [onAnimationEnd] so the caller resets
 * [CaptureUiState.extractionMode] to [ExtractionMode.NONE], which fades in the
 * confirm sheet. No two phases animate the same property concurrently.
 */
@Composable
private fun ExtractionAnimation(
    mode: ExtractionMode,
    colorArgb: Int?,
    onAnimationEnd: () -> Unit,
) {
    if (mode == ExtractionMode.NONE) return

    val composeColor = if (colorArgb != null) Color(
        red = ((colorArgb shr 16) and 0xFF) / 255f,
        green = ((colorArgb shr 8) and 0xFF) / 255f,
        blue = (colorArgb and 0xFF) / 255f
    ) else Color(0xFF8A4853) // default rose-gold fallback

    val bloomProgress = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }
    val isSubjectLocked = mode == ExtractionMode.SUBJECT_LOCKED

    LaunchedEffect(mode) {
        bloomProgress.snapTo(0f)
        glowAlpha.snapTo(0f)
        // Phase 1 (expand): bloom 0→1; glow 0→0.75 in subject-locked mode.
        // Each Animatable has exactly one writer this phase.
        coroutineScope {
            launch { bloomProgress.animateTo(1f, tween(EXPAND_MS)) }
            if (isSubjectLocked) {
                launch { glowAlpha.animateTo(0.75f, tween(EXPAND_MS)) }
            }
        }
        // Phase 2 (hold): pause at peak so the color reads clearly.
        delay(HOLD_MS.toLong())
        // Phase 3 (contract): bloom 1→0; glow fades out. Previous phase's
        // coroutines have joined, so bloomProgress again has one writer.
        coroutineScope {
            launch { bloomProgress.animateTo(0f, tween(CONTRACT_MS)) }
            if (isSubjectLocked) {
                launch { glowAlpha.animateTo(0f, tween(CONTRACT_MS)) }
            }
        }
        onAnimationEnd()
    }

    // No dim overlay — the bloom itself is the visual (the previous black
    // alpha pulse read as "screen flashes dim"; issue #53).
    Box(
        modifier = Modifier.fillMaxSize().zIndex(9f),
        contentAlignment = Alignment.Center
    ) {
        // Glow ring — full-viewport scale, subject-locked only.
        if (isSubjectLocked) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                val base = minOf(size.width, size.height) * 0.35f
                val ringRadius = base * (0.85f + 0.15f * bloomProgress.value)
                // Outer soft glow
                drawCircle(
                    color = composeColor.copy(alpha = glowAlpha.value * 0.35f),
                    radius = ringRadius * 1.5f,
                )
                // Inner crisp ring
                drawCircle(
                    color = composeColor.copy(alpha = glowAlpha.value * 0.85f),
                    radius = ringRadius,
                    style = Stroke(width = 8f * (0.6f + 0.4f * bloomProgress.value))
                )
            }
        }

        // Vivid radial bloom — high alpha so the extracted color is clearly
        // visible against the camera preview.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width.coerceAtLeast(size.height) * 0.75f
            val currentRadius = maxRadius * bloomProgress.value
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        composeColor.copy(alpha = 0.85f * bloomProgress.value),
                        composeColor.copy(alpha = 0.4f * bloomProgress.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius.coerceAtLeast(1f)
                ),
                radius = currentRadius.coerceAtLeast(1f),
                center = center,
            )
        }
    }
}

/** Extraction-animation phase durations (ms). */
private const val EXPAND_MS = 450
private const val HOLD_MS = 300
private const val CONTRACT_MS = 350

@Preview(name = "LowConfidenceHint — visible", showBackground = true, backgroundColor = 0xFFFCF9F8)
@Composable
private fun LowConfidenceHintVisiblePreview() {
    LowConfidenceHint(visible = true)
}

@Preview(name = "LowConfidenceHint — hidden", showBackground = true, backgroundColor = 0xFFFCF9F8)
@Composable
private fun LowConfidenceHintHiddenPreview() {
    LowConfidenceHint(visible = false)
}
