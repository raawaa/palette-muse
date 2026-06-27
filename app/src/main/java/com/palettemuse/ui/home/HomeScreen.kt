package com.palettemuse.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.palettemuse.data.repository.ThemeWithPhotos
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.PlayfairDisplay
import com.palettemuse.theme.PlusJakartaSans
import com.palettemuse.theme.RoseGold
import com.palettemuse.theme.SurfaceContainer
import com.palettemuse.theme.SurfaceLow
import com.palettemuse.theme.SurfaceWhite
import com.palettemuse.theme.glassmorphicBackground

// ===================================================================
// Aura Aesthetic color tokens (from Stitch design HTML)
// surface: #FCF9F8 | on-surface: #1C1B1B | on-surface-variant: #524345
// primary: #8A4853 | surface-tint: #8C4B55 | outline-variant: #D7C1C3
// ===================================================================
private val PrimaryDesign = Color(0xFF8A4853)
private val SurfaceTint = Color(0xFF8C4B55)
private val OnSurface = Color(0xFF1C1B1B)
private val OnSurfaceVariant = Color(0xFF524345)
private val OutlineVariant = Color(0xFFD7C1C3)
private val SurfaceContainerHighest = Color(0xFFE5E2E1)
private val TertiaryFixedDim = Color(0xFFC8C6C3)

/** Parses a hex string ("#RRGGBB") into a Compose Color, falling back to rose gold. */
private fun parseHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(RoseGold)

/** Formats the theme's relative time as a Chinese label (今日更新 / N天前). */
private fun relativeTimeLabel(epoch: Long): String {
    val days = ((System.currentTimeMillis() - epoch) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "今日更新"
        days == 1 -> "昨日"
        else -> "${days}天前"
    }
}

@Composable
fun HomeScreen(
    onNavigateToCapture: () -> Unit,
    onNavigateToAnalyze: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWhite)
    ) {
        // ===== Scrollable Canvas =====
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = 140.dp // reserve for FAB + bottom nav
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.stackMd)
        ) {
            // ---- TopAppBar (sticky-like at top of list) ----
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassmorphicBackground()
                        .statusBarsPadding()
                        .padding(horizontal = Dimens.containerMargin, vertical = Dimens.stackMd),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = PrimaryDesign
                    )
                    Text(
                        text = "PALETTE MUSE",
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = PrimaryDesign,
                        letterSpacing = 0.2.sp * 10f // tracking 0.2em approximation
                    )
                    // Search button intentionally removed per brief decision.
                    Spacer(Modifier.width(24.dp))
                }
            }

            // ---- Hero ----
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.stackLg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "探索你的色彩",
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                        color = PrimaryDesign
                    )
                    Spacer(Modifier.height(Dimens.stackSm))
                    Text(
                        text = "捕捉每日美学，收集属于你的色彩灵感与主题。",
                        fontFamily = PlusJakartaSans,
                        fontSize = 16.sp,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Dimens.stackLg)
                    )
                }
            }

            // ---- Themes section header ----
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.containerMargin),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "色彩主题",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = OnSurface
                    )
                    Text(
                        text = "查看全部",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.1.sp * 10f,
                        color = PrimaryDesign
                    )
                }
            }

            // ---- Theme cards ----
            if (!uiState.isLoading && uiState.themes.isNotEmpty()) {
                items(uiState.themes, key = { it.theme.id }) { themeWithPhotos ->
                    ThemeCard(
                        themeWithPhotos = themeWithPhotos,
                        onClick = { onNavigateToAnalyze(themeWithPhotos.theme.id) },
                        modifier = Modifier.padding(horizontal = Dimens.containerMargin)
                    )
                }
            }

            // ---- Empty state card (shows when no themes OR always as "new collection" prompt) ----
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = Dimens.containerMargin),
                    onClick = onNavigateToCapture
                )
            }
        }

        // ===== Bottom Navigation Bar =====
        BottomNav(
            onCapture = onNavigateToCapture,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ===================================================================
// Theme Card — large single-column image card with real palette dots
// ===================================================================

@Composable
private fun ThemeCard(
    themeWithPhotos: ThemeWithPhotos,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = themeWithPhotos.theme
    val photos = themeWithPhotos.photos
    val coverPath = photos.firstOrNull()?.imagePath

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(256.dp)
            .clip(RoundedCornerShape(Dimens.cardCorner))
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(Dimens.cardCorner),
                ambientColor = RoseGold.copy(alpha = 0.06f),
                spotColor = RoseGold.copy(alpha = 0.08f)
            )
            .background(SurfaceContainer)
            .clickable(onClick = onClick)
    ) {
        // Cover image (Coil AsyncImage — graceful empty if path null/blank)
        if (!coverPath.isNullOrBlank()) {
            AsyncImage(
                model = coverPath,
                contentDescription = theme.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Bottom gradient overlay (from surface/90 via surface/30 to transparent)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SurfaceWhite.copy(alpha = 0.30f),
                            SurfaceWhite.copy(alpha = 0.90f)
                        )
                    )
                )
        )

        // Bottom info bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Dimens.stackMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Real palette dots + theme name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    themeWithPhotos.palette.forEach { hex ->
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                .background(parseHex(hex))
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = theme.name,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${photos.size} 捕捉 • ${relativeTimeLabel(theme.updatedAt)}",
                    fontFamily = PlusJakartaSans,
                    fontSize = 14.sp,
                    color = OnSurfaceVariant
                )
            }

            // Arrow-forward circular glass button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .glassmorphicBackground(alpha = 0.6f)
                    .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "查看主题",
                    tint = PrimaryDesign,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ===================================================================
