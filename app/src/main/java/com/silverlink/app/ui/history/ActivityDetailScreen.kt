package com.silverlink.app.ui.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.feature.health.HealthTrendPoint
import com.silverlink.app.feature.health.OppoHealthDashboardData
import com.silverlink.app.feature.health.OppoHealthSdkManager
import com.silverlink.app.ui.components.TimeRange
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale

data class ActivityBarEntry(
    val date: LocalDate,
    val value: Int?,
    val label: String,
    val isFuture: Boolean = false,
    val isHighlight: Boolean = false
)

private data class ActivityMonthGridCell(
    val date: LocalDate?,
    val value: Int? = null,
    val isFuture: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(TimeRange.DAY) }
    val dayTrend = healthDashboardData?.activityDailySummary.orEmpty().sortedBy { it.timestamp }
    val rangePoints by produceState(
        initialValue = emptyList<HealthTrendPoint>(),
        selectedRange,
        healthDashboardData
    ) {
        value = when (selectedRange) {
            TimeRange.DAY -> lastDays(dayTrend, 7)
            TimeRange.WEEK -> {
                val window = currentWeekWindow()
                OppoHealthSdkManager.getActivitySummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            TimeRange.MONTH -> {
                val window = currentMonthWindow(zone)
                OppoHealthSdkManager.getActivitySummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            else -> {
                val window = currentTimeWindow(selectedRange)
                OppoHealthSdkManager.getActivitySummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
        }
    }

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
                    IconButton(
                        onClick = {
                            val summary = buildActivityShareText(
                                range = selectedRange,
                                dashboardData = healthDashboardData,
                                points = rangePoints,
                                zone = zone
                            )
                            if (summary.isBlank()) {
                                Toast.makeText(context, "暂无可分享的活动数据", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, summary)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享活动详情"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
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
                ActivityRangeTabs(
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRange = it }
                )
            }
            item {
                SourceCard(
                    title = "数据来源",
                    body = if (selectedRange == TimeRange.DAY) {
                        "今日卡片展示真实步数、热量、距离和活动分钟，趋势区域展示最近 7 天的真实日汇总。"
                    } else {
                        "周、月、年视图均直接读取 OPPO 健康活动汇总数据。"
                    }
                )
            }
            item {
                when (selectedRange) {
                    TimeRange.DAY -> ActivityDaySection(healthDashboardData, rangePoints)
                    TimeRange.WEEK -> ActivityRangeSection(
                        title = "本周活动",
                        points = buildCurrentWeekPoints(rangePoints),
                        chartEntries = buildCurrentWeekEntries(rangePoints),
                        subtitle = formatWeekRangeLabel(),
                        insight = "周视图按本周一到周日展示，未来日期保留为浅灰占位。"
                    )
                    TimeRange.MONTH -> ActivityRangeSection(
                        title = "本月活动",
                        points = buildCurrentMonthPoints(rangePoints, zone),
                        chartEntries = buildCurrentMonthEntries(rangePoints, zone),
                        subtitle = formatMonthRangeLabel(zone),
                        insight = "月视图按自然月展示每日步数，点击柱体可查看对应日期。"
                    )
                    TimeRange.YEAR -> ActivityYearSection(points = rangePoints)
                }
            }
        }
    }
}

@Composable
private fun ActivityRangeTabs(selectedRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
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
                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color(0xFF22C55E))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF475569))
            }
        }
    }
}

@Composable
private fun ActivityDaySection(data: OppoHealthDashboardData?, recentPoints: List<HealthTrendPoint>) {
    val steps = data?.steps ?: 0
    val target = data?.stepGoal ?: 8000
    val progress = if (target > 0) (steps.toFloat() / target).coerceIn(0f, 1f) else 0f
    val calories = data?.calories ?: 0
    val activeMinutes = data?.moveMinutes ?: 0
    val distanceKm = (data?.distanceMeters ?: 0) / 1000f

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
            MetricCard(Modifier.weight(1f), "热量消耗", calories.toString(), "kcal", Icons.Default.Fireplace, Color(0xFFF97316))
            MetricCard(Modifier.weight(1f), "活动时长", activeMinutes.toString(), "min", Icons.Default.Schedule, Color(0xFF007BFF))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                Modifier.weight(1f),
                "距离",
                String.format(Locale.US, "%.2f", distanceKm),
                "km",
                Icons.Default.DirectionsRun,
                Color(0xFF10B981)
            )
            MetricCard(
                Modifier.weight(1f),
                "达标率",
                "${(progress * 100).toInt()}",
                "%",
                Icons.Default.CalendarToday,
                Color(0xFF0EA5E9)
            )
        }
        SectionCard("最近 7 天步数趋势") {
            if (recentPoints.isEmpty()) {
                EmptySectionText()
            } else {
                ActivityBarChart(entries = buildRecentDayEntries(recentPoints))
            }
        }
        InsightCard(
            title = "达标提示",
            body = if (steps <= 0) {
                "今天还没有读取到活动数据。"
            } else if (progress >= 1f) {
                "今天已完成活动目标，当前卡片均来自真实活动数据。"
            } else {
                "距离今日目标还差 ${target - steps} 步。"
            },
            solid = false
        )
    }
}

