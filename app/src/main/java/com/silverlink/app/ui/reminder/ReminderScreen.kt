package com.silverlink.app.ui.reminder

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import com.silverlink.app.ui.components.UnifiedTopBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.data.local.entity.Medication
import com.silverlink.app.feature.reminder.RecognizedMedication
import com.silverlink.app.ui.theme.SuccessGreen
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    modifier: Modifier = Modifier,
    viewModel: ReminderViewModel = viewModel()
) {
    val context = LocalContext.current
    val medications by viewModel.medications.collectAsState()
    val recognitionState by viewModel.recognitionState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val takenTimes by viewModel.takenTimes.collectAsState()
    
    val todayTotalTasks by viewModel.todayTotalTasks.collectAsState()
    val todayCompletedTasks by viewModel.todayCompletedTasks.collectAsState()
    val weeklyPunctualityRate by viewModel.weeklyPunctualityRate.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMedication by remember { mutableStateOf<Medication?>(null) }
    
    // 刷新状态
    val isRefreshing = syncState is SyncState.Syncing
    
    // 相机拍照相关
    var photoFile by remember { mutableStateOf<File?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // 相机权限请求
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    
    // 拍照启动器
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            val bitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath)
            if (bitmap != null) {
                capturedBitmap = bitmap
                viewModel.recognizeMedication(bitmap)
            }
        }
    }
    
    // 启动相机拍照
    fun launchCamera() {
        val file = File(context.cacheDir, "medication_photo_${System.currentTimeMillis()}.jpg")
        photoFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        cameraLauncher.launch(uri)
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            UnifiedTopBar(
                title = "吃药提醒",
                icon = Icons.Default.Notifications
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), shape = CircleShape)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新同步", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFFF3E0), shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加", tint = Color(0xFFFF8A00))
                    }
                }
            }

            // Big Orange Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .background(Color(0xFFFF8A00), shape = RoundedCornerShape(20.dp))
                    .clickable { 
                        if (hasCameraPermission) {
                            launchCamera()
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("智能识别药瓶", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("对准药瓶，自动识别并添加提醒", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                }
            }

            // Stats Cards Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1
                Box(modifier = Modifier.weight(1f).background(Color.White, RoundedCornerShape(16.dp)).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$todayTotalTasks", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B00))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "今日任务", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                // Card 2
                Box(modifier = Modifier.weight(1f).background(Color.White, RoundedCornerShape(16.dp)).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$todayCompletedTasks", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00C853))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "已完成", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                // Card 3
                Box(modifier = Modifier.weight(1f).background(Color.White, RoundedCornerShape(16.dp)).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$weeklyPunctualityRate%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2962FF))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "本周准时率", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            if (medications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "还没有添加药品哦", 
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    medications.forEach { med ->
                        MedicationItem(
                            medication = med,
                            takenTimes = takenTimes[med.id].orEmpty(),
                            onToggleTime = { time -> viewModel.markMedicationTimeTaken(med, time) },
                            onEdit = { editingMedication = med },
                            onDelete = { viewModel.deleteMedication(med) }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }



        // Add Dialog
        if (showAddDialog) {
            MedicationDialog(
                title = "添加药品",
                initialName = "",
                initialDosage = "",
                initialTimes = emptyList(),
                onDismiss = { showAddDialog = false },
                onConfirm = { name, dosage, times ->
                    viewModel.addMedication(name, dosage, times)
                    showAddDialog = false
                }
            )
        }

        // Edit Dialog
        editingMedication?.let { medication ->
            MedicationDialog(
                title = "编辑药品",
                initialName = medication.name,
                initialDosage = medication.dosage,
                initialTimes = medication.getTimeList(),
                onDismiss = { editingMedication = null },
                onConfirm = { name, dosage, times ->
                    viewModel.updateMedication(medication, name, dosage, times)
                    editingMedication = null
                }
            )
        }

        // 识别状态对话框
        when (val state = recognitionState) {
            is RecognitionState.Loading -> {
                LoadingDialog()
            }
            is RecognitionState.Success -> {
                RecognitionResultDialog(
                    recognized = state.medication,
                    onDismiss = { viewModel.resetRecognitionState() },
                    onConfirm = { name, dosage, times ->
                        viewModel.saveRecognizedMedication(name, dosage, times)
                    }
                )
            }
            is RecognitionState.Error -> {
                ErrorDialog(
                    message = state.message,
                    onDismiss = { viewModel.resetRecognitionState() }
                )
            }
            is RecognitionState.Idle -> { /* Nothing to show */ }
        }
    }
    
    // 请求相机权限（首次）
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }
}

@Composable
fun LoadingDialog() {
    Dialog(onDismissRequest = { }) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "AI 正在识别药品...",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请稍候",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecognitionResultDialog(
    recognized: RecognizedMedication,
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(recognized.name) }
    var dosage by remember { mutableStateOf(recognized.dosage) }
    val times = remember { mutableStateListOf<String>().also { it.addAll(recognized.times) } }
    
    var showTimePicker by remember { mutableStateOf(false) }
    var pickerHour by remember { mutableStateOf(8) }
    var pickerMinute by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text("识别结果", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column {
                Text(
                    text = "AI 已识别到以下药品信息，请确认或修改：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("药名") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("剂量") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "服药时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (times.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        times.forEachIndexed { index, time ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(time, fontSize = 16.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { times.removeAt(index) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除时间",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                TextButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加时间")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && times.isNotEmpty()) {
                        onConfirm(name, dosage, times.toList())
                    }
                },
                enabled = name.isNotBlank() && times.isNotEmpty()
            ) {
                Text("保存", fontSize = 18.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 18.sp)
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = pickerHour,
            initialMinute = pickerMinute,
            onConfirm = { hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                if (!times.contains(timeStr)) {
                    times.add(timeStr)
                    times.sortBy { it }
                }
                pickerHour = hour
                pickerMinute = minute
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("识别失败") },
        text = { 
            Text(
                text = message,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("知道了")
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationItem(
    medication: Medication,
    takenTimes: Set<String>,
    onToggleTime: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored Indicator Line
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(32.dp)
                        .background(
                            color = if (medication.isTakenToday) SuccessGreen else Color(0xFFFF8A00),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = medication.dosage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                
                // 行动按钮组合
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .clickable { onEdit() }
                    ) {
                        Icon(
                            Icons.Default.Edit, 
                            contentDescription = "编辑",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2))
                            .clickable { onDelete() }
                    ) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "删除", 
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // 服药时间列表改进UI
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                medication.getTimeList().forEach { time ->
                    val isTaken = takenTimes.contains(time)
                    val bgColor = if (isTaken) SuccessGreen.copy(alpha = 0.15f) else Color(0xFFF8FAFC)
                    val contentColor = if (isTaken) SuccessGreen else Color(0xFF64748B)

                    Surface(
                        onClick = { if (!isTaken) onToggleTime(time) },
                        color = bgColor,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isTaken) Icons.Default.CheckCircle else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = time,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationDialog(
    title: String,
    initialName: String,
    initialDosage: String,
    initialTimes: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var dosage by remember { mutableStateOf(initialDosage) }
    val times = remember { mutableStateListOf<String>().also { it.addAll(initialTimes) } }
    
    var showTimePicker by remember { mutableStateOf(false) }
    var pickerHour by remember { mutableStateOf(8) }
    var pickerMinute by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("药名") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("剂量 (如: 1片)") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 时间列表
                Text(
                    text = "服药时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (times.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        times.forEachIndexed { index, time ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(time, fontSize = 16.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { times.removeAt(index) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除时间",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // 添加时间按钮
                Button(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加时间", fontSize = 18.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && times.isNotEmpty()) {
                        onConfirm(name, dosage, times.toList())
                    }
                },
                enabled = name.isNotBlank() && times.isNotEmpty(),
                modifier = Modifier.height(48.dp)
            ) {
                Text("保存", fontSize = 18.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 18.sp)
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = pickerHour,
            initialMinute = pickerMinute,
            onConfirm = { hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                if (!times.contains(timeStr)) {
                    times.add(timeStr)
                    times.sortBy { it } // 按时间排序
                }
                pickerHour = hour
                pickerMinute = minute
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberPickerView(
                    value = hour,
                    range = 0..23,
                    onValueChange = { hour = it }
                )
                Text(text = ":", style = MaterialTheme.typography.headlineMedium)
                NumberPickerView(
                    value = minute,
                    range = 0..59,
                    onValueChange = { minute = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute) }) {
                Text("确定", fontSize = 18.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 18.sp)
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun NumberPickerView(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                minValue = range.first
                maxValue = range.last
                this.value = value
                setOnValueChangedListener { _, _, newVal ->
                    onValueChange(newVal)
                }
            }
        },
        update = { picker ->
            if (picker.value != value) {
                picker.value = value
            }
        },
        modifier = Modifier
            .width(96.dp)
            .height(120.dp)
    )
}
