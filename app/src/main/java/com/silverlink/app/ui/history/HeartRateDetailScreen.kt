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
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.feature.health.HealthTrendPoint
import com.silverlink.app.feature.health.OppoHealthDashboardData
import com.silverlink.app.feature.health.OppoHealthSdkManager
import com.silverlink.app.ui.components.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { androidx.compose.runtime.mutableStateOf(TimeRange.DAY) }
    val dayPoints = healthDashboardData?.heartRateTimeline.orEmpty().sortedBy { it.timestamp }
    val rangePoints by produceState(
        initialValue = emptyList<HealthTrendPoint>(),
        selectedRange,
        healthDashboardData
    ) {
        value = when (selectedRange) {
            TimeRange.DAY -> dayPoints
            else -> {
                val window = currentTimeWindow(selectedRange)
                OppoHealthSdkManager.getHeartRateSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
        }
    }

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
                HeartRateRangeTabs(
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRange = it }
                )
            }
            item {
                SourceCard(
                    title = "数据来源",
                    body = if (rangePoints.isEmpty()) {
                        "当前时间范围内没有可展示的真实心率数据。"
                    } else {
                        "当前页面已接入 OPPO 健康真实心率数据，日视图读取明细，周月年读取汇总。"
                    }
                )
            }
            item {
                when (selectedRange) {
                    TimeRange.DAY -> HeartRateDaySection(
                        data = healthDashboardData,
                        points = rangePoints
                    )
                    TimeRange.WEEK -> HeartRateWeekSection(points = rangePoints)
                    TimeRange.MONTH -> HeartRateMonthSection(points = rangePoints)
                    TimeRange.YEAR -> HeartRateYearSection(points = rangePoints)
                }
            }
        }
    }
}

@Composable
private fun HeartRateRangeTabs(selectedRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
    val tabs = listOf(
        TimeRange.DAY to "日",
        TimeRange.WEEK to "周",
        TimeRange.MONTH to "月",
        TimeRange.YEAR to "年"
    )
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEFF3F8), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (range, label) ->
                val selected = range == selectedRange
                Surface(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) Color.White else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
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
private fun SourceCard(title: String, body: String) {
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
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF007BFF))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(
                    body,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}

@Composable
private fun HeartRateDaySection(
    data: OppoHealthDashboardData?,
    points: List<HealthTrendPoint>
) {
    val current = data?.latestHeartRate?.takeIf { it > 0 } ?: points.lastOrNull()?.value ?: 0
    val resting = minTrend(points)
    val average = averageTrend(points)
    val zones = heartRateZones(points)

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
                Text("今日最新心率", color = Color(0xFF007BFF), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(20.dp))
                if (points.isEmpty()) {
                    EmptyStateCard()
                } else {
                    HeartRateLineChart(points = points)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "最低心率", resting.toString(), "bpm", "今日最低")
            MetricCard(Modifier.weight(1f), "平均心率", average.toString(), "bpm", "今日均值")
        }
        SectionCard(title = "心率区间") {
            if (points.isEmpty()) {
                EmptySectionText()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    zones.forEach { zone ->
                        val ratio = if (points.isNotEmpty()) zone.minutes.toFloat() / points.size else 0f
                        ProgressRow(
                            title = zone.title,
                            range = zone.range,
                            trailing = "${zone.minutes} 次",
                            progress = ratio,
                            color = zone.color
                        )
                    }
                }
            }
        }
        InsightCard(
            title = "健康提示",
            body = if (points.isEmpty()) {
                "今天还没有读取到心率明细，完成授权并在健康 App 中有数据后会显示真实曲线。"
            } else {
                "今日心率范围 ${minTrend(points)} - ${maxTrend(points)} bpm，曲线与区间统计均来自真实采样。"
            },
            solid = false
        )
    }
}

@Composable
private fun HeartRateWeekSection(points: List<HealthTrendPoint>) {
    val weeklyPoints = lastDays(points, 7)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "周平均", averageTrend(weeklyPoints).toString(), "bpm", "最近 7 天")
            MetricCard(Modifier.weight(1f), "周最高", maxTrend(weeklyPoints).toString(), "bpm", "最近 7 天")
        }
        SectionCard("本周趋势", formatRangeLabel(weeklyPoints)) {
            if (weeklyPoints.isEmpty()) {
                EmptySectionText()
            } else {
                Text(
                    "${minTrend(weeklyPoints)} - ${maxTrend(weeklyPoints)} bpm",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(16.dp))
                HeartRateBarChart(points = weeklyPoints, highlightIndex = weeklyPoints.lastIndex)
            }
        }
        InsightCard(
            title = "本周洞察",
            body = if (weeklyPoints.isEmpty()) {
                "本周还没有读取到真实心率汇总。"
            } else {
                "最近 7 天平均心率为 ${averageTrend(weeklyPoints)} bpm，最高出现在 ${formatPointDate(weeklyPoints.maxByOrNull { it.value }?.timestamp)}。"
            },
            solid = true
        )
    }
}

