package com.silverlink.app.ui.family

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.data.local.GeofenceBoundaryStatus
import com.silverlink.app.ui.components.HealthTopBar
import com.silverlink.app.ui.components.LocationCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyLocationScreen(
    viewModel: FamilyLocationViewModel = viewModel()
) {
    val context = LocalContext.current
    val isPaired by viewModel.isPaired.collectAsState()
    val isLocationLoading by viewModel.isLocationLoading.collectAsState()
    val elderLocation by viewModel.elderLocation.collectAsState()
    val locationHistory by viewModel.locationHistory.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val evaluation by viewModel.evaluation.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    var centerLatitudeInput by remember { mutableStateOf("") }
    var centerLongitudeInput by remember { mutableStateOf("") }
    var radiusInput by remember { mutableStateOf("") }

    LaunchedEffect(settings.centerLatitude, settings.centerLongitude, settings.radiusMeters) {
        centerLatitudeInput = settings.centerLatitude?.toString().orEmpty()
        centerLongitudeInput = settings.centerLongitude?.toString().orEmpty()
        radiusInput = settings.radiusMeters.toInt().toString()
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToastMessage()
        }
    }

    val familyPrimary = Color(0xFF3F51B5)
    val statusColor = when (evaluation.stableStatus) {
        GeofenceBoundaryStatus.INSIDE -> Color(0xFF2E7D32)
        GeofenceBoundaryStatus.OUTSIDE -> Color(0xFFC62828)
        GeofenceBoundaryStatus.UNKNOWN -> Color(0xFF546E7A)
    }

    Scaffold(
        topBar = {
            HealthTopBar(
                title = "位置守护",
                onRefresh = {
                    viewModel.clearErrorMessage()
                    viewModel.refreshLocation()
                },
                primaryColor = familyPrimary
            )
        }
    ) { innerPadding ->
        if (!isPaired) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "尚未配对长辈，暂时无法查看位置。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                StatusSummaryCard(
                    enabled = settings.enabled,
                    statusLabel = viewModel.currentStatusLabel(),
                    distanceSummary = viewModel.distanceSummary(),
                    pendingLabel = viewModel.pendingStatusLabel(),
                    pendingMinutes = if (evaluation.pendingDurationMillis > 0L) {
                        (evaluation.pendingDurationMillis / 60_000L).toInt()
                    } else {
                        null
                    },
                    uncertainMessage = if (evaluation.isUncertain) evaluation.suppressReason else null,
                    statusColor = statusColor,
                    onToggle = viewModel::updateMonitoringEnabled
                )

                LocationCard(
                    location = elderLocation,
                    isLoading = isLocationLoading,
                    onRefresh = { viewModel.refreshLocation() },
                    onViewMap = elderLocation?.let { location ->
                        {
                            Toast.makeText(context, "正在打开地图...", Toast.LENGTH_SHORT).show()

                            val geoUri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(长辈位置)")
                            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            try {
                                context.startActivity(mapIntent)
                            } catch (_: Exception) {
                                val webUri = Uri.parse("https://uri.amap.com/marker?position=${location.longitude},${location.latitude}&name=长辈位置")
                                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(webIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "没有可用的地图或浏览器应用", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )

                FenceSetupCard(
                    latitudeInput = centerLatitudeInput,
                    longitudeInput = centerLongitudeInput,
                    radiusInput = radiusInput,
                    onLatitudeChange = { centerLatitudeInput = it },
                    onLongitudeChange = { centerLongitudeInput = it },
                    onRadiusChange = { radiusInput = it },
                    onUseCurrentLocation = viewModel::useLatestLocationAsFenceCenter,
                    onSave = {
                        viewModel.saveFenceDefinition(
                            latitudeInput = centerLatitudeInput,
                            longitudeInput = centerLongitudeInput,
                            radiusInput = radiusInput
                        )
                    }
                )

                AlertStrategyCard(
                    notifyOnExit = settings.notifyOnExit,
                    notifyOnEnter = settings.notifyOnEnter,
                    dwellMinutes = settings.dwellMinutes,
                    quietHoursEnabled = settings.quietHoursEnabled,
                    lowFrequencyEnabled = settings.lowFrequencyEnabled,
                    onNotifyOnExitChange = viewModel::updateNotifyOnExit,
                    onNotifyOnEnterChange = viewModel::updateNotifyOnEnter,
                    onDwellChange = viewModel::updateDwellMinutes,
                    onQuietHoursChange = viewModel::updateQuietHoursEnabled,
                    onLowFrequencyChange = viewModel::updateLowFrequencyEnabled
                )

                if (!errorMessage.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE65100)
                        )
                    }
                }

                if (locationHistory.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "轨迹说明",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "当前保留最近 2 小时内 ${locationHistory.size} 个位置点。守护判断会结合定位精度和 ${settings.dwellMinutes} 分钟连续超界时间，避免刚越界就提醒。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatusSummaryCard(
    enabled: Boolean,
    statusLabel: String,
    distanceSummary: String,
    pendingLabel: String?,
    pendingMinutes: Int?,
    uncertainMessage: String?,
    statusColor: Color,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = statusColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "防走失守护",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (enabled) "已开启，持续守护位置变化" else "已关闭，不会触发守护提醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = distanceSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!pendingLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (pendingMinutes != null) "$pendingLabel，已持续约 ${pendingMinutes + 1} 分钟" else pendingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6A1B9A)
                )
            }

            if (!uncertainMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = uncertainMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF546E7A)
                )
            }
        }
    }
}

