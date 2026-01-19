package com.silverlink.app.ui.memory

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.silverlink.app.data.remote.MemoryPhotoData
import kotlinx.coroutines.delay

/**
 * 老人端记忆画廊屏幕
 * 沉浸式横向画廊，支持语音问答
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryGalleryScreen(
    viewModel: MemoryGalleryViewModel = viewModel(),
    onBack: () -> Unit,
    onQuizClick: () -> Unit = {},
    onAskQuestion: (String, MemoryPhotoData) -> Unit = { _, _ -> }
) {
    val photos by viewModel.photos.collectAsState()
    val currentIndex by viewModel.currentPhotoIndex.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentPhoto = photos.getOrNull(currentIndex)
    
    var showDescription by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        viewModel.loadPhotos()
    }
    
    // 自动隐藏描述
    LaunchedEffect(currentIndex) {
        showDescription = true
        delay(5000)
        showDescription = false
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    when {
                        dragAmount < -50 -> viewModel.nextPhoto()
                        dragAmount > 50 -> viewModel.previousPhoto()
                    }
                }
            }
    ) {
        when {
            isLoading -> {
                LoadingView()
            }
            photos.isEmpty() -> {
                EmptyGalleryView(onBack = onBack)
            }
            currentPhoto != null -> {
                // 背景模糊图
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentPhoto.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(30.dp),
                    contentScale = ContentScale.Crop
                )
                
                // 主图片
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentPhoto.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = currentPhoto.description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // 顶部渐变遮罩 + 返回按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // 右侧按钮组
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 记忆小游戏按钮
                        IconButton(
                            onClick = onQuizClick,
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Quiz,
                                contentDescription = "记忆小游戏",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // 照片计数
                        Text(
                            text = "${currentIndex + 1} / ${photos.size}",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                
                // 底部描述区域
                AnimatedVisibility(
                    visible = showDescription,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .padding(20.dp)
                            .padding(bottom = 20.dp)
                    ) {
                        Column {
                            // 描述文字
                            Text(
                                text = currentPhoto.description.ifBlank { currentPhoto.aiDescription },
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 28.sp
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 地点和日期标签
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                currentPhoto.location?.let { location ->
                                    InfoChip(icon = "📍", text = location)
                                }
                                currentPhoto.takenDate?.let { date ->
                                    InfoChip(icon = "📅", text = date)
                                }
                                currentPhoto.people?.let { people ->
                                    InfoChip(icon = "👥", text = people)
                                }
                            }
                        }
                    }
                }
                
                // 左右导航箭头
                if (currentIndex > 0) {
                    IconButton(
                        onClick = { viewModel.previousPhoto() },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(8.dp)
                            .size(56.dp)
                            .background(
                                Color.Black.copy(alpha = 0.3f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "上一张",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                if (currentIndex < photos.size - 1) {
                    IconButton(
                        onClick = { viewModel.nextPhoto() },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(8.dp)
                            .size(56.dp)
                            .background(
                                Color.Black.copy(alpha = 0.3f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "下一张",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                // 点击显示描述
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.pressed }) {
                                        showDescription = !showDescription
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "加载照片中...",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun EmptyGalleryView(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "📷",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "还没有记忆照片",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "请让家人上传照片到记忆相册",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onBack,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("返回")
        }
    }
}

@Composable
private fun InfoChip(icon: String, text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
