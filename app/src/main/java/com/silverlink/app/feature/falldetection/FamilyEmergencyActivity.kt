package com.silverlink.app.feature.falldetection

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.ResolveEmergencyRequest
import com.silverlink.app.ui.theme.SilverLinkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 家人端紧急事件详情界面
 * 显示老人位置，提供拨打电话和标记已处理功能
 */
class FamilyEmergencyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val eventId = intent.getStringExtra("event_id") ?: ""
        val elderName = intent.getStringExtra("elder_name") ?: "您的家人"
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        setContent {
            SilverLinkTheme {
                FamilyEmergencyScreen(
                    eventId = eventId,
                    elderName = elderName,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = timestamp,
                    onCallEmergency = { callEmergencyNumber() },
                    onViewLocation = { viewLocation(latitude, longitude) },
                    onMarkResolved = { markAsResolved(eventId) },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun callEmergencyNumber() {
        // 拨打120急救电话
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:120")
        }
        startActivity(intent)
    }

    private fun viewLocation(latitude: Double, longitude: Double) {
        if (latitude != 0.0 && longitude != 0.0) {
            // 使用高德地图打开位置
            val uri = Uri.parse("https://uri.amap.com/marker?position=$longitude,$latitude&name=老人位置")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }

    private fun markAsResolved(eventId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val request = ResolveEmergencyRequest(
                    eventId = eventId,
                    familyDeviceId = deviceId
                )
                CloudBaseService.api.resolveEmergency(request)
            } catch (e: Exception) {
                // 忽略错误
            }
        }
        finish()
    }
}

@Composable
fun FamilyEmergencyScreen(
    eventId: String,
    elderName: String,
    latitude: Double,
    longitude: Double,
    timestamp: Long,
    onCallEmergency: () -> Unit,
    onViewLocation: () -> Unit,
    onMarkResolved: () -> Unit,
    onClose: () -> Unit
) {
    val timeStr = remember(timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
    
    val hasLocation = latitude != 0.0 && longitude != 0.0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFD32F2F), Color(0xFFB71C1C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            // 警告图标
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.White
            )

            // 标题
            Text(
                text = "⚠️ 紧急警报",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )

            // 信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "$elderName 可能发生了跌倒！",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFD32F2F)
                    )

                    Text(
                        text = "检测时间: $timeStr",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 查看位置按钮
                    Button(
                        onClick = onViewLocation,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasLocation) Color(0xFF1976D2) else Color.Gray
                        ),
                        enabled = hasLocation
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasLocation) "查看位置" else "位置不可用",
                            fontSize = 18.sp
                        )
                    }

                    // 拨打急救电话按钮
                    Button(
                        onClick = onCallEmergency,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("拨打120急救电话", fontSize = 18.sp)
                    }
                }
            }

            // 已确认安全按钮
            OutlinedButton(
                onClick = onMarkResolved,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("已确认安全", fontSize = 18.sp)
            }
        }
    }
}
