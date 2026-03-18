package com.silverlink.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.sdk.health.BodyMeasurementData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    val measurements = healthDashboardData?.weightTimeline.orEmpty().sortedByDescending { it.timestamp }
    val latest = measurements.firstOrNull()
    val previous = measurements.getOrNull(1)
    val chartPoints = measurements.take(6).reversed()

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {},
                containerColor = Color(0xFF007bff),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加记录")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("体重详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "更多")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
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
                OverviewCards(latest = latest, previous = previous)
            }
            item {
                TrendSection(chartPoints = chartPoints)
            }
            item {
                GoalSection(latest = latest, chartPoints = chartPoints)
            }
            item {
                HistorySection(measurements = measurements.take(5))
            }
        }
    }
}

@Composable
private fun OverviewCards(latest: BodyMeasurementData?, previous: BodyMeasurementData?) {
    val weight = latest?.weightKg ?: 0f
    val bmi = latest?.bmi ?: 0f
    val delta = if (latest != null && previous != null) latest.weightKg - previous.weightKg else 0f

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F2FF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB3D9FF))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Scale, contentDescription = null, tint = Color(0xFF007bff))
                    Text("当前体重", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (weight > 0f) String.format(Locale.US, "%.1f", weight) else "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), letterSpacing = (-1).sp)
                    Text("kg", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = if (delta <= 0f) Color(0xFFEF4444) else Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text(String.format(Locale.US, "%+.1fkg", delta), color = if (delta <= 0f) Color(0xFFEF4444) else Color(0xFF16A34A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B))
                    Text("当前BMI", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                }
                Text(if (bmi > 0f) String.format(Locale.US, "%.1f", bmi) else "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), letterSpacing = (-1).sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(weightStatusColor(bmi), CircleShape))
                    Text(weightStatusLabel(bmi), color = Color(0xFF64748B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TrendSection(chartPoints: List<BodyMeasurementData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val weights = chartPoints.map { it.weightKg }
            val trend = if (weights.size >= 2) weights.last() - weights.first() else 0f
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("趋势", fontSize = 14.sp, color = Color(0xFF64748B))
                    Text(String.format(Locale.US, "%+.1f kg", trend), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
                Surface(color = Color(0xFF007bff).copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                    Text("最近同步", color = Color(0xFF007bff), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val values = if (weights.isNotEmpty()) weights else listOf(0f, 0f)
                    val minValue = (values.minOrNull() ?: 65f) - 2f
                    val maxValue = max((values.maxOrNull() ?: 75f) + 2f, minValue + 1f)
                    val stepX = size.width / (values.size - 1).coerceAtLeast(1)

                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = stepX * index
                        val y = size.height - ((value - minValue) / (maxValue - minValue)) * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    val gradientPath = Path().apply {
                        addPath(path)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }

                    drawPath(
                        gradientPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF007bff).copy(alpha = 0.3f), Color.Transparent),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                    drawPath(path, color = Color(0xFF007bff), style = Stroke(width = 4f, cap = StrokeCap.Round))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val formatter = SimpleDateFormat("MM/dd", Locale.CHINA)
                    chartPoints.forEach { point ->
                        Text(formatter.format(Date(point.timestamp)), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSection(latest: BodyMeasurementData?, chartPoints: List<BodyMeasurementData>) {
    val initial = chartPoints.firstOrNull()?.weightKg ?: latest?.weightKg ?: 0f
    val current = latest?.weightKg ?: 0f
    val target = max(current - 4f, 0f)
    val progress = if (initial > target && current > 0f) ((initial - current) / (initial - target)).coerceIn(0f, 1f) else 0f

    Text("目标", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 8.dp))
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("目标体重: ${String.format(Locale.US, "%.1f", target)} kg", fontSize = 14.sp, color = Color(0xFF475569))
                Surface(color = Color(0xFF007bff).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        String.format(Locale.US, "剩余 %.1fkg", (current - target).coerceAtLeast(0f)),
                        color = Color(0xFF007bff),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFF1F5F9), CircleShape)) {
                Box(modifier = Modifier.fillMaxWidth(progress).height(12.dp).background(Color(0xFF007bff), CircleShape))
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("初始记录", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                    Text(String.format(Locale.US, "%.1f kg", initial), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("当前记录", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                    Text(if (current > 0f) String.format(Locale.US, "%.1f kg", current) else "--", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
            }
        }
    }
}

@Composable
private fun HistorySection(measurements: List<BodyMeasurementData>) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("最近记录", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF007bff))) {
            Text("全部", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val formatter = SimpleDateFormat("MM月dd日", Locale.CHINA)
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.CHINA)
        measurements.forEachIndexed { index, item ->
            val previous = measurements.getOrNull(index + 1)
            val change = if (previous != null) item.weightKg - previous.weightKg else 0f
            RecordItem(
                date = formatter.format(Date(item.timestamp)),
                time = timeFormatter.format(Date(item.timestamp)),
                weight = String.format(Locale.US, "%.1f kg", item.weightKg),
                change = String.format(Locale.US, "%+.1fkg", change),
                changeColor = if (change <= 0f) Color(0xFFEF4444) else Color(0xFF16A34A),
                alpha = 1f - index * 0.12f
            )
        }
        if (measurements.isEmpty()) {
            RecordItem(
                date = "暂无记录",
                time = "--:--",
                weight = "--",
                change = "等待同步",
                changeColor = Color(0xFF94A3B8),
                alpha = 1f
            )
        }
    }
}

@Composable
private fun RecordItem(date: String, time: String, weight: String, change: String, changeColor: Color, alpha: Float) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC).copy(alpha = alpha),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(date, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A).copy(alpha = alpha))
                    Text(time, fontSize = 12.sp, color = Color(0xFF64748B).copy(alpha = alpha))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(weight, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A).copy(alpha = alpha))
                Text(change, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = changeColor)
            }
        }
    }
}

private fun weightStatusLabel(bmi: Float): String {
    return when {
        bmi <= 0f -> "待同步"
        bmi < 18.5f -> "偏轻"
        bmi < 24f -> "标准"
        bmi < 28f -> "偏高"
        else -> "超重"
    }
}

private fun weightStatusColor(bmi: Float): Color {
    return when {
        bmi <= 0f -> Color(0xFF94A3B8)
        bmi < 18.5f -> Color(0xFFF59E0B)
        bmi < 24f -> Color(0xFF22C55E)
        bmi < 28f -> Color(0xFFFB923C)
        else -> Color(0xFFEF4444)
    }
}
