package com.silverlink.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Fireplace
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.feature.health.OppoHealthDashboardData
import com.silverlink.app.ui.components.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { androidx.compose.runtime.mutableStateOf(TimeRange.DAY) }

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = { Text("活动详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val actionIcon = when (selectedRange) {
                        TimeRange.DAY, TimeRange.WEEK -> Icons.Default.IosShare
                        TimeRange.MONTH -> Icons.Default.CalendarToday
                        TimeRange.YEAR -> Icons.Default.Settings
                    }
                    IconButton(onClick = {}) {
                        Icon(actionIcon, contentDescription = null)
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RangeTabs(
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRange = it }
                )
            }
            item { PlaceholderCard() }
            item {
                when (selectedRange) {
                    TimeRange.DAY -> DaySection(healthDashboardData)
                    TimeRange.WEEK -> WeekSection()
                    TimeRange.MONTH -> MonthSection()
                    TimeRange.YEAR -> YearSection()
                }
            }
        }
    }
}

@Composable
private fun RangeTabs(selectedRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
    val tabs = listOf(
        TimeRange.DAY to "日",
        TimeRange.WEEK to "周",
        TimeRange.MONTH to "月",
        TimeRange.YEAR to "年"
    )
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFEFF3F8)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (range, label) ->
                val selected = range == selectedRange
                Surface(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) Color.White else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color(0xFF0F172A) else Color(0xFF64748B),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x1A22C55E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF22C55E))
            }
            Column {
                Text("活动数据来源手表", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(
                    "当前页面按 Stitch 新稿展示，已支持步数、热量和时长，其他图表为占位示意。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}

@Composable
private fun DaySection(data: OppoHealthDashboardData?) {
    val steps = data?.steps ?: 0
    val target = data?.stepGoal ?: 8000
    val progress = if (target > 0) (steps.toFloat() / target).coerceIn(0f, 1f) else 0f
    
    val calories = data?.calories ?: 0
    val activeMinutes = data?.moveMinutes ?: 0
    val distance = data?.distanceMeters ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        color = Color(0xFFF1F5F9),
                        strokeWidth = 14.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { progress },
                        color = Color(0xFF10B981),
                        strokeWidth = 14.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(steps.toString(), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("步数 / 目标 $target", color = Color(0xFF64748B), fontSize = 13.sp)
                    }
                }
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "热量消耗", calories.toString(), "kcal", Icons.Default.Fireplace, Color(0xFFF97316))
            StatCard(Modifier.weight(1f), "活动时长", activeMinutes.toString(), "min", Icons.Default.Schedule, Color(0xFF007BFF))
        }

        SectionCard("今日分布") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeBlockBar("上午", "06:00 - 12:00", "${(steps * 0.3).toInt()} 步", 0.3f, Color(0xFF10B981))
                TimeBlockBar("下午", "12:00 - 18:00", "${(steps * 0.5).toInt()} 步", 0.5f, Color(0xFF34D399))
                TimeBlockBar("晚上", "18:00 - 24:00", "${(steps * 0.2).toInt()} 步", 0.2f, Color(0xFF6EE7B7))
            }
        }
        
        InsightCard("达标提示", if (progress >= 1f) "今天已经完成活动目标，保持得很好！" else "距离今日目标还有 ${target - steps} 步，起来活动一下吧。", false)
    }
}

@Composable
private fun WeekSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "日均步数", "6240", "步", Icons.Default.DirectionsRun, Color(0xFF10B981))
            StatCard(Modifier.weight(1f), "总消耗", "1420", "kcal", Icons.Default.Fireplace, Color(0xFFF97316))
        }
        SectionCard("本周活动", "3月11日 - 3月17日") {
            Text("活动最频繁: 周四", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(16.dp))
            WeeklyBars()
        }
        InsightCard("本周洞察", "每周活动总览已调整为 Stitch 风格柱子。您本周前三天活动量高于平时，周末有所下降。", true)
    }
}

@Composable
private fun MonthSection() {
    val monthLabel = remember { SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date()) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本月总步数", "184K", "步", Icons.Default.DirectionsRun, Color(0xFF10B981))
            StatCard(Modifier.weight(1f), "达标天数", "18", "天", Icons.Default.CalendarToday, Color(0xFF007BFF))
        }
        SectionCard(monthLabel) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Legend("未达标", Color(0xFFE2E8F0))
                Legend("已达标", Color(0xFF34D399))
                Legend("超额", Color(0xFF10B981))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            MonthGrid()
        }
        InsightCard("月度分析", "当月达标率为 60%。月历热力图展现每天目标的完成度，颜色越深代表超过目标越多。", true)
    }
}

@Composable
private fun YearSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "日均步数", "7120", "步", Icons.Default.DirectionsRun, Color(0xFF10B981))
            StatCard(Modifier.weight(1f), "年度最佳", "12K", "步", Icons.Default.Fireplace, Color(0xFFF97316))
        }
        SectionCard("月均活动量", "1月 - 12月") {
            YearChart()
        }
        InsightCard("年度里程碑", "年视图统计出您的活动量高峰在夏季。数据真实接入后可直观反应长期的运动趋势与连续性。", false)
    }
}

@Composable
private fun WeeklyBars() {
    val ranges = listOf(0.4f, 0.6f, 0.8f, 1.0f, 0.7f, 0.3f, 0.5f)
    Row(modifier = Modifier.fillMaxWidth().height(210.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        ranges.forEachIndexed { index, value ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(modifier = Modifier.fillMaxWidth().height(170.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(999.dp)))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp * value)
                            .background(if (value >= 0.8f) Color(0xFF10B981) else Color(0xFF34D399), RoundedCornerShape(999.dp))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(listOf("一", "二", "三", "四", "五", "六", "日")[index], fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun MonthGrid() {
    val cells = remember {
        buildList {
            repeat(5) { add("" to Color.Transparent) }
            for (day in 1..30) {
                val color = when {
                    day % 7 == 0 -> Color(0xFF10B981)
                    day % 3 == 0 || day % 4 == 0 -> Color(0xFF34D399)
                    else -> Color(0xFFF1F5F9)
                }
                add(day.toString() to color)
            }
            while (size % 7 != 0) add("" to Color.Transparent)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, color) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(color, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (color == Color.Transparent || color == Color(0xFFF1F5F9)) Color(0xFF94A3B8) else Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearChart() {
    val values = listOf(5000, 5200, 6800, 8100, 9200, 10500, 11000, 9800, 8500, 7200, 6000, 5100)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val minValue = 0
                val maxValue = 12000
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()
                val fillPath = Path()
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue).toFloat() / (maxValue - minValue).toFloat()
                    val y = size.height - ratio * (size.height - 20.dp.toPx()) - 10.dp.toPx()
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()
                drawPath(fillPath, brush = Brush.verticalGradient(listOf(Color(0x3310B981), Color.Transparent)))
                drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("1月", "3月", "5月", "7月", "9月", "11月").forEach {
                    Text(it, fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = Color(0xFF64748B), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun TimeBlockBar(title: String, range: String, steps: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$title ($range)", color = Color(0xFF475569), fontSize = 13.sp)
            Text(steps, color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(Color(0xFFF1F5F9), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
            drawRoundRect(color, size = Size(size.width * progress, size.height), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
        }
    }
}

@Composable
private fun InsightCard(title: String, body: String, solid: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (solid) Color(0xFF10B981) else Color(0xFFF0FDF4))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(if (solid) Color.White.copy(alpha = 0.18f) else Color(0x1A10B981), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = if (solid) Color.White else Color(0xFF10B981))
            }
            Column {
                Text(title, color = if (solid) Color.White else Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(body, color = if (solid) Color.White.copy(alpha = 0.92f) else Color(0xFF475569), fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}