@Composable
private fun ActivityRangeSection(
    title: String,
    points: List<HealthTrendPoint>,
    chartEntries: List<ActivityBarEntry>?,
    subtitle: String,
    insight: String
) {
    val displayEntries = chartEntries ?: buildDefaultRangeEntries(points)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "平均步数", averageTrend(points).toString(), "步", Icons.Default.DirectionsRun, Color(0xFF10B981))
            MetricCard(Modifier.weight(1f), "总步数", totalTrend(points).toString(), "步", Icons.Default.Fireplace, Color(0xFFF97316))
        }
        SectionCard(title, subtitle) {
            if (displayEntries.isEmpty()) {
                EmptySectionText()
            } else {
                ActivityBarChart(entries = displayEntries)
            }
        }
        if (points.size > 7) {
            SectionCard("每日完成情况") {
                ActivityMonthGrid(points = points)
            }
        }
        InsightCard(
            title = "趋势洞察",
            body = if (points.isEmpty()) "当前时间范围没有真实活动汇总。" else insight,
            solid = true
        )
    }
}

@Composable
private fun ActivityYearSection(points: List<HealthTrendPoint>) {
    val monthly = monthlyAverageValues(points)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "日均步数", averageTrend(points).toString(), "步", Icons.Default.DirectionsRun, Color(0xFF10B981))
            MetricCard(
                Modifier.weight(1f),
                "最佳月份",
                monthLabelFromIndex(monthly.indices.maxByOrNull { monthly[it] } ?: 0),
                "",
                Icons.Default.CalendarToday,
                Color(0xFF0EA5E9)
            )
        }
        SectionCard("月均活动量", "最近 12 个月") {
            if (points.isEmpty()) {
                EmptySectionText()
            } else {
                ActivityYearChart(values = monthly)
            }
        }
        InsightCard(
            title = "年度里程碑",
            body = if (points.isEmpty()) {
                "最近 12 个月没有可展示的真实活动汇总。"
            } else {
                "年度折线按真实月均步数生成，不再使用占位曲线。"
            },
            solid = false
        )
    }
}

@Composable
private fun ActivityBarChart(entries: List<ActivityBarEntry>) {
    val maxValue = (entries.maxOfOrNull { it.value ?: 0 } ?: 0).coerceAtLeast(1)
    val barSpacing = when {
        entries.size > 25 -> 2.dp
        entries.size > 12 -> 6.dp
        else -> 10.dp
    }
    var selectedIndex by remember(entries) {
        mutableStateOf(entries.indexOfLast { !it.isFuture && it.value != null }.coerceAtLeast(0))
    }
    val selectedEntry = entries.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selectedEntry != null) {
            val selectedText = if (selectedEntry.value != null) {
                "${selectedEntry.date.monthValue}月${selectedEntry.date.dayOfMonth}日 ${selectedEntry.value} 步"
            } else {
                "${selectedEntry.date.monthValue}月${selectedEntry.date.dayOfMonth}日 暂无数据"
            }
            Text(text = selectedText, fontSize = 13.sp, color = Color(0xFF475569))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            horizontalArrangement = Arrangement.spacedBy(barSpacing),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { index, entry ->
                val valueRatio = (entry.value ?: 0).toFloat() / maxValue.toFloat()
                val isSelected = index == selectedIndex
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable(enabled = !entry.isFuture) { selectedIndex = index },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(999.dp))
                        )
                        if (!entry.isFuture) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((170.dp * valueRatio).coerceAtLeast(4.dp))
                                    .background(
                                        when {
                                            isSelected -> Color(0xFF10B981)
                                            entry.isHighlight -> Color(0xFF34D399)
                                            else -> Color(0xFF6EE7B7)
                                        },
                                        RoundedCornerShape(999.dp)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = entry.label,
                        fontSize = if (entries.size > 25) 9.sp else 11.sp,
                        color = if (entry.isFuture) Color(0xFFCBD5E1) else Color(0xFF94A3B8),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityMonthGrid(points: List<HealthTrendPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }
        }
        buildMonthGridCells(points).chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(monthGridCellColor(cell), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cell.date != null) {
                            Text(
                                text = cell.date.dayOfMonth.toString(),
                                color = monthGridCellTextColor(cell),
                                fontSize = 12.sp
                            )
                        }
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
private fun ActivityYearChart(values: List<Int>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val minValue = 0
                val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
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
                    drawCircle(Color.White, radius = 9f, center = Offset(x, y))
                    drawCircle(Color(0xFF10B981), radius = 6f, center = Offset(x, y))
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
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color
) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(unit, fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 4.dp))
                }
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
                modifier = Modifier
                    .size(38.dp)
                    .background(if (solid) Color.White.copy(alpha = 0.18f) else Color(0x1A10B981), RoundedCornerShape(12.dp)),
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
private fun EmptySectionText() {
    Text("暂无真实数据", color = Color(0xFF94A3B8))
}

