package com.palettemuse.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.repository.ThemeWithPhotos
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.OnSurface
import com.palettemuse.theme.OnSurfaceVariant
import com.palettemuse.theme.OutlineVariant
import com.palettemuse.theme.PlayfairDisplay
import com.palettemuse.theme.PlusJakartaSans
import com.palettemuse.theme.PrimaryDesign
import com.palettemuse.theme.RoseGold
import com.palettemuse.theme.SurfaceWhite
import com.palettemuse.theme.glassmorphicBackground

// Aura Aesthetic color tokens are imported from theme/Color.kt — do not redefine here.

/** Parses a hex string ("#RRGGBB") into a Compose Color, falling back to rose gold. */
private fun parseHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(RoseGold)

/** Formats the theme's relative time as a Chinese label (今日更新 / 昨日 / N天前). */
private fun relativeTimeLabel(epoch: Long): String {
    val days = ((System.currentTimeMillis() - epoch) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "今日更新"
        days == 1 -> "昨日"
        else -> "${days}天前"
    }
}

@Composable
fun ThemeDetailScreen(
    onNavigateToExport: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ThemeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Auto-pop once the theme has been deleted via the overflow menu.
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onBack()
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
                ThemeDetailContent(
                    state = uiState.data!!,
                    onExport = { onNavigateToExport(viewModel.themeId) },
                    onRename = viewModel::renameTheme,
                    onUpdateColor = viewModel::updateThemeColor,
                    onDelete = viewModel::deleteTheme,
                    onBack = onBack
                )
            }
        }
    }
}

// ===================================================================
// Content — TopAppBar + scrollable palette header + Hero + masonry
// ===================================================================

@Composable
private fun ThemeDetailContent(
    state: ThemeWithPhotos,
    onExport: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateColor: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val theme = state.theme
    val photos = state.photos
    val palette = state.palette

    // Hero = seed photo (or first photo as fallback).
    val heroPhoto = photos.firstOrNull { it.isSeed } ?: photos.firstOrNull()
    val gridPhotos = if (heroPhoto != null) photos.filter { it.id != heroPhoto.id } else photos

    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.containerMargin,
                end = Dimens.containerMargin,
                top = 0.dp,
                bottom = 140.dp
            ),
            verticalItemSpacing = Dimens.gutter,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gutter)
        ) {
            // ---- Header (palette header) — spans full width as a single item ----
            item(span = fullLineSpan()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 88.dp, bottom = Dimens.stackMd), // top clearance for TopAppBar
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Palette dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        palette.forEach { hex ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = CircleShape,
                                        ambientColor = RoseGold.copy(alpha = 0.06f),
                                        spotColor = RoseGold.copy(alpha = 0.08f)
                                    )
                                    .clip(CircleShape)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                    .background(parseHex(hex))
                            )
                        }
                    }
                    Spacer(Modifier.height(Dimens.stackSm))
                    Text(
                        text = theme.name,
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                        color = OnSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${photos.size} 次捕捉 • ${relativeTimeLabel(theme.updatedAt)}",
                        fontFamily = PlusJakartaSans,
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )
                }
            }

            // ---- Origin Post (Hero) — full width large image ----
            if (heroPhoto != null) {
                item(span = fullLineSpan()) {
                    HeroOrigin(photo = heroPhoto)
                }
            }

            // ---- Masonry grid items (remaining captures) ----
            items(
                items = gridPhotos,
                key = { it.id }
            ) { photo ->
                CaptureCard(photo = photo)
            }
        }

        // ===== TopAppBar (glassmorphic, sticky overlay) =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphicBackground()
                .statusBarsPadding()
                .padding(horizontal = Dimens.containerMargin, vertical = Dimens.stackMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = OnSurfaceVariant
                )
            }
            Text(
                text = "PALETTE MUSE",
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = PrimaryDesign,
                letterSpacing = 0.2.sp * 10f
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = "更多",
                        tint = OnSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名", fontFamily = PlusJakartaSans) },
                        onClick = {
                            menuExpanded = false
                            onRename("新主题") // MVP: simple rename hook; UI dialog deferred
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("改主题色", fontFamily = PlusJakartaSans) },
                        onClick = {
                            menuExpanded = false
                            onUpdateColor(theme.representativeHex) // MVP: hook only
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", fontFamily = PlusJakartaSans, color = RoseGold) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }

        // ===== Export FAB (bottom-end glass pill) =====
        ExportPill(
            onClick = onExport,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.containerMargin, bottom = Dimens.stackLg)
        )
    }
}

// ===================================================================
// Hero Origin — full-width large image with glass "主题起点" pill
// ===================================================================

@Composable
private fun HeroOrigin(photo: PhotoEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(530.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(Dimens.cardCorner),
                ambientColor = RoseGold.copy(alpha = 0.06f),
                spotColor = RoseGold.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(Dimens.cardCorner))
            .background(OutlineVariant)
    ) {
        AsyncImage(
            model = photo.imagePath,
            contentDescription = "主题起点",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Top-left glass pill — 主题起点 (star icon + label)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .glassmorphicBackground(alpha = 0.6f)
                .clip(RoundedCornerShape(Dimens.pillShape))
                .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(Dimens.pillShape))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = PrimaryDesign,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "主题起点",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp * 10f,
                color = OnSurface
            )
        }
    }
}

// ===================================================================
// Capture Card — masonry grid item (AsyncImage + rounded corner)
// ===================================================================

@Composable
private fun CaptureCard(photo: PhotoEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(Dimens.imageCorner),
                ambientColor = RoseGold.copy(alpha = 0.06f),
                spotColor = RoseGold.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(Dimens.imageCorner))
            .background(OutlineVariant)
    ) {
        AsyncImage(
            model = photo.imagePath,
            contentDescription = photo.dominantHex,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Crop
        )
    }
}

// ===================================================================
// Export Pill — bottom-end glassmorphic pill with ios_share icon
// ===================================================================

@Composable
private fun ExportPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(Dimens.pillShape),
                ambientColor = RoseGold.copy(alpha = 0.06f),
                spotColor = RoseGold.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(Dimens.pillShape))
            .glassmorphicBackground(alpha = 0.6f)
            .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(Dimens.pillShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "导出海报",
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp * 10f,
            color = PrimaryDesign
        )
        Icon(
            Icons.Default.IosShare,
            contentDescription = "导出海报",
            tint = PrimaryDesign,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ===================================================================
// Staggered grid span helper — full line span for header / hero items
// ===================================================================

@Suppress("ktlint:standard:function-naming")
private fun fullLineSpan() = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine
