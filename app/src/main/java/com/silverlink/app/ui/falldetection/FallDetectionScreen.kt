package com.silverlink.app.ui.falldetection

import android.content.Context
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.ui.theme.AlertRed
import com.silverlink.app.ui.theme.SuccessGreen
import com.silverlink.app.ui.theme.WarmContainer
import com.silverlink.app.ui.theme.WarmPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallDetectionScreen(
    modifier: Modifier = Modifier,
    viewModel: FallDetectionViewModel = viewModel(),
    onNavigateToContacts: () -> Unit
) {
    val context = LocalContext.current
    val isDetectionEnabled by viewModel.isDetectionEnabled.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val hasPermissions by viewModel.hasRequiredPermissions.collectAsState()
    
    // 监听生命周期，每次回到界面刷新状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshServiceStatus()
    }

    // 权限请求启动器
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshServiceStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全守护", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. 状态卡片
            StatusCard(
                isEnabled = isDetectionEnabled,
                isServiceRunning = isServiceRunning,
                hasPermissions = hasPermissions,
                onToggle = { 
                    if (isDetectionEnabled) {
                        viewModel.disableDetection()
                    } else {
                        if (hasPermissions) {
                            viewModel.enableDetection()
                        } else {
                            permissionsLauncher.launch(viewModel.getRequiredPermissions())
                        }
                    }
                },
                onRequestPermissions = {
                    permissionsLauncher.launch(viewModel.getRequiredPermissions())
                }
            )

            // 2. 紧急联系人入口
            SettingsCard(
                title = "紧急联系人",
                subtitle = "管理跌倒时通知的亲友",
                icon = Icons.Default.Person,
                onClick = onNavigateToContacts
            )
            
            // 3. 灵敏度设置
            SensitivitySettingsCard()

            // 3. SOS 紧急按钮
            SOSSection(
                onSOS = {
                    // 直接拨打首个紧急联系人或120
                    // 这里简单处理：如果已授权，调用SOS逻辑（需在VM中实现，或直接拨号）
                    // 暂时实现为跳转拨号界面
                    val intent = Intent(Intent.ACTION_DIAL)
                    intent.data = Uri.parse("tel:120")
                    context.startActivity(intent)
                }
            )
            
            // 4. 说明信息
            InfoSection()
        }
    }
}

@Composable
private fun StatusCard(
    isEnabled: Boolean,
    isServiceRunning: Boolean,
    hasPermissions: Boolean,
    onToggle: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        if (isEnabled && isServiceRunning) SuccessGreen.copy(alpha = 0.1f) 
        else if (!hasPermissions) AlertRed.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.surface,
        label = "bgColor"
    )
    
    val borderColor by animateColorAsState(
        if (isEnabled && isServiceRunning) SuccessGreen.copy(alpha = 0.5f)
        else if (!hasPermissions) AlertRed.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outlineVariant,
        label = "borderColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnabled && isServiceRunning) "守护中" else "未开启守护",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled && isServiceRunning) SuccessGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnabled && isServiceRunning) "跌倒检测正在后台运行" else "开启后将自动检测跌倒并通知家人",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SuccessGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.scale(1.2f)
                )
            }

            if (!hasPermissions && isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("授予必要权限以开启服务")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(WarmContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = WarmPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SOSSection(onSOS: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "紧急求助",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击下方按钮立即拨打急救电话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onSOS,
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
                        ),
                        shape = CircleShape
                    ),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Call, 
                        contentDescription = "SOS", 
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                    Text(
                        "SOS",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "跌倒检测通过手机传感器识别异常动作。可能会受手机携带方式影响，建议将手机放置在裤口袋中以获得最佳效果。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun SensitivitySettingsCard() {
    val context = LocalContext.current
    val prefs = remember { 
        context.getSharedPreferences("silverlink_user_prefs", Context.MODE_PRIVATE) 
    }
    var selectedSensitivity by remember { 
        mutableIntStateOf(prefs.getInt("fall_detection_sensitivity", 1)) 
    }
    
    val sensitivityOptions = listOf("高灵敏度", "中灵敏度", "低灵敏度")
    val sensitivityDescriptions = listOf(
        "更容易触发警报，适合高风险人群",
        "平衡检测准确性，推荐使用",
        "减少误报，适合活跃用户"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(WarmContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = WarmPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "检测灵敏度",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "调整跌倒检测的敏感程度",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            sensitivityOptions.forEachIndexed { index, option ->
                val isSelected = selectedSensitivity == index
                val backgroundColor by animateColorAsState(
                    if (isSelected) WarmPrimary.copy(alpha = 0.1f) 
                    else Color.Transparent,
                    label = "bgColor"
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable {
                            selectedSensitivity = index
                            prefs.edit().putInt("fall_detection_sensitivity", index).apply()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (isSelected) WarmPrimary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) WarmPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = sensitivityDescriptions[index],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (index < sensitivityOptions.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
