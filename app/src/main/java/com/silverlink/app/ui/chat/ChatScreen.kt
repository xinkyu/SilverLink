package com.silverlink.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.silverlink.app.ui.components.UnifiedTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.data.local.ConversationEntity
import com.silverlink.app.data.local.MemoryRecordEntity
import com.silverlink.app.data.local.UserProfileMemoryEntity
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.feature.chat.RagConfig
import com.silverlink.app.feature.chat.RagDebugSnapshot
import com.silverlink.app.ui.theme.CalmContainer
import com.silverlink.app.ui.theme.CalmOnSecondary
import com.silverlink.app.ui.theme.WarmContainer
import com.silverlink.app.ui.theme.WarmOnPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onNavigateToGallery: () -> Unit = {},
    onNavigateToMedicationAdd: () -> Unit = {},
    onNavigateToMedicationFind: () -> Unit = {},
    onNavigateToMemoryQuiz: () -> Unit = {},
    onNavigateToMoodAnalysis: (String) -> Unit = {},
    onNavigateToSafetySettings: () -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferences.getInstance(context) }
    val userConfig by userPrefs.userConfig.collectAsState()
    val assistantName = userConfig.assistantName.ifBlank { "小银" }
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val currentEmotion by viewModel.currentEmotion.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val memoryRecords by viewModel.memoryRecords.collectAsState()
    val profileMemories by viewModel.profileMemories.collectAsState()
    val ragConfig by viewModel.ragConfig.collectAsState()
    val ragDebugEnabled by viewModel.ragDebugEnabled.collectAsState()
    val ragDebugSnapshot by viewModel.ragDebugSnapshot.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showCallScreen by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 会话列表底部弹窗状态
    var showConversationSheet by remember { mutableStateOf(false) }
    var showMemoryCenterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var memoryInputText by remember { mutableStateOf("") }

    // 录音权限状态
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // 相机权限状态
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // 显示实时相机界面
    var showPillCameraScreen by remember { mutableStateOf(false) }

    // 录音权限请求
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            Toast.makeText(context, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 相机权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            showPillCameraScreen = true
        } else {
            Toast.makeText(context, "需要相机权限才能拍照找药", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 显示实时相机界面
    if (showPillCameraScreen) {
        com.silverlink.app.ui.reminder.PillCheckCameraScreen(
            onCapture = { bitmap ->
                showPillCameraScreen = false
                viewModel.checkPill(bitmap)
            },
            onDismiss = {
                showPillCameraScreen = false
            }
        )
        return
    }

    // 显示错误提示
    LaunchedEffect(voiceState) {
        if (voiceState is VoiceState.Error) {
            Toast.makeText(context, (voiceState as VoiceState.Error).message, Toast.LENGTH_SHORT).show()
            viewModel.resetVoiceState()
        }
    }

    LaunchedEffect(showCallScreen) {
        if (!showCallScreen) {
            viewModel.stopRealtimeConversation()
        }
    }

    // 首次请求录音权限
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(showCallScreen) {
        if (!showCallScreen) {
            viewModel.stopRealtimeConversation()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // 监听语音命令意图 - 检测到后执行对应导航
    val voiceCommandIntent by viewModel.voiceCommandIntent.collectAsState()
    LaunchedEffect(voiceCommandIntent) {
        when (val intent = voiceCommandIntent) {
            is ChatViewModel.VoiceCommandIntent.None -> { /* 不处理 */ }
            is ChatViewModel.VoiceCommandIntent.OpenRealtimeCall -> {
                showCallScreen = true
                viewModel.clearVoiceCommandIntent()
            }
            is ChatViewModel.VoiceCommandIntent.OpenGallery -> {
                onNavigateToGallery()
                viewModel.clearVoiceCommandIntent()
            }
            is ChatViewModel.VoiceCommandIntent.SearchPhotos -> {
                onNavigateToGallery()
                viewModel.clearVoiceCommandIntent()
            }
            is ChatViewModel.VoiceCommandIntent.OpenMedicationAdd -> {
                onNavigateToMedicationAdd()
                viewModel.clearVoiceCommandIntent()
            }
            is ChatViewModel.VoiceCommandIntent.OpenMedicationFind -> {
                // 直接打开相机拍照找药
                if (hasCameraPermission) {
                    showPillCameraScreen = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                viewModel.clearVoiceCommandIntent()
            }
            is ChatViewModel.VoiceCommandIntent.OpenMemoryQuiz -> {
                onNavigateToMemoryQuiz()
                viewModel.clearVoiceCommandIntent()
            }
            is ChatViewModel.VoiceCommandIntent.OpenMoodAnalysis -> {
                onNavigateToMoodAnalysis(intent.period)
                viewModel.clearVoiceCommandIntent()
            }
            is ChatViewModel.VoiceCommandIntent.OpenSafetySettings -> {
                onNavigateToSafetySettings()
                viewModel.clearVoiceCommandIntent()
            }
            is ChatViewModel.VoiceCommandIntent.OpenContacts -> {
                onNavigateToContacts()
                viewModel.clearVoiceCommandIntent()
            }
            // 设置类和联系人添加类命令已在 ViewModel 中直接处理，无需导航
            else -> { 
                viewModel.clearVoiceCommandIntent()
            }
        }
    }

    if (showCallScreen) {
        RealtimeCallScreen(
            assistantName = assistantName,
            conversationState = conversationState,
            partialTranscript = partialTranscript,
            onEndCall = { showCallScreen = false },
            onStartCall = {
                if (hasAudioPermission) {
                    viewModel.startRealtimeConversation()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        UnifiedTopBar(
            title = assistantName,
            icon = Icons.Default.Face,
            titleSuffix = {
                EmotionBadge(emotion = currentEmotion)
            },
            rightContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ttsState is TtsState.Speaking) {
                        Text(
                            text = "🔊",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { 
                        if (hasCameraPermission) {
                            showPillCameraScreen = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "找药",
                            tint = Color.Gray
                        )
                    }
                    IconButton(onClick = { viewModel.createNewConversation() }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "新对话",
                            tint = Color.Gray
                        )
                    }
                    IconButton(onClick = { showConversationSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "历史会话",
                            tint = Color.Gray
                        )
                    }
                    IconButton(onClick = {
                        viewModel.refreshMemoryCenter()
                        showMemoryCenterSheet = true
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = "记忆中心",
                            tint = Color.Gray
                        )
                    }
                }
            }
        )
        if (showCallScreen) {
            RealtimeCallScreen(
                assistantName = assistantName,
                conversationState = conversationState,
                partialTranscript = partialTranscript,
                onEndCall = { showCallScreen = false },
                onStartCall = {
                    if (hasAudioPermission) {
                        viewModel.startRealtimeConversation()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        } else {
            // Chat History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (partialTranscript.isNotBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "正在听：$partialTranscript",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(messages) { message ->
                    ChatBubble(message)
                }
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Input Area
            ChatInputArea(
                text = inputText,
                voiceState = voiceState,
                onTextChanged = { inputText = it },
                onSendClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                        keyboardController?.hide()
                    }
                },
                onVoiceStart = {
                    if (hasAudioPermission) {
                        viewModel.startRecording()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onVoiceEnd = {
                    viewModel.stopRecordingAndRecognize()
                },
                onVoiceCallClick = {
                    showCallScreen = true
                }
            )
        }
    }
    
    // 历史会话列表底部弹窗
    if (showConversationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConversationSheet = false },
            sheetState = sheetState
        ) {
            ConversationListSheet(
                conversations = conversations,
                currentConversationId = currentConversationId,
                onConversationClick = { conversationId ->
                    viewModel.switchConversation(conversationId)
                    scope.launch {
                        sheetState.hide()
                        showConversationSheet = false
                    }
                },
                onDeleteClick = { conversationId ->
                    viewModel.deleteConversation(conversationId)
                },
                onNewConversationClick = {
                    viewModel.createNewConversation()
                    scope.launch {
                        sheetState.hide()
                        showConversationSheet = false
                    }
                }
            )
        }
    }

    if (showMemoryCenterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMemoryCenterSheet = false },
            sheetState = sheetState
        ) {
            MemoryCenterSheet(
                memoryRecords = memoryRecords,
                profileMemories = profileMemories,
                ragConfig = ragConfig,
                ragDebugEnabled = ragDebugEnabled,
                ragDebugSnapshot = ragDebugSnapshot,
                memoryInputText = memoryInputText,
                onMemoryInputChanged = { memoryInputText = it },
                onAddMemory = {
                    viewModel.addMemoryFromUi(memoryInputText)
                    memoryInputText = ""
                },
                onDeleteMemory = { viewModel.deleteLongTermMemory(it) },
                onDeleteProfile = { viewModel.deleteStructuredProfileMemory(it) },
                onClearAll = { viewModel.clearAllLongTermMemories() },
                onRagConfigChanged = { viewModel.updateRagConfig(it) },
                onRagDebugEnabledChanged = { viewModel.setRagDebugEnabled(it) }
            )
        }
    }
}

@Composable
private fun MemoryCenterSheet(
    memoryRecords: List<MemoryRecordEntity>,
    profileMemories: List<UserProfileMemoryEntity>,
    ragConfig: RagConfig,
    ragDebugEnabled: Boolean,
    ragDebugSnapshot: RagDebugSnapshot?,
    memoryInputText: String,
    onMemoryInputChanged: (String) -> Unit,
    onAddMemory: () -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onClearAll: () -> Unit,
    onRagConfigChanged: (RagConfig) -> Unit,
    onRagDebugEnabledChanged: (Boolean) -> Unit
) {
    var selectedProfile by remember { mutableStateOf<UserProfileMemoryEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("记忆中心", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClearAll) {
                    Text("清空")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = memoryInputText,
                onValueChange = onMemoryInputChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("添加一条长期记忆") },
                maxLines = 2,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onAddMemory,
                    enabled = memoryInputText.isNotBlank()
                ) {
                    Text("添加记忆")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MemoryCenterCard(title = "结构化画像") {
                if (profileMemories.isEmpty()) {
                    Text("暂时还没有结构化画像。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    profileMemories.forEachIndexed { index, profile ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProfile = profile }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = profile.key,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = profile.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "点击查看详情",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDeleteProfile(profile.key) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除画像")
                            }
                        }
                    }
                }
            }
        }

        item {
            MemoryCenterCard(title = "长期记忆") {
                if (memoryRecords.isEmpty()) {
                    Text("暂时还没有长期记忆。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    memoryRecords.forEachIndexed { index, memory ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = memory.content,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 8.dp, end = 8.dp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { onDeleteMemory(memory.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除记忆")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    selectedProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { selectedProfile = null },
            title = { Text(profile.key) },
            text = { Text(profile.value) },
            confirmButton = {
                TextButton(onClick = { selectedProfile = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun MemoryCenterCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF0F172A))
            content()
        }
    }
}
@Composable
fun ChatBubble(message: Message) {
    val isUser = message.role == "user"
    
    // Polished Colors
    val bubbleColor = if (isUser) CalmContainer else WarmContainer
    val textColor = if (isUser) CalmOnSecondary else WarmOnPrimary
    
    // Unified large rounded corners (24dp)
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 4.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = shape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun ChatInputArea(
    text: String,
    voiceState: VoiceState,
    onTextChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: () -> Unit,
    onVoiceCallClick: () -> Unit
) {
    var isVoiceMode by remember { mutableStateOf(false) }
    val isRecording = voiceState is VoiceState.Recording
    val isRecognizing = voiceState is VoiceState.Recognizing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding() // Keep this to respect system nav bar
            .padding(horizontal = 12.dp, vertical = 8.dp) // Reduced padding
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switch Button (Keyboard <-> Mic) - Larger for elderly users
            IconButton(
                onClick = { isVoiceMode = !isVoiceMode },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isVoiceMode) Icons.Filled.Keyboard else Icons.Filled.Mic,
                    contentDescription = if (isVoiceMode) "Switch to Keyboard" else "Switch to Voice",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isVoiceMode) {
                IconButton(
                    onClick = onVoiceCallClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "语音通话",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Press-to-Talk Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp) // Slightly taller for easier access
                        .background(
                            color = when {
                                isRecording -> MaterialTheme.colorScheme.error
                                isRecognizing -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = RoundedCornerShape(32.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onVoiceStart()
                                    tryAwaitRelease()
                                    onVoiceEnd()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isRecognizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onTertiary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在识别...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        } else if (isRecording) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onError
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "松开发送",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            Text(
                                text = "按住 说话",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Call Button in voice mode
                IconButton(
                    onClick = onVoiceCallClick,
                    modifier = Modifier
                        .size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "语音通话",
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // Text Input
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入想说的话…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendClick() })
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button - Larger for elderly users
                IconButton(
                    onClick = onSendClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = text.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

}

/**
 * 情绪标签组件 - 显示检测到的用户情绪
 */
@Composable
fun EmotionBadge(emotion: Emotion) {
    val (emoji, color) = when (emotion) {
        Emotion.HAPPY -> "😊" to Color(0xFF4CAF50)
        Emotion.SAD -> "😢" to Color(0xFF2196F3)
        Emotion.ANGRY -> "😤" to Color(0xFFFF5722)
        Emotion.ANXIOUS -> "😰" to Color(0xFFFF9800)
        Emotion.NEUTRAL -> "😐" to Color.Gray
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = emotion.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

/**
 * 历史会话列表弹窗
 */
@Composable
fun ConversationListSheet(
    conversations: List<ConversationEntity>,
    currentConversationId: Long?,
    onConversationClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onNewConversationClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "历史会话",
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = onNewConversationClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("新对话")
            }
        }
        
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无历史会话",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        isSelected = conversation.id == currentConversationId,
                        onClick = { onConversationClick(conversation.id) },
                        onDeleteClick = { showDeleteDialog = conversation.id }
                    )
                }
            }
        }
    }
    
    // 删除确认对话框
    showDeleteDialog?.let { conversationId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除会话") },
            text = { Text("确定要删除这个会话吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick(conversationId)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 单个会话项
 */
@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val timeText = remember(conversation.updatedAt) {
        dateFormat.format(Date(conversation.updatedAt))
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
