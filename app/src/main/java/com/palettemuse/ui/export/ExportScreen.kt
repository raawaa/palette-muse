package com.palettemuse.ui.export

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.repository.ThemeWithPhotos
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.OnSurfaceVariant
import com.palettemuse.theme.OutlineVariant
import com.palettemuse.theme.PlayfairDisplay
import com.palettemuse.theme.PlusJakartaSans
import com.palettemuse.theme.PrimaryDesign
import com.palettemuse.theme.RoseGold
import com.palettemuse.theme.RoseGoldDark
import com.palettemuse.theme.SurfaceWhite
import android.graphics.Bitmap


// Aura Aesthetic color tokens are imported from theme/Color.kt — do not redefine here.

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
                    previewBitmap = uiState.previewBitmap,
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
    previewBitmap: Bitmap?,
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
            // ---- Poster Preview Card (3:4, rendered by PosterRenderer) ----
            PosterPreviewCard(
                bitmap = previewBitmap,
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
              .background(Color.White)
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
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
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
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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
            .background(Color.White)
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