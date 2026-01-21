package com.silverlink.app.feature.falldetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserPreferences.FallDetectionSensitivity
import com.silverlink.app.feature.location.LocationTrackingService
import com.silverlink.app.feature.proactive.ProactiveInteractionService
import kotlinx.coroutines.launch

private enum class EnableTarget {
    FALL,
    PROACTIVE
}

/**
 * 跌倒检测设置屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallDetectionScreen(
    onNavigateToContacts: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences.getInstance(context) }
    
    // 状态
    var isEnabled by remember { mutableStateOf(userPreferences.isFallDetectionEnabled()) }
    var sensitivity by remember { mutableStateOf(userPreferences.getFallDetectionSensitivity()) }

    var isProactiveEnabled by remember { mutableStateOf(userPreferences.isProactiveInteractionEnabled()) }
    
    // 位置共享状态
    var isLocationSharingEnabled by remember { mutableStateOf(userPreferences.isLocationSharingEnabled()) }
    
    // 需要请求的权限列表
    val requiredPermissions = remember {
        mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    
    // 是否显示后台权限提示对话框
    var showBackgroundPermissionDialog by remember { mutableStateOf(false) }
    
    var pendingEnableTarget by remember { mutableStateOf<EnableTarget?>(null) }

    fun enableFallDetection() {
        isEnabled = true
        userPreferences.setFallDetectionEnabled(true)
        FallDetectionService.start(context)
        Toast.makeText(context, "安全守护已开启", Toast.LENGTH_SHORT).show()

        // ★ 弹出提示，引导用户开启后台/锁屏显示权限
        showBackgroundPermissionDialog = true
    }

    fun enableProactiveInteraction() {
        isProactiveEnabled = true
        userPreferences.setProactiveInteractionEnabled(true)
        ProactiveInteractionService.start(context)
        Toast.makeText(context, "久坐守护已开启", Toast.LENGTH_SHORT).show()
    }

    // 权限请求结果
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            when (pendingEnableTarget) {
                EnableTarget.FALL -> enableFallDetection()
                EnableTarget.PROACTIVE -> enableProactiveInteraction()
                null -> Unit
            }
            pendingEnableTarget = null
        } else {
            // 部分权限被拒绝
            val message = when (pendingEnableTarget) {
                EnableTarget.PROACTIVE -> "需要授权相关权限才能正常使用久坐守护功能"
                else -> "需要授权相关权限才能正常使用跌倒检测功能"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            pendingEnableTarget = null
        }
    }
    
    // 检查并请求权限
    fun checkAndRequestPermissions(target: EnableTarget) {
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isEmpty()) {
            when (target) {
                EnableTarget.FALL -> enableFallDetection()
                EnableTarget.PROACTIVE -> enableProactiveInteraction()
            }
        } else {
            // 请求缺失的权限
            pendingEnableTarget = target
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全守护") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFF5F5F5)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 功能开关卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isEnabled) Color(0xFF43A047) else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "跌倒检测",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isEnabled) "正在守护中" else "功能已关闭",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isEnabled) Color(0xFF43A047) else Color.Gray
                                )
                            }
                        }
                        
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    // 开启时检查权限
                                    checkAndRequestPermissions(EnableTarget.FALL)
                                } else {
                                    // 关闭时直接停止
                                    isEnabled = false
                                    userPreferences.setFallDetectionEnabled(false)
                                    FallDetectionService.stop(context)
                                }
                            }
                        )
                    }
                    
                    if (isEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "开启后，系统将持续监测异常摔倒情况。检测到跌倒后15秒无响应，将自动通知紧急联系人。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            // 1.1 久坐无响应守护开关
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (isProactiveEnabled) Color(0xFF43A047) else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "久坐无响应守护",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isProactiveEnabled) "正在守护中" else "功能已关闭",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isProactiveEnabled) Color(0xFF43A047) else Color.Gray
                                )
                            }
                        }

                        Switch(
                            checked = isProactiveEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    checkAndRequestPermissions(EnableTarget.PROACTIVE)
                                } else {
                                    isProactiveEnabled = false
                                    userPreferences.setProactiveInteractionEnabled(false)
                                    ProactiveInteractionService.stop(context)
                                }
                            }
                        )
                    }

                    if (isProactiveEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "3小时无移动将自动唤醒，两次无响应会弹出紧急提醒并自动拨打电话、发送短信给紧急联系人。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            // 2. 灵敏度设置
            if (isEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "检测灵敏度",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        
                        SensitivityOption(
                            title = "高灵敏度",
                            description = "容易触发，适合独居可以通过语音取消的老人",
                            isSelected = sensitivity == FallDetectionSensitivity.HIGH,
                            onClick = {
                                sensitivity = FallDetectionSensitivity.HIGH
                                userPreferences.setFallDetectionSensitivity(FallDetectionSensitivity.HIGH)
                                // 重启服务以应用新设置
                                FallDetectionService.start(context)
                            }
                        )
                        
                        SensitivityOption(
                            title = "中等灵敏度（推荐）",
                            description = "平衡了误报和漏报，适合大多数情况",
                            isSelected = sensitivity == FallDetectionSensitivity.MEDIUM,
                            onClick = {
                                sensitivity = FallDetectionSensitivity.MEDIUM
                                userPreferences.setFallDetectionSensitivity(FallDetectionSensitivity.MEDIUM)
                                FallDetectionService.start(context)
                            }
                        )
                        
                        SensitivityOption(
                            title = "低灵敏度",
                            description = "仅检测剧烈摔倒，减少误报干扰",
                            isSelected = sensitivity == FallDetectionSensitivity.LOW,
                            onClick = {
                                sensitivity = FallDetectionSensitivity.LOW
                                userPreferences.setFallDetectionSensitivity(FallDetectionSensitivity.LOW)
                                FallDetectionService.start(context)
                            }
                        )
                    }
                }
            }
            
            // 3. 位置共享开关
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isLocationSharingEnabled) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "位置共享",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isLocationSharingEnabled) "家人可查看您的位置" else "功能已关闭",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isLocationSharingEnabled) Color(0xFF4CAF50) else Color.Gray
                                )
                            }
                        }
                        
                        Switch(
                            checked = isLocationSharingEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    // 开启位置共享
                                    if (LocationTrackingService.hasLocationPermission(context)) {
                                        isLocationSharingEnabled = true
                                        userPreferences.setLocationSharingEnabled(true)
                                        LocationTrackingService.start(context)
                                        Toast.makeText(context, "位置共享已开启", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // 需要位置权限，先开启跌倒检测可以额外请求
                                        Toast.makeText(context, "请先开启跌倒检测并授权位置权限", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    // 关闭位置共享
                                    isLocationSharingEnabled = false
                                    userPreferences.setLocationSharingEnabled(false)
                                    LocationTrackingService.stop(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4CAF50)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "开启后，每5分钟更新一次位置，家人可以在App中查看您的实时位置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            // 4. 紧急联系人入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onNavigateToContacts() },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = null,
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "紧急联系人",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "管理接收求救信息的家人",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // 权限引导对话框
        if (showBackgroundPermissionDialog) {
            BackgroundPermissionDialog(
                onDismiss = { showBackgroundPermissionDialog = false },
                onGoToSettings = {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开设置页面，请手动前往设置", Toast.LENGTH_SHORT).show()
                    }
                    showBackgroundPermissionDialog = false
                }
            )
        }
    }
}

@Composable
fun BackgroundPermissionDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚠️ 重要权限提醒",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("为了确保熄屏时能正常弹出跌倒警报，请务必开启以下权限：")
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("• 后台弹出界面 / 锁屏显示", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "请在设置 -> 权限管理中找到并开启此权限，否则只能听到声音无法看到界面。", 
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGoToSettings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("去设置开启")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("我已知晓")
            }
        }
    )
}


@Composable
private fun SensitivityOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                color = if (isSelected) Color(0xFFE3F2FD) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null, // Handled by Row click
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF1976D2))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF1976D2) else Color.Black
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
