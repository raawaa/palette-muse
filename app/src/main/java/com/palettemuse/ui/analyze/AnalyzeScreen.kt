package com.palettemuse.ui.analyze

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palettemuse.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(
    projectId: String,
    onNavigateToExport: () -> Unit,
    onBack: () -> Unit,
    viewModel: AnalyzeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("穿搭色彩分析") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White.copy(alpha = 0.6f)
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败: ${uiState.error}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val project = uiState.project ?: return@Scaffold
            val palettes = uiState.palettes

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.stackMd)
            ) {
                // Photo
                val bitmap = BitmapFactory.decodeFile(project.imagePath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "穿搭照片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(Dimens.cardCorner)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.containerMargin))

                // Color tags
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.stackSm)) {
                    palettes.forEach { palette ->
                        Box(
                            modifier = Modifier
                                .background(
                                    Color(android.graphics.Color.parseColor(palette.hexColor)),
                                    RoundedCornerShape(Dimens.chipCorner)
                                )
                                .padding(horizontal = Dimens.stackMd, vertical = 6.dp)
                        ) {
                            Text(
                                text = palette.semanticName.uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.stackLg))

                // Color palette section
                Text(
                    text = "Color Palette Extracted",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(Dimens.gutter))

                palettes.forEach { palette ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(Dimens.cardCorner))
                                .background(Color(android.graphics.Color.parseColor(palette.hexColor)))
                        )
                        Spacer(modifier = Modifier.width(Dimens.gutter))
                        Column {
                            Text(
                                text = palette.role.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = palette.semanticName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = palette.hexColor,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.stackLg))

                // CTA
                Button(
                    onClick = onNavigateToExport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.buttonCorner),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(Dimens.stackSm))
                    Text(text = "开始色彩探索", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(Dimens.stackSm))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
