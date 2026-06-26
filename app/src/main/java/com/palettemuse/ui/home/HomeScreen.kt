package com.palettemuse.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palettemuse.data.model.ProjectEntity
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.palettemuse.R
import com.palettemuse.theme.Dimens
import com.palettemuse.theme.PlayfairDisplay
import com.palettemuse.theme.PlusJakartaSans
import com.palettemuse.theme.RoseGold
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ===== TopAppBar (always visible) =====
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF524345))
                Text(
                    text = "ChromaMuse",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 28.sp,
                    color = RoseGold,
                    letterSpacing = (-0.5).sp
                )
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF524345))
            }

            if (uiState.isLoading) {
                // loading
            } else if (uiState.projects.isEmpty()) {
                // ===== Empty hero placeholder =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Color Diary",
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

                // Empty hero
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF0EDED)),
                    contentAlignment = Alignment.Center
                ) {
                    val cameraAnim by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.camera))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LottieAnimation(composition = cameraAnim, modifier = Modifier.size(120.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Start your color journey",
                            fontFamily = PlayfairDisplay,
                            fontSize = 24.sp,
                            color = RoseGold,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Capture your first color inspiration",
                            fontSize = 14.sp,
                            color = Color(0xFF524345)
                        )
                    }
                }
            } else {
                // ===== My Color Diary =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Color Diary",
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
                val heroProject = uiState.projects.firstOrNull()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF0EDED))
                ) {
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFFDCD9D9).copy(alpha = 0.4f))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(Color.White.copy(alpha = 0.8f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(9999.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = (heroProject?.title ?: "WELCOME").uppercase(),
                                fontSize = 10.sp, letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1B)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (heroProject != null) "Captured with Palette Muse" else "Start your color journey",
                            fontSize = 16.sp, color = Color.White, modifier = Modifier.width(280.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ===== Recent Captures (always visible) =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Captures",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = Color(0xFF1C1B1B)
                )
                Icon(Icons.Default.Tune, contentDescription = "Filter", tint = Color(0xFF5C5D6E))
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.projects.isEmpty()) {
                // Empty grid placeholder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(280, 360).forEach { h ->
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(h.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF0EDED)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📷", fontSize = 32.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("No captures yet", fontSize = 14.sp, color = Color(0xFF5C5D6E))
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            } else {
                // Staggered grid with actual projects
                val projects = uiState.projects
                val column1 = projects.filterIndexed { i, _ -> i % 2 == 0 }
                val column2 = projects.filterIndexed { i, _ -> i % 2 == 1 }
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                        column1.forEach { project ->
                            PaletteCard(project, sdf.format(Date(project.createdAt)), 280) {
                                onNavigateToAnalyze(project.id)
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                        column2.forEachIndexed { idx, project ->
                            PaletteCard(project, sdf.format(Date(project.createdAt)), if (idx % 2 == 0) 320 else 260) {
                                onNavigateToAnalyze(project.id)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }

        // ===== FAB =====
        Box(
            modifier = Modifier.fillMaxSize().padding(end = 20.dp, bottom = 100.dp),
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

        // ===== Bottom Navigation =====
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.6f))
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = RoseGold, modifier = Modifier.size(24.dp))
                    Text("home", fontSize = 10.sp, letterSpacing = 1.sp, color = RoseGold, fontWeight = FontWeight.Bold)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onNavigateToCapture() }
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = Color(0xFF5C5D6E).copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    Text("photo_camera", fontSize = 10.sp, letterSpacing = 1.sp, color = Color(0xFF5C5D6E).copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color(0xFF5C5D6E).copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    Text("person", fontSize = 10.sp, letterSpacing = 1.sp, color = Color(0xFF5C5D6E).copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PaletteCard(project: ProjectEntity, date: String, height: Int, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth().height(height.dp)
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
            // Palette circles bottom-left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart).padding(12.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    listOf("#F5F2EB", "#D1BCAE", "#8C7A72").forEach { hex ->
                        Box(
                            Modifier
                                .size(16.dp).border(1.dp, Color.White, CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(project.title, fontSize = 14.sp, color = Color(0xFF5C5D6E), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(date, fontSize = 12.sp, color = Color(0xFF5C5D6E))
        }
    }
}
