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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import com.silverlink.app.feature.health.HealthTrendPoint
import com.silverlink.app.feature.health.OppoHealthDashboardData
import com.silverlink.app.ui.components.TimeRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { androidx.compose.runtime.mutableStateOf(TimeRange.DAY) }

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = { Text("心率", fontWeight = FontWeight.Bold) },
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
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
                    .background(Color(0x1A007BFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF007BFF))
            }
            Column {
                Text("手表数据接入中", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(
                    "当前页面先按 Stitch 新稿展示，趋势和统计为占位态，后续直接替换成真实心率数据。",
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
    val points = remember(data) { if (data?.heartRateTimeline.isNullOrEmpty()) demoTimeline() else data!!.heartRateTimeline }
    val current = data?.latestHeartRate?.takeIf { it > 0 } ?: 72
    val resting = points.minOf { it.value }
    val average = points.map { it.value }.average().toInt()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(current.toString(), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BPM", color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF007BFF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("实时心率", color = Color(0xFF007BFF), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                LineChart(points)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "静息心率", resting.toString(), "bpm", "示意统计")
            StatCard(Modifier.weight(1f), "日均心率", average.toString(), "bpm", "示意统计")
        }
        SectionCard("心率区间") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZoneBar("静息", "< 100", "18h 45m", 0.75f, Color(0xFF60A5FA))
                ZoneBar("燃脂", "110 - 135", "1h 12m", 0.15f, Color(0xFF22C55E))
                ZoneBar("有氧", "136 - 155", "32m", 0.08f, Color(0xFFF59E0B))
                ZoneBar("峰值", "> 156", "5m", 0.02f, Color(0xFFEF4444))
            }
        }
        InsightCard("健康提示", "日视图已切成新版头图、曲线和区间结构。后续只需要把这些占位统计映射到真实心率数据。", false)
    }
}

@Composable
private fun WeekSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "平均静息心率", "62", "bpm", "示意值")
            StatCard(Modifier.weight(1f), "平均每日峰值", "85", "bpm", "示意值")
        }
        SectionCard("本周范围", "3月11日 - 3月17日") {
            Text("62 - 142 bpm", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(16.dp))
            WeeklyBars()
        }
        InsightCard("本周洞察", "周视图已按 Stitch 切成范围柱状结构，等手表数据接通后可直接展示每日最低/最高心率。", true)
        SectionCard("心率区间") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZoneSummary("静息", "< 90 bpm", "82%", "138h 12m", Color(0xFF94A3B8))
                ZoneSummary("燃脂", "91 - 115 bpm", "12%", "20h 15m", Color(0xFF22C55E))
                ZoneSummary("有氧", "116 - 145 bpm", "5%", "8h 45m", Color(0xFFF59E0B))
                ZoneSummary("峰值", "> 145 bpm", "1%", "0h 48m", Color(0xFFEF4444))
            }
        }
    }
}

@Composable
private fun MonthSection() {
    val monthLabel = remember { SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date()) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "月平均心率", "65", "bpm", "占位值")
            StatCard(Modifier.weight(1f), "最高记录", "110", "bpm", "占位值")
        }
        SectionCard(monthLabel) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Legend("偏低", Color(0xFF34D399))
                Legend("正常", Color(0xFF007BFF))
                Legend("偏高", Color(0xFFFB923C))
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
        StatList("静息心率稳定度", "92%")
        StatList("最高心率日期", "12日")
        InsightCard("智能提示", "月视图已改成热力日历样式，后续只需把每天的心率状态映射为不同颜色。", true)
    }
}

@Composable
private fun YearSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "年度平均", "68", "bpm", "2026 占位值")
            StatCard(Modifier.weight(1f), "较去年变化", "-3", "%", "改善趋势")
        }
        SectionCard("月均心率", "1月 - 12月") {
            YearChart()
        }
        SectionCard("年度里程碑") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatList("运动最稳定月份", "5月")
                StatList("最低平均心率月份", "10月")
            }
        }
        InsightCard("年度洞察", "年视图已经切成折线趋势和里程碑结构，真实月均心率接入后可直接替换。", false)
    }
}

