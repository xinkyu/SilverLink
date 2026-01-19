package com.silverlink.app.ui.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.silverlink.app.feature.memory.CognitiveQuizService
import com.silverlink.app.ui.theme.WarmPrimary
import kotlinx.coroutines.delay

/**
 * 记忆测验界面
 * 展示照片并提问，老人通过语音回答
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryQuizScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryQuizViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val correctCount by viewModel.correctCount.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(context, "需要录音权限才能回答", Toast.LENGTH_SHORT).show()
        }
    }
    val isRecording = voiceState is MemoryQuizViewModel.VoiceState.Recording

    LaunchedEffect(voiceState) {
        if (voiceState is MemoryQuizViewModel.VoiceState.Error) {
            Toast.makeText(
                context,
                (voiceState as MemoryQuizViewModel.VoiceState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            viewModel.resetVoiceState()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "记忆小游戏",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (totalCount > 0) {
                            Spacer(modifier = Modifier.width(12.dp))
                            ScoreBadge(correct = correctCount, total = totalCount)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            when (val state = uiState) {
                is MemoryQuizViewModel.QuizUiState.Loading -> {
                    LoadingContent()
                }
                is MemoryQuizViewModel.QuizUiState.NoPhotos -> {
                    NoPhotosContent(onBack = onBack)
                }
                is MemoryQuizViewModel.QuizUiState.ShowingQuestion -> {
                    QuestionContent(
                        question = state.question,
                        isRecording = isRecording,
                        onRecordStart = {
                            if (hasAudioPermission) {
                                viewModel.startRecording()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onRecordEnd = {
                            viewModel.stopRecordingAndRecognize()
                        },
                        onHintClick = { viewModel.playHint() },
                        onRepeatClick = { viewModel.repeatQuestion() }
                    )
                }
                is MemoryQuizViewModel.QuizUiState.WaitingForAnswer -> {
                    VerifyingContent()
                }
                is MemoryQuizViewModel.QuizUiState.ShowingResult -> {
                    ResultContent(
                        result = state.result,
                        isCorrect = state.isCorrect,
                        onNextClick = { viewModel.loadNextQuestion() }
                    )
                }
                is MemoryQuizViewModel.QuizUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetryClick = { viewModel.loadNextQuestion() }
                    )
                }
            }
        }
    }
}

/**
 * 分数徽章
 */
@Composable
private fun ScoreBadge(correct: Int, total: Int) {
    val rate = if (total > 0) correct.toFloat() / total else 0f
    val color = when {
        rate >= 0.7f -> Color(0xFF4CAF50)
        rate >= 0.5f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$correct / $total",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 加载中内容
 */
@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = WarmPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在准备问题...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 无照片内容
 */
@Composable
private fun NoPhotosContent(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "📷",
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "还没有照片可以测试",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请让家人先上传一些照片到记忆库",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text("返回")
            }
        }
    }
}

/**
 * 问题展示内容
 */
@Composable
private fun QuestionContent(
    question: CognitiveQuizService.QuizQuestion,
    isRecording: Boolean,
    onRecordStart: () -> Unit,
    onRecordEnd: () -> Unit,
    onHintClick: () -> Unit,
    onRepeatClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 照片卡片（带金边动效）
        PhotoCard(
            imageUrl = question.photo.imageUrl,
            thumbnailUrl = question.photo.thumbnailUrl,
            localPath = question.photo.localPath,
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 问题文字
        Text(
            text = question.questionText,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 提示按钮
            if (question.hint != null) {
                IconButton(
                    onClick = onHintClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = "提示",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            // 重复问题按钮
            IconButton(
                onClick = onRepeatClick,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = "重复问题",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 按住说话按钮
        PressToSpeakButton(
            isRecording = isRecording,
            onRecordStart = onRecordStart,
            onRecordEnd = onRecordEnd
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 照片卡片
 */
@Composable
private fun PhotoCard(
    imageUrl: String?,
    thumbnailUrl: String?,
    localPath: String?,
    modifier: Modifier = Modifier
) {
    // 金边闪烁动画
    var glowing by remember { mutableStateOf(true) }
    val borderAlpha by animateFloatAsState(
        targetValue = if (glowing) 1f else 0.3f,
        animationSpec = tween(1000),
        label = "border"
    )
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            glowing = !glowing
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 4.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFD700).copy(alpha = borderAlpha),
                        Color(0xFFFFA500).copy(alpha = borderAlpha)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        val imageSource = when {
            localPath != null -> localPath
            imageUrl != null -> imageUrl
            thumbnailUrl != null -> thumbnailUrl
            else -> null
        }
        
        if (imageSource != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageSource)
                    .crossfade(true)
                    .build(),
                contentDescription = "测验照片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📷",
                    fontSize = 64.sp
                )
            }
        }
    }
}

/**
 * 按住说话按钮
 */
@Composable
private fun PressToSpeakButton(
    isRecording: Boolean,
    onRecordStart: () -> Unit,
    onRecordEnd: () -> Unit
) {
    val backgroundColor = if (isRecording) {
        MaterialTheme.colorScheme.error
    } else {
        WarmPrimary
    }
    
    Box(
        modifier = Modifier
            .size(100.dp)
            .shadow(8.dp, CircleShape)
            .background(backgroundColor, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onRecordStart()
                        tryAwaitRelease()
                        onRecordEnd()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "录音",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (isRecording) "松开" else "按住说",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * 验证中内容
 */
@Composable
private fun VerifyingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = WarmPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在验证答案...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 结果展示内容
 */
@Composable
private fun ResultContent(
    result: CognitiveQuizService.QuizResult,
    isCorrect: Boolean,
    onNextClick: () -> Unit
) {
    // 正确时显示庆祝动效
    var showCelebration by remember { mutableStateOf(isCorrect) }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // 结果表情
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Text(
                    text = if (isCorrect) "🎉" else "💪",
                    fontSize = 80.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 结果文字
            val feedbackText = when (result) {
                is CognitiveQuizService.QuizResult.Correct -> result.encouragement
                is CognitiveQuizService.QuizResult.Incorrect -> result.gentleHint
                is CognitiveQuizService.QuizResult.PartiallyCorrect -> result.feedback
            }
            
            Text(
                text = feedbackText,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 下一题按钮
            Button(
                onClick = onNextClick,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "下一题",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * 错误内容
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "😕",
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetryClick) {
                Text("重试")
            }
        }
    }
}
