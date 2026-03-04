package com.silverlink.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverlink.app.feature.watch.WatchConnectionService
import com.silverlink.app.feature.watch.WatchConnectionService.ConnectionState
import com.silverlink.sdk.nearby.DeviceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchManagementScreen(
    onNavigateToHealth: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by WatchConnectionService.connectionState.collectAsState()
    val connectedDevice by WatchConnectionService.connectedDevice.collectAsState()
    val discoveredDevices by WatchConnectionService.discoveredDevices.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手表管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status card
            ConnectionStatusCard(
                connectionState = connectionState,
                connectedDevice = connectedDevice,
                onStartService = { WatchConnectionService.start(context) },
                onStopService = { WatchConnectionService.stop(context) },
                onDisconnect = { WatchConnectionService.disconnect() }
            )

            // Discovery & pairing
            if (connectionState != ConnectionState.CONNECTED) {
                DiscoveryCard(
                    connectionState = connectionState,
                    discoveredDevices = discoveredDevices,
                    onStartDiscovery = { WatchConnectionService.startDiscovery() },
                    onConnect = { deviceId -> WatchConnectionService.connectToDevice(deviceId) }
                )
            }

            // Connected features
            if (connectionState == ConnectionState.CONNECTED) {
                ConnectedFeaturesCard(
                    onSyncMedications = {
                        WatchConnectionService.sendMessage(
                            com.silverlink.shared.protocol.WatchMessage.SyncMedications(emptyList())
                        )
                    },
                    onNavigateToHealth = onNavigateToHealth
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    connectedDevice: DeviceInfo?,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFF1B5E20).copy(alpha = 0.1f)
                ConnectionState.CONNECTING, ConnectionState.DISCOVERING ->
                    Color(0xFFF49007).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                ConnectionState.CONNECTING, ConnectionState.DISCOVERING ->
                                    Color(0xFFF49007)
                                else -> Color.Gray
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Watch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> "已连接"
                            ConnectionState.CONNECTING -> "正在连接..."
                            ConnectionState.DISCOVERING -> "正在搜索..."
                            ConnectionState.DISCONNECTED -> "未连接"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (connectedDevice != null) {
                        Text(
                            text = connectedDevice.name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (connectionState) {
                    ConnectionState.DISCONNECTED -> {
                        Button(
                            onClick = onStartService,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("启动服务")
                        }
                    }
                    ConnectionState.CONNECTED -> {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("断开连接")
                        }
                    }
                    else -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(
    connectionState: ConnectionState,
    discoveredDevices: List<DeviceInfo>,
    onStartDiscovery: () -> Unit,
    onConnect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("附近设备", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(12.dp))

            if (discoveredDevices.isEmpty()) {
                Text(
                    text = if (connectionState == ConnectionState.DISCOVERING)
                        "正在搜索附近的手表..."
                    else
                        "点击下方按钮搜索附近手表",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                discoveredDevices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onConnect(device.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Watch,
                            contentDescription = null,
                            tint = Color(0xFFF49007)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.Medium)
                            Text(
                                device.id,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("连接", color = Color(0xFFF49007), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (connectionState != ConnectionState.DISCOVERING) {
                OutlinedButton(
                    onClick = onStartDiscovery,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("搜索手表")
                }
            }
        }
    }
}

@Composable
private fun ConnectedFeaturesCard(
    onSyncMedications: () -> Unit,
    onNavigateToHealth: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("功能", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(12.dp))

            FeatureRow(
                icon = Icons.Default.Favorite,
                title = "健康数据",
                subtitle = "查看手表同步的健康数据",
                onClick = onNavigateToHealth
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            FeatureRow(
                icon = Icons.Default.Sync,
                title = "同步数据",
                subtitle = "同步药物和联系人到手表",
                onClick = onSyncMedications
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFF49007),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