@Composable
private fun LineChart(points: List<HealthTrendPoint>) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0x0D007BFF))) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val minValue = points.minOf { it.value }
            val maxValue = max(points.maxOf { it.value }, minValue + 1)
            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
            val linePath = Path()
            val fillPath = Path()
            points.forEachIndexed { index, point ->
                val x = stepX * index
                val ratio = (point.value - minValue).toFloat() / (maxValue - minValue).toFloat()
                val y = size.height - ratio * (size.height - 16.dp.toPx()) - 8.dp.toPx()
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
            drawPath(fillPath, brush = Brush.verticalGradient(listOf(Color(0x33007BFF), Color(0x05007BFF))))
            drawPath(linePath, color = Color(0xFF007BFF), style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun WeeklyBars() {
    val ranges = listOf(0.56f, 0.82f, 0.60f, 0.72f, 0.54f, 0.40f, 0.46f)
    Row(modifier = Modifier.fillMaxWidth().height(210.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        ranges.forEachIndexed { index, value ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(modifier = Modifier.fillMaxWidth().height(170.dp).background(Color(0xFFE2E8F0), RoundedCornerShape(999.dp)))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp * value)
                            .background(if (index == 1 || index == 3) Color(0xFF007BFF) else Color(0x66007BFF), RoundedCornerShape(999.dp))
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
                    day == 12 -> Color(0xFFF97316)
                    day % 7 == 0 -> Color(0xFFFB923C)
                    day % 5 == 0 || day % 6 == 0 -> Color(0xFF007BFF)
                    day % 3 == 0 -> Color(0xFF10B981)
                    else -> Color(0xFF34D399)
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
                        Text(label, color = if (color == Color.Transparent) Color(0xFFCBD5E1) else Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearChart() {
    val values = listOf(70, 68, 69, 66, 64, 63, 65, 67, 61, 60, 62, 61)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val minValue = values.minOrNull() ?: 0
                val maxValue = max(values.maxOrNull() ?: 0, minValue + 1)
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
                drawPath(fillPath, brush = Brush.verticalGradient(listOf(Color(0x33007BFF), Color.Transparent)))
                drawPath(linePath, color = Color(0xFF007BFF), style = Stroke(width = 5f, cap = StrokeCap.Round))
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
private fun StatCard(modifier: Modifier, title: String, value: String, unit: String, caption: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(caption, fontSize = 11.sp, color = Color(0xFF94A3B8))
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
private fun ZoneBar(title: String, range: String, duration: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$title ($range)", color = Color(0xFF475569), fontSize = 13.sp)
            Text(duration, color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(Color(0xFFE2E8F0), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
            drawRoundRect(color, size = Size(size.width * progress, size.height), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
        }
    }
}

@Composable
private fun ZoneSummary(title: String, range: String, percent: String, duration: String, color: Color) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(6.dp).height(40.dp).background(color, RoundedCornerShape(999.dp)))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                    Text(range, fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(percent, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(duration, fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun InsightCard(title: String, body: String, solid: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (solid) Color(0xFF007BFF) else Color(0xFFEFF6FF))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(if (solid) Color.White.copy(alpha = 0.18f) else Color(0x1A007BFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = if (solid) Color.White else Color(0xFF007BFF))
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

@Composable
private fun StatList(title: String, value: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color(0xFF64748B), fontSize = 13.sp)
            Text(value, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

private fun demoTimeline(): List<HealthTrendPoint> {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val values = listOf(68, 70, 67, 72, 76, 74, 78, 84, 80, 77, 73, 71)
    return values.mapIndexed { index, value ->
        HealthTrendPoint(calendar.timeInMillis + index * 2L * 60L * 60L * 1000L, value)
    }
}
