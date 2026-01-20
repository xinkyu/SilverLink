package com.silverlink.app.ui.onboarding

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.silverlink.app.data.local.Dialect
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.repository.SyncRepository
import com.silverlink.app.feature.chat.AudioPlayerHelper
import com.silverlink.app.feature.chat.AudioRecorder
import com.silverlink.app.feature.chat.VoiceCloningService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 家人端配置流程
 * 1. 输入老人称呼和方言设置
 * 2. 录制声音样本（用于声音复刻，支持方言TTS）
 * 3. 生成配对码和二维码
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySetupScreen(
    onBack: () -> Unit,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val syncRepository = remember { SyncRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var currentStep by remember { mutableStateOf(1) }
    var elderName by remember { mutableStateOf("") }
    var elderProfile by remember { mutableStateOf("") }
    var selectedDialect by remember { mutableStateOf(Dialect.NONE) }
    var clonedVoiceId by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var qrContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmApricot)
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { 
                Text(
                    text = when (currentStep) {
                        1 -> "配置智能伴侣"
                        2 -> "录制声音样本"
                        else -> "分享给长辈"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (currentStep > 1) {
                        currentStep--
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color(0xFF5D4037)
            )
        )
        
        // 步骤指示器
        StepIndicator(
            currentStep = currentStep,
            totalSteps = 3,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        
        when (currentStep) {
            1 -> ElderInfoStep(
                elderName = elderName,
                onNameChange = { elderName = it },
                elderProfile = elderProfile,
                onProfileChange = { elderProfile = it },
                selectedDialect = selectedDialect,
                onDialectChange = { selectedDialect = it },
                onNext = {
                    if (elderName.isNotBlank()) {
                        // 保存方言设置
                        userPreferences.setDialect(selectedDialect)
                        currentStep = 2
                    }
                }
            )
            2 -> VoiceRecordingStep(
                elderName = elderName,
                familyDeviceId = syncRepository.getDeviceId(),
                selectedDialect = selectedDialect,
                onVoiceCloned = { voiceId ->
                    clonedVoiceId = voiceId
                    userPreferences.setClonedVoiceId(voiceId)
                },
                onNext = {
                    if (!isLoading) {
                        isLoading = true
                        // 创建配对码并同步到云端
                        scope.launch {
                            android.util.Log.d("FamilySetup", "onNext called, clonedVoiceId='$clonedVoiceId'")
                            val result = syncRepository.createPairingCodeOnCloud(elderName, elderProfile, selectedDialect.name, clonedVoiceId)
                            result.onSuccess { code ->
                                pairingCode = code
                                // 生成二维码内容（包含配对码、长辈称呼、方言和复刻音色ID）
                                android.util.Log.d("FamilySetup", "Generating QR with clonedVoiceId='$clonedVoiceId'")
                                qrContent = userPreferences.generateQRContent(
                                    code, elderName, elderProfile, selectedDialect, clonedVoiceId
                                )
                                currentStep = 3
                            }
                            isLoading = false
                        }
                    }
                },
                onSkip = {
                    if (!isLoading) {
                        isLoading = true
                        scope.launch {
                            val result = syncRepository.createPairingCodeOnCloud(elderName, elderProfile, selectedDialect.name, "")
                            result.onSuccess { code ->
                                pairingCode = code
                                qrContent = userPreferences.generateQRContent(
                                    code, elderName, elderProfile, selectedDialect, ""
                                )
                                currentStep = 3
                            }
                            isLoading = false
                        }
                    }
                },
                isLoading = isLoading
            )
            3 -> PairingCodeStep(
                elderName = elderName,
                pairingCode = pairingCode,
                qrContent = qrContent,
                hasClonedVoice = clonedVoiceId.isNotBlank(),
                onComplete = {
                    onSetupComplete()
                }
            )
        }
    }
}

/**
 * 步骤指示器
 */
@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (step in 1..totalSteps) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (step <= currentStep) Color(0xFFFFB74D) else Color(0xFFBCAAA4),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (step < currentStep) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = step.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (step < totalSteps) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(3.dp)
                        .background(
                            color = if (step < currentStep) Color(0xFFFFB74D) else Color(0xFFBCAAA4)
                        )
                )
            }
        }
    }
}

