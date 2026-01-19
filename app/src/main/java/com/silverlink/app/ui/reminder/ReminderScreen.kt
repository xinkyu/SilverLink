package com.silverlink.app.ui.reminder

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
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
        if (medications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "还没有添加药品哦", 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点刷新按钮同步，或点右下角添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(medications) { med ->
                    MedicationItem(
                        medication = med,
                        takenTimes = takenTimes[med.id].orEmpty(),
                        onToggleTime = { time -> viewModel.markMedicationTimeTaken(med, time) },
                        onEdit = { editingMedication = med },
                        onDelete = { viewModel.deleteMedication(med) }
                    )
                }
            }
        }

        // FAB 区域 - 三个按钮
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // 刷新按钮
            SmallFloatingActionButton(
                onClick = { viewModel.refresh() },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新同步")
                }
            }
            
            // 拍照识别按钮
            SmallFloatingActionButton(
                onClick = {
                    if (hasCameraPermission) {
                        launchCamera()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "拍照识别")
            }
            
            // 手动添加按钮
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(72.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加药品", modifier = Modifier.size(36.dp))
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (medication.isTakenToday) 
                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (medication.isTakenToday) 0.dp else 4.dp
        ),
        modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (medication.isTakenToday) 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = medication.dosage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 编辑按钮
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 删除按钮
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "删除", 
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 时间列表显示
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                medication.getTimeList().forEach { time ->
                    val isTaken = takenTimes.contains(time)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isTaken) SuccessGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isTaken) SuccessGreen else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        IconButton(
                            onClick = { if (!isTaken) onToggleTime(time) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isTaken) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = if (isTaken) "已服药" else "标记已服药",
                                tint = if (isTaken) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
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
