package com.palettemuse.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palettemuse.data.model.ProjectEntity
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.RoseGold
import com.palettemuse.theme.PlayfairDisplay
import com.palettemuse.theme.PlusJakartaSans
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToCapture: () -> Unit,
    onNavigateToAnalyze: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFCF9F8))) {
        if (uiState.isLoading) {
            // Loading state
        } else if (uiState.projects.isEmpty()) {
            // === Empty state ===
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(120.dp))
                Text("🎨", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "我的作品集",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = RoseGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "拍摄你的第一个色彩灵感",
                    color = Color(0xFF524345),
                    fontSize = 16.sp
                )
            }

            // FAB
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomEnd) {
                LargeFloatingActionButton(
                    onClick = onNavigateToCapture,
                    shape = RoundedCornerShape(Dimens.pillShape),
                    containerColor = RoseGold
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新建", tint = Color.White)
                }
            }
        } else {
            // === Main content with scroll ===
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // ===== TopAppBar (in-content, no Scaffold) =====
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color(0xFF524345)
                    )
                    Text(
                        text = "ChromaMuse",
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = RoseGold,
                        letterSpacing = (-0.5).sp
                    )
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Account",
                        tint = Color(0xFF524345)
                    )
                }

                // ===== Today's Inspiration =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Inspiration",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = Color(0xFF1C1B1B)
                    )
                    Text(
                        text = SimpleDateFormat("MMM dd", Locale.US).format(Date()).uppercase(),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5C5D6E)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hero card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF0EDED))
                ) {
                    // Latest project image as hero (or placeholder)
                    val heroProject = uiState.projects.firstOrNull()
                    if (heroProject != null) {
                        val bitmap = BitmapFactory.decodeFile(heroProject.imagePath)
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFFDCD9D9).copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )

                    // Bottom content
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        // Tag pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(Color.White.copy(alpha = 0.8f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(9999.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (heroProject != null) heroProject.title.uppercase() else "WELCOME",
                                fontSize = 10.sp,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1B)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (heroProject != null) "Captured with Palette Muse" else "Start your color journey",
                            fontSize = 16.sp,
                            color = Color.White,
                            modifier = Modifier.width(280.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ===== Discover Palettes =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discover Palettes",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = Color(0xFF1C1B1B)
                    )
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Filter",
                        tint = Color(0xFF5C5D6E)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Staggered 2-column grid
                val projects = uiState.projects
                val column1 = projects.filterIndexed { index, _ -> index % 2 == 0 }
                val column2 = projects.filterIndexed { index, _ -> index % 2 == 1 }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Column 1
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        column1.forEach { project ->
                            PaletteCard(
                                project = project,
                                onClick = { onNavigateToAnalyze(project.id) },
                                heights = listOf(280, 360)
                            )
                        }
                    }

                    // Column 2 (staggered offset)
                    Column(
                        modifier = Modifier.weight(1f).padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        column2.forEachIndexed { index, project ->
                            PaletteCard(
                                project = project,
                                onClick = { onNavigateToAnalyze(project.id) },
                                heights = listOf(320, 260)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(120.dp)) // Room for bottom nav
            }

            // FAB
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp, bottom = 100.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                LargeFloatingActionButton(
                    onClick = onNavigateToCapture,
                    shape = RoundedCornerShape(Dimens.pillShape),
                    containerColor = RoseGold
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新建", tint = Color.White)
                }
            }
        }

        // ===== Bottom Navigation =====
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.6f))
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home (active)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Home",
                        tint = RoseGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "home",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = RoseGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Camera
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onNavigateToCapture() }
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Camera",
                        tint = Color(0xFF5C5D6E).copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "photo_camera",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = Color(0xFF5C5D6E).copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Profile
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color(0xFF5C5D6E).copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "person",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = Color(0xFF5C5D6E).copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    heights: List<Int>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heights[project.hashCode().mod(heights.size)].dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF0EDED))
        ) {
            val bitmap = BitmapFactory.decodeFile(project.imagePath)
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Palette circles overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    listOf("#F5F2EB", "#D1BCAE", "#8C7A72").forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(1.dp, Color.White, RoundedCornerShape(9999.dp))
                                .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(9999.dp))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = project.title,
                fontSize = 14.sp,
                color = Color(0xFF5C5D6E),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