/**
 * 步骤1: 输入长辈信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderInfoStep(
    elderName: String,
    onNameChange: (String) -> Unit,
    elderProfile: String,
    onProfileChange: (String) -> Unit,
    selectedDialect: Dialect,
    onDialectChange: (Dialect) -> Unit,
    onNext: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // 图标
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF8D6E63)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "请输入长辈的称呼",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "这将用于AI伴侣称呼长辈",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF8D6E63)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 输入框
        OutlinedTextField(
            value = elderName,
            onValueChange = onNameChange,
            label = { Text("长辈称呼", fontSize = 18.sp) },
            placeholder = { Text("例如：王爷爷、李奶奶", fontSize = 18.sp) },
            textStyle = MaterialTheme.typography.headlineSmall,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFFB74D),
                unfocusedBorderColor = Color(0xFFBCAAA4),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = elderProfile,
            onValueChange = onProfileChange,
            label = { Text("长辈信息（可选）", fontSize = 18.sp) },
            placeholder = { Text("如：家乡/兴趣/身体状况/忌口", fontSize = 18.sp) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFFB74D),
                unfocusedBorderColor = Color(0xFFBCAAA4),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            minLines = 2,
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        // 方言选择下拉框
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedDialect.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("AI语音方言（可选）", fontSize = 18.sp) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFB74D),
                    unfocusedBorderColor = Color(0xFFBCAAA4),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledBorderColor = Color(0xFFBCAAA4),
                    disabledContainerColor = Color.White,
                    disabledTextColor = Color(0xFF5D4037),
                    disabledLabelColor = Color(0xFF8D6E63)
                ),
                enabled = false
            )
            // 透明覆盖层用于捕获点击
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = !expanded }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Dialect.values().forEach { dialect ->
                    DropdownMenuItem(
                        text = { Text(dialect.displayName) },
                        onClick = {
                            onDialectChange(dialect)
                            expanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 下一步按钮
        Button(
            onClick = onNext,
            enabled = elderName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFB74D),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFBCAAA4)
            )
        ) {
            Text(
                text = "下一步",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 步骤2: 录制声音样本
 */
