package com.palettemuse.ui.export

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.OnSurface
import com.palettemuse.theme.OnSurfaceVariant
import com.palettemuse.theme.OutlineVariant
import com.palettemuse.theme.PearlWhite
import com.palettemuse.theme.PlayfairDisplay
import com.palettemuse.theme.PlusJakartaSans
import com.palettemuse.theme.PrimaryDesign
import com.palettemuse.theme.RoseGold
import com.palettemuse.theme.RoseGoldDark
import com.palettemuse.theme.SurfaceContainer
import com.palettemuse.theme.SurfaceLow
import com.palettemuse.theme.SurfaceWhite
import com.palettemuse.theme.glassmorphicBackground
import java.io.File

// Aura Aesthetic color tokens are imported from theme/Color.kt — do not redefine here.

/** Parses a hex string ("#RRGGBB") into a Compose Color, falling back to rose gold. */
private fun parseHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(RoseGold)

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface one-shot hints (e.g. unsupported template) as SnackBars.
    LaunchedEffect(uiState.unsupportedTemplateHint) {
        uiState.unsupportedTemplateHint?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeUnsupportedHint()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWhite)
    ) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryDesign)
                }
            }
            uiState.theme == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        uiState.error ?: "主题不存在",
                        fontFamily = PlusJakartaSans,
                        color = OnSurfaceVariant
                    )
                    Spacer(Modifier.height(Dimens.stackMd))
                    Text(
                        text = "返回",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDesign,
                        modifier = Modifier.clickable(onClick = onBack)
                    )
                }
            }
            else -> {
                ExportContent(
                    state = uiState,
                    onShare = {
                        uiState.posterBitmap?.let { bmp ->
                            shareBitmap(context, bmp)
                        }
                    },
                    onSave = { viewModel.savePoster(context) },
                    onSelectTemplate = viewModel::selectTemplate,
                    onBack = onBack
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 200.dp) // clear the glass bottom action panel
        )
    }
}

// ===================================================================
// Content — TopAppBar + scrollable poster preview + template chips
// ===================================================================

@Composable
private fun ExportContent(
    state: ExportUiState,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onSelectTemplate: (PosterTemplate) -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // ===== Scrollable main canvas =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 88.dp, bottom = 200.dp) // clearance for TopAppBar + bottom panel
                .padding(horizontal = Dimens.containerMargin),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Poster Preview (3:4 white card with Bento collage + footer) ----
            PosterPreviewCard(
                theme = state.theme!!,
                photos = state.selectedPhotos,
                palette = state.palette,
                representativeHex = state.theme.representativeHex,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Dimens.stackLg))

            // ---- Template selector chips (horizontal scroll) ----
            TemplateChips(
                selected = state.selectedTemplate,
                onSelect = onSelectTemplate
            )
        }

        // ===== TopAppBar (glassmorphic, sticky) =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphicBackground()
                .statusBarsPadding()
                .padding(horizontal = Dimens.containerMargin, vertical = Dimens.stackMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = OnSurfaceVariant)
            }
            Text(
                text = "PALETTE MUSE",
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = PrimaryDesign,
                letterSpacing = 0.2.sp * 10f
            )
            IconButton(onClick = { /* MVP: settings deferred */ }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "调整", tint = OnSurfaceVariant)
            }
        }

        // ===== Bottom Actions (glass panel) =====
        BottomActionPanel(
            onShare = onShare,
            onSave = onSave,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ===================================================================
// Poster Preview Card — 3:4 white card, Bento 2x2 collage + footer
// ===================================================================

@Composable
private fun PosterPreviewCard(
    theme: ThemeEntity,
    photos: List<PhotoEntity>,
    palette: List<String>,
    representativeHex: String,
    modifier: Modifier = Modifier
) {
    // Design: aspect-[3/4], white bg, rounded-xl (3rem ≈ 28dp), ambient shadow.
    // NOTE: shadow is applied BEFORE clip per the design spec so the soft
    // rose-gold ambient shadow is not clipped by the rounded corners.
    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(Dimens.cardCorner),
                ambientColor = RoseGold.copy(alpha = 0.06f),
                spotColor = RoseGold.copy(alpha = 0.10f)
            )
            .clip(RoundedCornerShape(Dimens.cardCorner))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Bento 2x2 collage (flex-1) ----
            BentoCollage(
                photos = photos,
                overlayHex = representativeHex,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // ---- Footer: title + subtitle + palette tag ----
            PosterFooter(theme = theme, palette = palette)
        }
    }
}

