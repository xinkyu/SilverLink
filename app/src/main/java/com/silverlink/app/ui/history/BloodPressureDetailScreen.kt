package com.silverlink.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.sdk.health.BloodPressureData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodPressureDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val dashboardData by viewModel.healthDashboardData.collectAsState()
    val readings = dashboardData?.bloodPressureTimeline.orEmpty().sortedByDescending { it.timestamp }
    val latest = readings.firstOrNull()
    val chartReadings = readings.take(4).reversed()

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = { Text("血压详情", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color(0xFF64748B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {},
                containerColor = Color(0xFF007bff),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加记录")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroStats(latest = latest)
            }
            item {
                StatusCard(latest = latest)
            }
            item {
                ChartSection(readings = chartReadings)
            }
            recentHistoryItems(readings = readings.take(5))
        }
    }
}

@Composable
private fun HeroStats(latest: BloodPressureData?) {
    val systolic = latest?.systolic ?: 0
    val diastolic = latest?.diastolic ?: 0

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF007bff).copy(alpha = 0.1f))
                .border(1.dp, Color(0xFF007bff).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF007bff), modifier = Modifier.size(16.dp))
                Text("收缩压", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007bff), letterSpacing = 1.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (systolic > 0) systolic.toString() else "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("mmHg", fontSize = 14.sp, color = Color(0xFF64748B))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                Text("舒张压", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B), letterSpacing = 1.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (diastolic > 0) diastolic.toString() else "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("mmHg", fontSize = 14.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun StatusCard(latest: BloodPressureData?) {
    val status = bloodPressureStatus(latest)
    val bgColor = if (status.isNormal) Color(0xFFECFDF5) else Color(0xFFFFF7ED)
    val borderColor = if (status.isNormal) Color(0xFFD1FAE5) else Color(0xFFFED7AA)
    val accentColor = if (status.isNormal) Color(0xFF10B981) else Color(0xFFF59E0B)
    val titleColor = if (status.isNormal) Color(0xFF064E3B) else Color(0xFF9A3412)
    val bodyColor = if (status.isNormal) Color(0xFF047857) else Color(0xFFC2410C)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
            }
            Column {
                Text("状态: ${status.label}", color = titleColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(status.description, color = bodyColor, fontSize = 14.sp)
            }
        }
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
            Text("查看健康洞察", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChartSection(readings: List<BloodPressureData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            val averageSystolic = readings.map { it.systolic }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
            val averageDiastolic = readings.map { it.diastolic }.average().takeIf { !it.isNaN() }?.toInt() ?: 0

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("近期趋势", fontSize = 14.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    Text(
                        if (averageSystolic > 0 && averageDiastolic > 0) "$averageSystolic/$averageDiastolic" else "--",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Text("${readings.size}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("最近记录数", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))

            val values = if (readings.isNotEmpty()) readings.map { it.systolic.toFloat() } else listOf(0f, 0f, 0f, 0f)
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val validValues = values.filter { it > 0f }
                val minValue = (validValues.minOrNull() ?: 100f) - 10f
                val maxValue = (validValues.maxOrNull() ?: 140f) + 10f
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()

                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = if (maxValue > minValue) (value - minValue) / (maxValue - minValue) else 0f
                    val y = size.height - ratio * size.height
                    if (index == 0) {
                        linePath.moveTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                    }
                }

                val bgPath = Path().apply {
                    addPath(linePath)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path = bgPath, color = Color(0xFF007bff).copy(alpha = 0.1f))
                drawPath(linePath, color = Color(0xFF007bff), style = Stroke(width = 6f, cap = StrokeCap.Round))

                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = if (maxValue > minValue) (value - minValue) / (maxValue - minValue) else 0f
                    val y = size.height - ratio * size.height
                    drawCircle(Color.White, radius = 12f, center = Offset(x, y))
                    drawCircle(Color(0xFF007bff), radius = 8f, center = Offset(x, y))
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val formatter = SimpleDateFormat("MM/dd", Locale.CHINA)
                (if (readings.isNotEmpty()) readings else List(4) { null }).forEach { point ->
                    Text(point?.let { formatter.format(Date(it.timestamp)) } ?: "--", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun LazyListScope.recentHistoryItems(readings: List<BloodPressureData>) {
    item {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最近记录", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                Text("查看全部", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF007bff), modifier = Modifier.clickable {})
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                readings.forEach { reading ->
                    val formatter = SimpleDateFormat("MM月dd日, hh:mm a", Locale.CHINA)
                    val status = bloodPressureStatus(reading)
                    HistoryItem(
                        time = formatter.format(Date(reading.timestamp)),
                        label = "健康同步",
                        reading = "${reading.systolic}/${reading.diastolic}",
                        status = status.label,
                        statusColor = status.color,
                        statusBg = status.background,
                        statusText = status.textColor
                    )
                }
                if (readings.isEmpty()) {
                    HistoryItem(
                        time = "暂无记录",
                        label = "尚未同步到血压数据",
                        reading = "--/--",
                        status = "待同步",
                        statusColor = Color(0xFF94A3B8),
                        statusBg = Color(0xFFF1F5F9),
                        statusText = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(time: String, label: String, reading: String, status: String, statusColor: Color, statusBg: Color, statusText: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF007bff).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF007bff), modifier = Modifier.size(20.dp))
            }
            Column {
                Text(time, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(reading, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
            Box(modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(statusBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusText, letterSpacing = 1.sp)
            }
        }
    }
}

private data class BloodPressureStatus(
    val label: String,
    val description: String,
    val color: Color,
    val background: Color,
    val textColor: Color,
    val isNormal: Boolean
)

private fun bloodPressureStatus(reading: BloodPressureData?): BloodPressureStatus {
    val systolic = reading?.systolic ?: 0
    val diastolic = reading?.diastolic ?: 0
    return when {
        systolic <= 0 || diastolic <= 0 -> BloodPressureStatus(
            label = "待同步",
            description = "还没有读取到最新的血压数据。",
            color = Color(0xFF94A3B8),
            background = Color(0xFFF1F5F9),
            textColor = Color(0xFF475569),
            isNormal = false
        )
        systolic < 120 && diastolic < 80 -> BloodPressureStatus(
            label = "正常",
            description = "您的血压读数处于健康范围。",
            color = Color(0xFF10B981),
            background = Color(0xFFD1FAE5),
            textColor = Color(0xFF064E3B),
            isNormal = true
        )
        systolic < 140 && diastolic < 90 -> BloodPressureStatus(
            label = "偏高",
            description = "血压略高，建议继续观察近期趋势。",
            color = Color(0xFFF59E0B),
            background = Color(0xFFFEF3C7),
            textColor = Color(0xFF92400E),
            isNormal = false
        )
        else -> BloodPressureStatus(
            label = "偏高",
            description = "血压高于建议范围，建议结合医生建议持续关注。",
            color = Color(0xFFEF4444),
            background = Color(0xFFFEE2E2),
            textColor = Color(0xFF991B1B),
            isNormal = false
        )
    }
}
