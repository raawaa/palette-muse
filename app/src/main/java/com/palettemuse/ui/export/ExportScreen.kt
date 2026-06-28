package com.palettemuse.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity
import com.palettemuse.data.repository.ThemeWithPhotos
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.OnSurface
import com.palettemuse.theme.OnSurfaceVariant
import com.palettemuse.theme.OutlineVariant
import com.palettemuse.theme.PlayfairDisplay
import com.palettemuse.theme.PlusJakartaSans
import com.palettemuse.theme.PosterBgFilm
import com.palettemuse.theme.PosterBgJournal
import com.palettemuse.theme.PosterBrown
import com.palettemuse.theme.PrimaryDesign
import com.palettemuse.theme.RoseGold
import com.palettemuse.theme.RoseGoldDark
import com.palettemuse.theme.SurfaceLow
import com.palettemuse.theme.SurfaceWhite
import com.palettemuse.theme.glassmorphicBackground

// Aura Aesthetic color tokens are imported from theme/Color.kt — do not redefine here.

/** Parses a hex string ("#RRGGBB") into a Compose Color, falling back to rose gold. */
private fun parseHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(RoseGold)

/** Human-readable Chinese label for each [PosterRenderer.TemplateType] (chip + a11y). */
private fun PosterRenderer.TemplateType.label(): String = when (this) {
    PosterRenderer.TemplateType.GRID -> "网格"
    PosterRenderer.TemplateType.FILM -> "胶片"
    PosterRenderer.TemplateType.JOURNAL -> "日记"
    PosterRenderer.TemplateType.MINIMAL -> "极简"
}

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface save-to-gallery success as a one-shot confirmation SnackBar.
    LaunchedEffect(uiState.exportSuccess) {
        if (uiState.exportSuccess) {
            snackbarHostState.showSnackbar("已保存到相册")
            viewModel.consumeExportSuccess()
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
            uiState.data == null -> {
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
                    data = uiState.data!!,
                    selectedTemplate = uiState.selectedTemplate,
                    onShare = { viewModel.sharePoster(context) },
                    onSave = { viewModel.savePoster() },
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
    data: ThemeWithPhotos,
    selectedTemplate: PosterRenderer.TemplateType,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onSelectTemplate: (PosterRenderer.TemplateType) -> Unit,
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
            // ---- Poster Preview Card (3:4, switches by template) ----
            PosterPreviewCard(
                theme = data.theme,
                photos = data.photos,
                swatches = data.swatches,
                template = selectedTemplate,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Dimens.stackLg))

            // ---- Template selector chips (horizontal scroll) ----
            TemplateChips(
                selected = selectedTemplate,
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
// Poster Preview Card — 3:4 white card, switches by template
// ===================================================================

@Composable
private fun PosterPreviewCard(
    theme: ThemeEntity,
    photos: List<PhotoEntity>,
    swatches: List<String>,
    template: PosterRenderer.TemplateType,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
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
    ) {
        val cardW = maxWidth
        val cardH = maxHeight

        // Photo layer — fills the full card
        when (template) {
            PosterRenderer.TemplateType.GRID ->
                PosterPreviewGrid(photos = photos, theme = theme, swatches = swatches, modifier = Modifier.fillMaxSize(), cardW = cardW, cardH = cardH)
            PosterRenderer.TemplateType.FILM ->
                PosterPreviewFilm(photos = photos, theme = theme, swatches = swatches, modifier = Modifier.fillMaxSize(), cardW = cardW, cardH = cardH)
            PosterRenderer.TemplateType.JOURNAL ->
                PosterPreviewJournal(photos = photos, theme = theme, swatches = swatches, modifier = Modifier.fillMaxSize(), cardW = cardW, cardH = cardH)
            PosterRenderer.TemplateType.MINIMAL ->
                PosterPreviewMinimal(photos = photos, theme = theme, swatches = swatches, modifier = Modifier.fillMaxSize(), cardW = cardW, cardH = cardH)
        }

        // Footer overlay — drawn on top of photos (matching Canvas drawTitleAndStrip)
        PosterFooter(
            theme = theme,
            swatches = swatches,
            template = template,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ===================================================================
// PosterPreviewGrid — 2x2 Bento collage + footer (4 photos)
// ===================================================================

@Composable
private fun PosterPreviewGrid(
    photos: List<PhotoEntity>,
    theme: ThemeEntity,
    swatches: List<String>,
    modifier: Modifier = Modifier,
    cardW: Dp = 0.dp,
    cardH: Dp = 0.dp
) {
    Column(modifier = modifier.padding(16.dp)) {
        BentoCollage(
            photos = photos,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

// ===================================================================
// PosterPreviewFilm — 顶部大照 + 下方 3 小照横排 + 黑/白边框 (4 photos)
// ===================================================================

@Composable
private fun PosterPreviewFilm(
    photos: List<PhotoEntity>,
    theme: ThemeEntity,
    swatches: List<String>,
    modifier: Modifier = Modifier,
    cardW: Dp,
    cardH: Dp
) {
    // Pixel-aligned with PosterRenderer.renderFilm:
    //   top photo  h * 0.65f
    //   bottom 3 photos  w / 3f  each  +  1px black border
    Column(modifier = modifier.fillMaxSize().background(PosterBgFilm)) {
        if (photos.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardH * 0.65f)
            ) {
                PhotoSlot(photos[0], modifier = Modifier.fillMaxSize())
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardH * 0.35f)
        ) {
            for (i in 1..3.coerceAtMost(photos.size - 1)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, Color.Black)
                        .padding(2.dp)
                ) {
                    PhotoSlot(photos[i], modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ===================================================================
// PosterPreviewJournal — 散落拼贴 (1-2 主照 + 小照倾斜 ±3°) (3 photos)
// ===================================================================

@Composable
private fun PosterPreviewJournal(
    photos: List<PhotoEntity>,
    theme: ThemeEntity,
    swatches: List<String>,
    modifier: Modifier = Modifier,
    cardW: Dp,
    cardH: Dp
) {
    // Pixel-aligned with PosterRenderer.renderJournal:
    //   cellW = w / 2f, cellH = h * 0.4f, rotate ±3°
    val cellW = cardW / 2f
    val cellH = cardH * 0.4f
    Box(modifier = modifier.fillMaxSize().background(PosterBgJournal)) {
        for ((i, photo) in photos.take(3).withIndex()) {
            val row = i / 2
            val col = i % 2
            val angle = if (i % 2 == 0) -3f else 3f
            Box(
                modifier = Modifier
                    .offset(x = cellW * col.toFloat(), y = cellH * row.toFloat())
                    .size(cellW, cellH)
                    .rotate(angle)
                    .padding(4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().border(1.dp, Color.Black)) {
                    PhotoSlot(photo = photo, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ===================================================================
// PosterPreviewMinimal — 主照 3/4 + 主题名/色板底部 + 1 小图角标 (2 photos)
// ===================================================================

@Composable
private fun PosterPreviewMinimal(
    photos: List<PhotoEntity>,
    theme: ThemeEntity,
    swatches: List<String>,
    modifier: Modifier = Modifier,
    cardW: Dp,
    cardH: Dp
) {
    // Pixel-aligned with PosterRenderer.renderMinimal:
    //   main photo  h * 0.7f
    //   accent photo  w * 0.2f  at BottomEnd  offset(-16.dp)  border(2.dp, Color.White)
    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        if (photos.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardH * 0.7f)
            ) {
                PhotoSlot(photos[0], modifier = Modifier.fillMaxSize())
            }
            if (photos.size > 1) {
                val accentW = cardW * 0.2f
                Box(
                    modifier = Modifier
                        .size(accentW)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-16).dp, y = (-16).dp)
                        .border(2.dp, Color.White)
                ) {
                    PhotoSlot(photos[1], modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ===================================================================
// Photo slot — AsyncImage with optional placeholder tint (reused by all 4 previews)
// ===================================================================

@Composable
private fun PhotoSlot(photo: PhotoEntity?, modifier: Modifier = Modifier) {
    if (photo != null && photo.imagePath.isNotBlank()) {
        AsyncImage(
            model = photo.imagePath,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.background(OutlineVariant))
    }
}

// ===================================================================
// Bento 2x2 collage — 4 AsyncImage tiles (used by Grid preview)
// ===================================================================

@Composable
private fun BentoCollage(
    photos: List<PhotoEntity>,
    modifier: Modifier = Modifier
) {
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(Dimens.imageCorner))
                            .background(OutlineVariant)
                    ) {
                        PhotoSlot(photo = photo, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// ===================================================================
// Poster Footer — theme name + series label + swatches swatch tag
// ===================================================================

@Composable
private fun PosterFooter(
    theme: ThemeEntity,
    swatches: List<String>,
    template: PosterRenderer.TemplateType,
    modifier: Modifier = Modifier
) {
    // Template-specific styling (Plan 4 Task 1)
    val (bg, titleSize, paletteSize) = when (template) {
        PosterRenderer.TemplateType.GRID    -> Triple(Color.White, 64.sp, 40.dp)
        PosterRenderer.TemplateType.FILM    -> Triple(PosterBgFilm, 48.sp, 18.dp)
        PosterRenderer.TemplateType.JOURNAL -> Triple(PosterBgJournal, 56.sp, 24.dp)
        PosterRenderer.TemplateType.MINIMAL -> Triple(Color.White, 36.sp, 16.dp)
    }
    val titleColor = if (template == PosterRenderer.TemplateType.JOURNAL) PosterBrown else Color.Black

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(16.dp)
    ) {
        Text(
            text = theme.name,
            fontSize = titleSize,
            fontFamily = PlayfairDisplay,
            color = titleColor
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            swatches.take(3).forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(paletteSize)
                        .background(
                            color = parseHex(hex),
                            shape = if (template == PosterRenderer.TemplateType.JOURNAL)
                                RoundedCornerShape(8.dp) else CircleShape
                        )
                        .border(
                            width = if (template == PosterRenderer.TemplateType.FILM) 1.dp else 0.dp,
                            color = Color.Black,
                            shape = if (template == PosterRenderer.TemplateType.JOURNAL)
                                RoundedCornerShape(8.dp) else CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun SwatchTag(swatches: List<String>) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.fullRound))
            .background(SurfaceLow)
            .border(0.5.dp, OutlineVariant, RoundedCornerShape(Dimens.fullRound))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatches.forEach { hex ->
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
// Template chips — Grid / Film / Diary / Minimal (all 4 selectable in Plan 3)
// ===================================================================

@Composable
private fun TemplateChips(
    selected: PosterRenderer.TemplateType,
    onSelect: (PosterRenderer.TemplateType) -> Unit
) {
    val templates = listOf(
        PosterRenderer.TemplateType.GRID,
        PosterRenderer.TemplateType.FILM,
        PosterRenderer.TemplateType.JOURNAL,
        PosterRenderer.TemplateType.MINIMAL
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        templates.forEach { template ->
            val isActive = template == selected
            TemplateChip(
                label = template.label(),
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
    val borderColor = if (isActive) RoseGold else OutlineVariant
    val textColor = if (isActive) RoseGold else OnSurfaceVariant
    val bgColor = if (isActive) RoseGold.copy(alpha = 0.08f) else Color.Transparent
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