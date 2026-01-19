package com.silverlink.app.ui.memory

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 照片上传对话框
 * 支持 AI 自动分析和手动录入描述
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoUploadDialog(
    bitmap: Bitmap,
    uploadState: UploadState,
    onDismiss: () -> Unit,
    onUpload: (description: String, people: String, location: String, takenDate: String) -> Unit,
    onUploadSuccess: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var takenDate by remember { mutableStateOf("") }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    
    // 监听上传成功
    LaunchedEffect(uploadState) {
        if (uploadState is UploadState.Success) {
            onUploadSuccess()
        }
    }
    
    Dialog(
        onDismissRequest = { 
            if (uploadState !is UploadState.Uploading) onDismiss() 
        },
        properties = DialogProperties(
            dismissOnBackPress = uploadState !is UploadState.Uploading,
            dismissOnClickOutside = uploadState !is UploadState.Uploading,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📸 上传记忆照片",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = uploadState !is UploadState.Uploading
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 照片预览
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "选中的照片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        
                        // AI 分析中遮罩
                        if (uploadState is UploadState.Analyzing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "AI 正在分析照片...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // 描述输入
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("描述（给老人讲述这张照片的故事）") },
                        placeholder = { Text("例如：这是2018年春节，全家在老家门口贴对联...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = uploadState !is UploadState.Uploading
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 人物输入
                    OutlinedTextField(
                        value = people,
                        onValueChange = { people = it },
                        label = { Text("照片中的人物") },
                        placeholder = { Text("例如：爷爷, 奶奶, 儿子, 孙子") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        enabled = uploadState !is UploadState.Uploading
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 地点输入
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("拍摄地点") },
                        placeholder = { Text("例如：北京故宫 / 老家客厅") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        singleLine = true,
                        enabled = uploadState !is UploadState.Uploading
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 日期输入
                    OutlinedTextField(
                        value = takenDate,
                        onValueChange = { takenDate = it },
                        label = { Text("拍摄日期（可选）") },
                        placeholder = { Text("例如：2018年春节 / 2020-10-01") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        singleLine = true,
                        enabled = uploadState !is UploadState.Uploading
                    )
                    
                    // 提示信息
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "描述越详细，老人问起时 AI 能回答得越好。人物信息有助于认知训练功能。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = uploadState !is UploadState.Uploading
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = { onUpload(description, people, location, takenDate) },
                        modifier = Modifier.weight(1f),
                        enabled = uploadState !is UploadState.Uploading && uploadState !is UploadState.Analyzing
                    ) {
                        if (uploadState is UploadState.Uploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("上传中...")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("上传")
                        }
                    }
                }
                
                // 错误提示
                AnimatedVisibility(visible = uploadState is UploadState.Error) {
                    if (uploadState is UploadState.Error) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    uploadState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