private fun buildRecentDayEntries(points: List<HealthTrendPoint>, zone: ZoneId = ZoneId.systemDefault()): List<ActivityBarEntry> {
    val recentPoints = lastDays(points, 7)
    return recentPoints.mapIndexed { index, point ->
        val date = Instant.ofEpochMilli(point.timestamp).atZone(zone).toLocalDate()
        ActivityBarEntry(
            date = date,
            value = point.value,
            label = date.dayOfMonth.toString(),
            isHighlight = index == recentPoints.lastIndex
        )
    }
}

private fun buildDefaultRangeEntries(points: List<HealthTrendPoint>, zone: ZoneId = ZoneId.systemDefault()): List<ActivityBarEntry> {
    return points.map { point ->
        val date = Instant.ofEpochMilli(point.timestamp).atZone(zone).toLocalDate()
        ActivityBarEntry(
            date = date,
            value = point.value,
            label = date.dayOfMonth.toString()
        )
    }
}

private fun buildCurrentMonthEntries(points: List<HealthTrendPoint>, zone: ZoneId = ZoneId.systemDefault()): List<ActivityBarEntry> {
    val today = LocalDate.now(zone)
    val month = YearMonth.from(today)
    val valuesByDate = points.associateBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    return (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        val isFuture = date.isAfter(today)
        ActivityBarEntry(
            date = date,
            value = if (isFuture) null else (valuesByDate[date]?.value ?: 0),
            label = when {
                day == 1 -> "1"
                day == month.lengthOfMonth() -> day.toString()
                day % 5 == 0 -> day.toString()
                else -> ""
            },
            isFuture = isFuture,
            isHighlight = date == today
        )
    }
}

private fun buildCurrentMonthPoints(points: List<HealthTrendPoint>, zone: ZoneId = ZoneId.systemDefault()): List<HealthTrendPoint> {
    return buildCurrentMonthEntries(points, zone)
        .filter { !it.isFuture }
        .map { entry ->
            HealthTrendPoint(
                timestamp = entry.date.atStartOfDay(zone).toInstant().toEpochMilli(),
                value = entry.value ?: 0
            )
        }
}

private fun buildCurrentWeekEntries(points: List<HealthTrendPoint>, zone: ZoneId = ZoneId.systemDefault()): List<ActivityBarEntry> {
    val today = LocalDate.now(zone)
    val monday = today.with(DayOfWeek.MONDAY)
    val valuesByDate = points.associateBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    return (0..6).map { offset ->
        val date = monday.plusDays(offset.toLong())
        val isFuture = date.isAfter(today)
        ActivityBarEntry(
            date = date,
            value = if (isFuture) null else (valuesByDate[date]?.value ?: 0),
            label = date.dayOfMonth.toString(),
            isFuture = isFuture,
            isHighlight = date == today
        )
    }
}

private fun buildCurrentWeekPoints(points: List<HealthTrendPoint>, zone: ZoneId = ZoneId.systemDefault()): List<HealthTrendPoint> {
    return buildCurrentWeekEntries(points, zone)
        .filter { !it.isFuture }
        .map { entry ->
            HealthTrendPoint(
                timestamp = entry.date.atStartOfDay(zone).toInstant().toEpochMilli(),
                value = entry.value ?: 0
            )
        }
}

private fun currentWeekWindow(zone: ZoneId = ZoneId.systemDefault()): TimeWindow {
    val today = LocalDate.now(zone)
    val monday = today.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    return TimeWindow(
        start = monday.atStartOfDay(zone).toInstant().toEpochMilli(),
        end = sunday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    )
}

private fun currentMonthWindow(zone: ZoneId = ZoneId.systemDefault()): TimeWindow {
    val today = LocalDate.now(zone)
    val month = YearMonth.from(today)
    val start = month.atDay(1)
    val end = month.atEndOfMonth()
    return TimeWindow(
        start = start.atStartOfDay(zone).toInstant().toEpochMilli(),
        end = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    )
}

