package com.silverlink.app.ui.memory

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.silverlink.app.data.remote.MemoryPhotoData

/**
 * 家人端记忆库主屏幕
 * 展示已上传的照片列表，支持上传新照片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryLibraryScreen(
    viewModel: MemoryLibraryViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    var showUploadDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            // 加载 Bitmap
            context.contentResolver.openInputStream(it)?.use { stream ->
                selectedBitmap = BitmapFactory.decodeStream(stream)
            }
            showUploadDialog = true
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadPhotos()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "📸 记忆相册",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("上传照片", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            when {
                isLoading && photos.isEmpty() -> {
                    LoadingView()
                }
                photos.isEmpty() -> {
                    EmptyStateView(onUpload = { imagePickerLauncher.launch("image/*") })
                }
                else -> {
                    PhotoGrid(
                        photos = photos,
                        onPhotoClick = { /* TODO: 预览照片 */ }
                    )
                }
            }
            
            // 错误提示
            errorMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        }
    }
    
    // 上传对话框
    if (showUploadDialog && selectedBitmap != null) {
        PhotoUploadDialog(
            bitmap = selectedBitmap!!,
            uploadState = uploadState,
            onDismiss = {
                showUploadDialog = false
                selectedBitmap = null
                selectedImageUri = null
                viewModel.resetUploadState()
            },
            onUpload = { description, people, location, takenDate ->
                viewModel.uploadPhoto(
                    bitmap = selectedBitmap!!,
                    description = description,
                    people = people,
                    location = location,
                    takenDate = takenDate
                )
            },
            onUploadSuccess = {
                showUploadDialog = false
                selectedBitmap = null
                selectedImageUri = null
                viewModel.loadPhotos() // 刷新列表
            }
        )
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
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "加载中...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyStateView(onUpload: () -> Unit) {
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
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "上传老照片，让 AI 帮助老人回忆美好时光",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onUpload,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("上传第一张照片")
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<MemoryPhotoData>,
    onPhotoClick: (MemoryPhotoData) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(photos) { photo ->
            PhotoGridItem(
                photo = photo,
                onClick = { onPhotoClick(photo) }
            )
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: MemoryPhotoData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            // 照片（使用占位图或缩略图）
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.thumbnailUrl ?: photo.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 底部渐变遮罩 + 描述
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = photo.description.ifBlank { photo.aiDescription }.take(30).let {
                        if (it.length >= 30) "$it..." else it
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 地点标签
            photo.location?.let { location ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "📍 $location",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