@Composable
private fun FenceSetupCard(
    latitudeInput: String,
    longitudeInput: String,
    radiusInput: String,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = Color(0xFF1565C0)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "守护中心与守护半径",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = latitudeInput,
                onValueChange = onLatitudeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("守护中心纬度") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3F51B5),
                    unfocusedBorderColor = Color(0xFFD0D7E2),
                    focusedContainerColor = Color(0xFFF8FAFF),
                    unfocusedContainerColor = Color(0xFFF8FAFF)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = longitudeInput,
                onValueChange = onLongitudeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("守护中心经度") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3F51B5),
                    unfocusedBorderColor = Color(0xFFD0D7E2),
                    focusedContainerColor = Color(0xFFF8FAFF),
                    unfocusedContainerColor = Color(0xFFF8FAFF)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = radiusInput,
                onValueChange = onRadiusChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("守护半径（米）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3F51B5),
                    unfocusedBorderColor = Color(0xFFD0D7E2),
                    focusedContainerColor = Color(0xFFF8FAFF),
                    unfocusedContainerColor = Color(0xFFF8FAFF)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onUseCurrentLocation) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("设为当前位置")
                }
                Button(onClick = onSave) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun AlertStrategyCard(
    notifyOnExit: Boolean,
    notifyOnEnter: Boolean,
    dwellMinutes: Int,
    quietHoursEnabled: Boolean,
    lowFrequencyEnabled: Boolean,
    onNotifyOnExitChange: (Boolean) -> Unit,
    onNotifyOnEnterChange: (Boolean) -> Unit,
    onDwellChange: (Int) -> Unit,
    onQuietHoursChange: (Boolean) -> Unit,
    onLowFrequencyChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFF6A1B9A)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "提醒策略",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StrategySwitchRow(
                title = "离开守护范围提醒",
                description = "连续超出守护范围后才提醒",
                checked = notifyOnExit,
                onCheckedChange = onNotifyOnExitChange
            )
            StrategySwitchRow(
                title = "回到守护范围提醒",
                description = "重新进入守护范围时提醒",
                checked = notifyOnEnter,
                onCheckedChange = onNotifyOnEnterChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "连续离开时长：${dwellMinutes} 分钟",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = dwellMinutes.toFloat(),
                onValueChange = { onDwellChange(it.toInt().coerceIn(3, 5)) },
                valueRange = 3f..5f,
                steps = 1
            )

            StrategySwitchRow(
                title = "安静时段（22:00 - 07:00）",
                description = "夜间不推送守护提醒",
                checked = quietHoursEnabled,
                onCheckedChange = onQuietHoursChange
            )
            StrategySwitchRow(
                title = "低频提醒",
                description = "同类提醒至少间隔 60 分钟",
                checked = lowFrequencyEnabled,
                onCheckedChange = onLowFrequencyChange
            )
        }
    }
}

@Composable
private fun StrategySwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