// Empty State Card — dashed border "开启新收藏"
// ===================================================================

@Composable
private fun EmptyStateCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(256.dp)
            .clip(RoundedCornerShape(Dimens.cardCorner))
            .border(
                width = 1.dp,
                color = OutlineVariant,
                shape = RoundedCornerShape(Dimens.cardCorner)
            )
            .background(SurfaceLow)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.AddPhotoAlternate,
            contentDescription = null,
            tint = TertiaryFixedDim,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(Dimens.stackMd))
        Text(
            text = "开启新收藏",
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = OnSurface
        )
        Spacer(Modifier.height(Dimens.stackSm))
        Text(
            text = "拍下你的穿搭，创建首个色彩主题。",
            fontFamily = PlusJakartaSans,
            fontSize = 14.sp,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(200.dp)
        )
    }
}

// ===================================================================
// Bottom Navigation — 工作室 / FAB(add_a_photo) / 我的
// ===================================================================

@Composable
private fun BottomNav(
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Nav bar background (glassmorphic, rounded top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .glassmorphicBackground(alpha = 0.6f)
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Studio tab (active)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { /* current screen */ }
            ) {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = "工作室",
                    tint = PrimaryDesign,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "工作室",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.1.sp * 10f,
                    color = PrimaryDesign
                )
            }

            // FAB spacer (the FAB overlaps above the bar)
            Spacer(Modifier.width(64.dp))

            // Profile tab (inactive)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { /* future: profile */ }
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "我的",
                    tint = OnSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "我的",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.1.sp * 10f,
                    color = OnSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Center FAB — gradient elevation, add_a_photo
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(bottom = 24.dp) // lift above bar (-top-6 equivalent)
                .size(64.dp)
                .clip(CircleShape)
                .border(4.dp, SurfaceWhite, CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(PrimaryDesign, SurfaceTint)
                    )
                )
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = RoseGold.copy(alpha = 0.2f),
                    spotColor = RoseGold.copy(alpha = 0.3f)
                )
                .clickable(onClick = onCapture),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AddAPhoto,
                contentDescription = "拍摄",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