@Composable
private fun HeartRateMonthSection(points: List<HealthTrendPoint>) {
    val monthlyPoints = lastDays(points, 30)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "月平均", averageTrend(monthlyPoints).toString(), "bpm", "最近 30 天")
            MetricCard(Modifier.weight(1f), "月最高", maxTrend(monthlyPoints).toString(), "bpm", "最近 30 天")
        }
        SectionCard(formatMonthTitle()) {
            if (monthlyPoints.isEmpty()) {
                EmptySectionText()
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Legend("偏低", Color(0xFF34D399))
                    Legend("正常", Color(0xFF007BFF))
                    Legend("偏高", Color(0xFFFB923C))
                }
                Spacer(modifier = Modifier.height(12.dp))
                HeartRateMonthGrid(points = monthlyPoints)
            }
        }
        MetricRow(title = "稳定天数", value = "${monthlyPoints.count { it.value in 55..95 }} 天")
        MetricRow(title = "最高日", value = formatPointDate(monthlyPoints.maxByOrNull { it.value }?.timestamp))
        InsightCard(
            title = "月度分析",
            body = if (monthlyPoints.isEmpty()) {
                "本月暂无真实心率汇总。"
            } else {
                "月视图中的每个格子都映射真实日均心率，颜色不再使用占位值。"
            },
            solid = false
        )
    }
}

@Composable
private fun HeartRateYearSection(points: List<HealthTrendPoint>) {
    val values = monthlyAverageValues(points)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "年度平均", averageTrend(points).toString(), "bpm", "最近 12 个月")
            MetricCard(
                Modifier.weight(1f),
                "峰值月份",
                monthLabelFromIndex(values.indices.maxByOrNull { values[it] } ?: 0),
                "",
                "${values.maxOrNull() ?: 0} bpm"
            )
        }
        SectionCard("月均心率", "最近 12 个月") {
            if (points.isEmpty()) {
                EmptySectionText()
            } else {
                HeartRateYearChart(values = values)
            }
        }
        InsightCard(
            title = "年度洞察",
            body = if (points.isEmpty()) {
                "最近 12 个月没有读取到真实心率汇总。"
            } else {
                "年度趋势按真实月均心率生成，峰值出现在 ${monthLabelFromIndex(values.indices.maxByOrNull { values[it] } ?: 0)}。"
            },
            solid = false
        )
    }
}

@Composable
private fun HeartRateLineChart(points: List<HealthTrendPoint>) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0x0D007BFF))) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val minValue = minTrend(points)
            val maxValue = maxTrend(points).coerceAtLeast(minValue + 1)
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
private fun HeartRateBarChart(points: List<HealthTrendPoint>, highlightIndex: Int) {
    val maxValue = maxTrend(points).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEachIndexed { index, point ->
            val value = point.value.toFloat() / maxValue.toFloat()
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp * value)
                            .background(
                                if (index == highlightIndex) Color(0xFF007BFF) else Color(0x66007BFF),
                                RoundedCornerShape(999.dp)
                            )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = weekdayLabel(point.timestamp),
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun HeartRateMonthGrid(points: List<HealthTrendPoint>) {
    val cells = points.map { point ->
        val color = when {
            point.value < 55 -> Color(0xFF34D399)
            point.value <= 95 -> Color(0xFF007BFF)
            else -> Color(0xFFFB923C)
        }
        dayOfMonthLabel(point.timestamp) to color
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
                        Text(label, color = Color.White, fontSize = 12.sp)
                    }
                }
                repeat((7 - row.size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun HeartRateYearChart(values: List<Int>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val minValue = (values.minOrNull() ?: 0).coerceAtMost(50)
                val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(minValue + 1)
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
private fun MetricCard(modifier: Modifier, title: String, value: String, unit: String, caption: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(unit, fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 4.dp))
                }
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
private fun ProgressRow(title: String, range: String, trailing: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$title ($range)", color = Color(0xFF475569), fontSize = 13.sp)
            Text(trailing, color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(Color(0xFFE2E8F0), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
            drawRoundRect(color, size = Size(size.width * progress.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
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
                modifier = Modifier
                    .size(38.dp)
                    .background(if (solid) Color.White.copy(alpha = 0.18f) else Color(0x1A007BFF), RoundedCornerShape(12.dp)),
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
private fun MetricRow(title: String, value: String) {
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

@Composable
private fun EmptyStateCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("暂无真实心率曲线", color = Color(0xFF94A3B8))
    }
}

@Composable
private fun EmptySectionText() {
    Text("暂无真实数据", color = Color(0xFF94A3B8))
}

private data class HeartRateZone(
    val title: String,
    val range: String,
    val minutes: Int,
    val color: Color
)

private fun heartRateZones(points: List<HealthTrendPoint>): List<HeartRateZone> {
    return listOf(
        HeartRateZone("静息", "< 60", points.count { it.value < 60 }, Color(0xFF60A5FA)),
        HeartRateZone("日常", "60 - 100", points.count { it.value in 60..100 }, Color(0xFF22C55E)),
        HeartRateZone("偏高", "101 - 140", points.count { it.value in 101..140 }, Color(0xFFF59E0B)),
        HeartRateZone("高强度", "> 140", points.count { it.value > 140 }, Color(0xFFEF4444))
    )
}

private fun weekdayLabel(timestamp: Long): String {
    return SimpleDateFormat("E", Locale.CHINA).format(Date(timestamp))
}

private fun dayOfMonthLabel(timestamp: Long): String {
    return SimpleDateFormat("d", Locale.CHINA).format(Date(timestamp))
}

private fun formatPointDate(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "--"
    return SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timestamp))
}

private fun formatRangeLabel(points: List<HealthTrendPoint>): String {
    if (points.isEmpty()) return "暂无数据"
    val format = SimpleDateFormat("M月d日", Locale.CHINA)
    return "${format.format(Date(points.first().timestamp))} - ${format.format(Date(points.last().timestamp))}"
}

private fun formatMonthTitle(): String {
    return SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date())
}

private fun monthLabelFromIndex(index: Int): String = "${index + 1}月"