@Composable
fun VoiceRecordingStep(
    elderName: String,
    familyDeviceId: String,
    selectedDialect: Dialect,
    onVoiceCloned: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioRecorder = remember { AudioRecorder(context) }
    val audioPlayer = remember { AudioPlayerHelper(context) }
    val voiceCloningService = remember { VoiceCloningService() }
    
    var recordingState by remember { mutableStateOf(RecordingState.IDLE) }
    var recordedFilePath by remember { mutableStateOf<String?>(null) }
    var recordingDuration by remember { mutableStateOf(0) }
    var cloningState by remember { mutableStateOf(CloningState.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                errorMessage = "需要麦克风权限才能录制声音"
            }
        }
    )

    // 进入页面时自动请求权限
    LaunchedEffect(Unit) {
        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    
    // 录音计时器
    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.RECORDING) {
            recordingDuration = 0
            while (recordingState == RecordingState.RECORDING) {
                delay(1000)
                recordingDuration++
                // 最长30秒自动停止
                if (recordingDuration >= VoiceCloningService.MAX_DURATION_SECONDS) {
                    recordedFilePath = audioRecorder.stopRecording()
                    recordingState = RecordingState.COMPLETED
                }
            }
        }
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // 添加滚动支持
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // 标题和说明
        Text(
            text = "录制家人的声音", // 修改标题
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (selectedDialect != Dialect.NONE) {
                "录制 10-30 秒清晰语音，AI将模仿您的声音并用${selectedDialect.displayName}陪伴${elderName}"
            } else {
                "录制 10-30 秒清晰语音，AI将模仿您的声音陪伴${elderName}"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF8D6E63),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 录音提示卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "🎤 录音示范文本（建议朗读）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${elderName}，我是[您的名字]。最近身体还好吗？要注意休息，多喝水。我会经常来陪您的，您想我了就跟小银说话。\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF795548),
                    lineHeight = 22.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 录音按钮和状态
        when (cloningState) {
            CloningState.CLONING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = Color(0xFFFFB74D)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在创建专属声音...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF8D6E63)
                )
            }
            CloningState.SUCCESS -> {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                        .padding(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "声音复刻成功！",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF388E3C),
                    fontWeight = FontWeight.Bold
                )
            }
            else -> {
                // 录音按钮
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = when (recordingState) {
                                RecordingState.RECORDING -> Color(0xFFE53935)
                                RecordingState.COMPLETED -> Color(0xFF4CAF50)
                                else -> Color(0xFFFFB74D)
                            },
                            shape = CircleShape
                        )
                        .clickable(enabled = cloningState == CloningState.IDLE) {
                            when (recordingState) {
                                RecordingState.IDLE -> {
                                    errorMessage = null
                                    // 检查权限（虽然自动请求了，但点击时再次检查更稳妥）
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        val path = audioRecorder.startRecording()
                                        if (path != null) {
                                            recordingState = RecordingState.RECORDING
                                        } else {
                                            errorMessage = "无法启动录音，请检查设备"
                                        }
                                    } else {
                                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                RecordingState.RECORDING -> {
                                    recordedFilePath = audioRecorder.stopRecording()
                                    recordingState = RecordingState.COMPLETED
                                }
                                RecordingState.COMPLETED -> {
                                    // 重新录制
                                    recordingState = RecordingState.IDLE
                                    recordedFilePath = null
                                    recordingDuration = 0
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (recordingState) {
                            RecordingState.RECORDING -> Icons.Default.Stop
                            RecordingState.COMPLETED -> Icons.Default.Mic
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 录音时长显示
                Text(
                    text = when (recordingState) {
                        RecordingState.RECORDING -> "录音中 ${recordingDuration}s / 30s"
                        RecordingState.COMPLETED -> "已录制 ${recordingDuration}s（点击重录）"
                        else -> "点击开始录音"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (recordingState == RecordingState.RECORDING) Color(0xFFE53935) else Color(0xFF8D6E63)
                )
                
                // 录音进度条
                if (recordingState == RecordingState.RECORDING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { recordingDuration / 30f },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(6.dp),
                        color = Color(0xFFE53935),
                        trackColor = Color(0xFFBCAAA4)
                    )
                }
            }
        }
        
        // 错误信息
        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE53935)
            )
        }
        
        // 试听按钮
        if (recordingState == RecordingState.COMPLETED && recordedFilePath != null && cloningState == CloningState.IDLE) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    recordedFilePath?.let { path ->
                        scope.launch {
                            try {
                                val bytes = File(path).readBytes()
                                audioPlayer.play(bytes)
                            } catch (e: Exception) {
                                errorMessage = "播放失败"
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("试听录音")
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 底部按钮
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 创建声音/下一步按钮
            Button(
                onClick = {
                    if (cloningState == CloningState.SUCCESS) {
                        onNext()
                    } else if (recordingState == RecordingState.COMPLETED && recordedFilePath != null) {
                        // 检查录音时长
                        if (recordingDuration < VoiceCloningService.MIN_DURATION_SECONDS) {
                            errorMessage = "录音时间不足，请至少录制10秒"
                            return@Button
                        }
                        
                        // 开始声音复刻
                        cloningState = CloningState.CLONING
                        errorMessage = null
                        
                        scope.launch {
                            val result = voiceCloningService.createVoice(
                                audioFile = File(recordedFilePath!!),
                                voicePrefix = elderName.take(8).replace(Regex("[^a-zA-Z0-9_]"), "").ifEmpty { "voice" },
                                familyDeviceId = familyDeviceId
                            )
                            result.fold(
                                onSuccess = { voiceId ->
                                    cloningState = CloningState.SUCCESS
                                    onVoiceCloned(voiceId)
                                },
                                onFailure = { e ->
                                    cloningState = CloningState.IDLE
                                    errorMessage = "声音创建失败：${e.message}"
                                }
                            )
                        }
                    }
                },
                enabled = !isLoading && (cloningState == CloningState.SUCCESS || 
                    (recordingState == RecordingState.COMPLETED && cloningState == CloningState.IDLE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB74D),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFBCAAA4)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        text = when (cloningState) {
                            CloningState.SUCCESS -> "下一步"
                            else -> "创建专属声音"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 跳过按钮
            if (cloningState != CloningState.SUCCESS) {
                TextButton(
                    onClick = onSkip,
                    enabled = !isLoading && cloningState != CloningState.CLONING,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "跳过，使用默认声音",
                        color = Color(0xFF8D6E63)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// 录音状态
private enum class RecordingState {
    IDLE, RECORDING, COMPLETED
}

// 声音复刻状态
private enum class CloningState {
    IDLE, CLONING, SUCCESS
}

/**
 * 步骤3: 显示配对码
 */
@Composable
fun PairingCodeStep(
    elderName: String,
    pairingCode: String,
    qrContent: String,
    hasClonedVoice: Boolean = false,
    onComplete: () -> Unit
) {
    // 生成二维码（使用包含长辈信息的内容）
    val qrCodeBitmap = remember(qrContent) {
        generateQRCode(qrContent)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "配置成功！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF388E3C)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 显示声音复刻状态
        if (hasClonedVoice) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "已创建专属声音，支持方言播报",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请让${elderName}扫描二维码或输入配对码",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF5D4037),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 二维码卡片
        Card(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (qrCodeBitmap != null) {
                    Image(
                        bitmap = qrCodeBitmap.asImageBitmap(),
                        contentDescription = "配对二维码",
                        modifier = Modifier.size(160.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "或输入配对码",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF8D6E63)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 配对码显示
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = pairingCode,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D4037),
                letterSpacing = 6.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 完成按钮
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFB74D),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "完成配置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 生成二维码
 */
fun generateQRCode(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK 
                    else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