private fun formatWeekRangeLabel(zone: ZoneId = ZoneId.systemDefault()): String {
    val today = LocalDate.now(zone)
    val monday = today.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    return "${monday.monthValue}月${monday.dayOfMonth}日 - ${sunday.monthValue}月${sunday.dayOfMonth}日"
}

private fun formatMonthRangeLabel(zone: ZoneId = ZoneId.systemDefault()): String {
    val today = LocalDate.now(zone)
    val month = YearMonth.from(today)
    return "${month.monthValue}月1日 - ${month.monthValue}月${month.lengthOfMonth()}日"
}

private fun buildMonthGridCells(points: List<HealthTrendPoint>, zone: ZoneId = ZoneId.systemDefault()): List<ActivityMonthGridCell> {
    val today = LocalDate.now(zone)
    val month = YearMonth.from(today)
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val valuesByDate = points.associateBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    val cells = mutableListOf<ActivityMonthGridCell>()
    repeat(leadingBlanks) {
        cells += ActivityMonthGridCell(date = null)
    }
    (1..month.lengthOfMonth()).forEach { day ->
        val date = month.atDay(day)
        val isFuture = date.isAfter(today)
        cells += ActivityMonthGridCell(
            date = date,
            value = if (isFuture) null else (valuesByDate[date]?.value ?: 0),
            isFuture = isFuture
        )
    }
    while (cells.size % 7 != 0) {
        cells += ActivityMonthGridCell(date = null)
    }
    return cells
}

private fun monthGridCellColor(cell: ActivityMonthGridCell): Color {
    if (cell.date == null) return Color.Transparent
    if (cell.isFuture) return Color(0xFFF1F5F9)
    val value = cell.value ?: 0
    return when {
        value >= 8000 -> Color(0xFF10B981)
        value >= 5600 -> Color(0xFF34D399)
        else -> Color(0xFFF1F5F9)
    }
}

private fun monthGridCellTextColor(cell: ActivityMonthGridCell): Color {
    if (cell.date == null) return Color.Transparent
    return if (cell.isFuture || (cell.value ?: 0) < 5600) Color(0xFF94A3B8) else Color.White
}

private fun formatRangeLabel(points: List<HealthTrendPoint>): String {
    if (points.isEmpty()) return "暂无数据"
    val format = SimpleDateFormat("M月d日", Locale.CHINA)
    return "${format.format(Date(points.first().timestamp))} - ${format.format(Date(points.last().timestamp))}"
}

private fun buildActivityShareText(
    range: TimeRange,
    dashboardData: OppoHealthDashboardData?,
    points: List<HealthTrendPoint>,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val displayPoints = when (range) {
        TimeRange.DAY -> lastDays(points, 7)
        TimeRange.WEEK -> buildCurrentWeekPoints(points, zone)
        TimeRange.MONTH -> buildCurrentMonthPoints(points, zone)
        TimeRange.YEAR -> points
    }
    val steps = dashboardData?.steps ?: 0
    if (range == TimeRange.DAY && steps <= 0 && displayPoints.isEmpty()) return ""
    if (range != TimeRange.DAY && displayPoints.isEmpty()) return ""

    val rangeLabel = when (range) {
        TimeRange.DAY -> LocalDate.now(zone).let { "${it.monthValue}月${it.dayOfMonth}日" }
        TimeRange.WEEK -> formatWeekRangeLabel(zone)
        TimeRange.MONTH -> formatMonthRangeLabel(zone)
        TimeRange.YEAR -> "${LocalDate.now(zone).year}年"
    }
    val total = if (range == TimeRange.DAY) steps else totalTrend(displayPoints)
    val average = if (range == TimeRange.DAY) steps else averageTrend(displayPoints)
    val best = displayPoints.maxByOrNull { it.value }

    return buildString {
        append(rangeLabel).append(" 活动详情").append('\n')
        append("总步数：").append(total).append(" 步").append('\n')
        append("平均步数：").append(average).append(" 步").append('\n')
        dashboardData?.calories?.takeIf { range == TimeRange.DAY }?.let {
            append("热量消耗：").append(it).append(" kcal").append('\n')
        }
        dashboardData?.moveMinutes?.takeIf { range == TimeRange.DAY }?.let {
            append("活动时长：").append(it).append(" 分钟").append('\n')
        }
        best?.let {
            val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            append("最高步数：").append(date.monthValue).append("月").append(date.dayOfMonth).append("日 ")
                .append(it.value).append(" 步")
        }
    }
}

private fun monthLabelFromIndex(index: Int): String = "${index + 1}月"