// ===================================================================
// Bento 2x2 collage — 4 AsyncImage tiles + representativeHex color overlay
// ===================================================================

@Composable
private fun BentoCollage(
    photos: List<PhotoEntity>,
    overlayHex: String,
    modifier: Modifier = Modifier
) {
    val overlay = parseHex(overlayHex)
    // Simulate the design's mix-blend-multiply + bg-primary/10 overlay:
    // a low-alpha tint of the theme's representative color unified across tiles.
    val overlayColor = overlay.copy(alpha = 0.10f)

    // Build exactly 4 slots; missing photos fall back to a tinted placeholder tile.
    val slots = (0 until 4).map { idx -> photos.getOrNull(idx) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.chunked(2).forEach { rowPair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPair.forEach { photo ->
                    BentoTile(
                        photo = photo,
                        overlayColor = overlayColor,
                        placeholderColor = overlay.copy(alpha = 0.05f),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoTile(
    photo: PhotoEntity?,
    overlayColor: Color,
    placeholderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.imageCorner))
            .background(placeholderColor)
    ) {
        if (photo != null && photo.imagePath.isNotBlank()) {
            AsyncImage(
                model = photo.imagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Unifying color overlay (mix-blend-multiply stand-in).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor)
            )
        }
    }
}

// ===================================================================
// Poster Footer — theme name + series label + palette swatch tag
// ===================================================================

@Composable
private fun PosterFooter(theme: ThemeEntity, palette: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.name,
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                color = OnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Palette Muse 合集",
                fontFamily = PlusJakartaSans,
                fontSize = 14.sp,
                color = OnSurfaceVariant
            )
        }
        PaletteTag(palette = palette)
    }
}

@Composable
private fun PaletteTag(palette: List<String>) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.fullRound))
            .background(SurfaceLow)
            .border(0.5.dp, OutlineVariant, RoundedCornerShape(Dimens.fullRound))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        palette.forEach { hex ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseHex(hex))
            )
        }
    }
}

// ===================================================================
// Template chips — Grid (active, RoseGold) / Film / Diary / Minimal
// MVP: only Grid is selectable; others surface a "coming soon" SnackBar.
// ===================================================================

@Composable
private fun TemplateChips(
    selected: PosterTemplate,
    onSelect: (PosterTemplate) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PosterTemplate.entries.forEach { template ->
            val isActive = template == selected
            TemplateChip(
                label = template.label,
                isActive = isActive,
                onClick = { onSelect(template) }
            )
        }
    }
}

@Composable
private fun TemplateChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isActive) PrimaryDesign else OutlineVariant
    val textColor = if (isActive) PrimaryDesign else OnSurfaceVariant
    val bgColor = if (isActive) PrimaryDesign.copy(alpha = 0.05f) else Color.Transparent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.fullRound))
            .border(1.dp, borderColor, RoundedCornerShape(Dimens.fullRound))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = textColor
        )
    }
}

// ===================================================================
// Bottom Action Panel (glass) — Share to Xiaohongshu + Save to gallery
// ===================================================================

@Composable
private fun BottomActionPanel(
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassmorphicBackground()
            .padding(horizontal = Dimens.containerMargin)
            .padding(top = Dimens.stackMd, bottom = 32.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Primary action — Rose Gold gradient pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(Dimens.fullRound),
                    ambientColor = RoseGold.copy(alpha = 0.10f),
                    spotColor = RoseGold.copy(alpha = 0.20f)
                )
                .clip(RoundedCornerShape(Dimens.fullRound))
                .background(
                    Brush.horizontalGradient(listOf(RoseGold, RoseGoldDark))
                )
                .clickable(onClick = onShare),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "分享到小红书",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = Color.White
            )
        }

        // Secondary action — outline pill with download icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(Dimens.fullRound))
                .border(1.dp, RoseGoldDark, RoundedCornerShape(Dimens.fullRound))
                .background(SurfaceWhite.copy(alpha = 0.5f))
                .clickable(onClick = onSave)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = PrimaryDesign,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "保存到相册",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = PrimaryDesign
            )
        }
    }
}

// ===================================================================
// Share helper — write Bitmap to cache + launch ACTION_SEND ShareSheet
// ===================================================================

private fun shareBitmap(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    val file = File(context.cacheDir, "poster_share.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享海报"))
}
