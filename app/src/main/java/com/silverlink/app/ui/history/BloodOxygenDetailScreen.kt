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
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodOxygenDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(TimeRange.DAY) }
    val dayPoints = healthDashboardData?.bloodOxygenTimeline.orEmpty().sortedBy { it.timestamp }
    val rangePoints by produceState(
        initialValue = emptyList<HealthTrendPoint>(),
        selectedRange,
        healthDashboardData
    ) {
        value = when (selectedRange) {
            TimeRange.DAY -> dayPoints
            else -> {
                val window = currentTimeWindow(selectedRange)
                OppoHealthSdkManager.getBloodOxygenSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = { Text("血氧详情", fontWeight = FontWeight.Bold) },
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
                        else -> Icons.Default.IosShare
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
                BloodOxygenRangeTabs(
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRange = it }
                )
            }
            item {
                SourceCard(
                    title = "数据来源",
                    body = if (selectedRange == TimeRange.DAY) {
                        "今日视图读取真实血氧时间线。"
                    } else {
                        "周、月、年视图读取真实血氧汇总。"
                    }
                )
            }
            item {
                when (selectedRange) {
                    TimeRange.DAY -> BloodOxygenDaySection(healthDashboardData, rangePoints)
                    TimeRange.WEEK -> BloodOxygenRangeSection(points = lastDays(rangePoints, 7), isMonth = false)
                    TimeRange.MONTH -> BloodOxygenRangeSection(points = lastDays(rangePoints, 30), isMonth = true)
                    TimeRange.YEAR -> BloodOxygenYearSection(points = rangePoints)
                }
            }
        }
    }
}

@Composable
private fun BloodOxygenRangeTabs(selectedRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
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
                    .background(Color(0x1A10B981), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF10B981))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF475569))
            }
        }
    }
}

@Composable
private fun BloodOxygenDaySection(data: OppoHealthDashboardData?, points: List<HealthTrendPoint>) {
    val latest = data?.bloodOxygen?.takeIf { it > 0 } ?: points.lastOrNull()?.value ?: 0
    val average = averageTrend(points)
    val minValue = minTrend(points)
    val normalCount = points.count { it.value >= 95 }
    val lowCount = points.count { it.value in 90..94 }
    val warnCount = points.count { it.value < 90 }
    val total = points.size.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("${latest}%", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("最新血氧饱和度", color = Color(0xFF64748B), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今日均值", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text("${average}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color(0xFFE2E8F0)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("最低值", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text("${minValue}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    }
                }
            }
        }

        SectionCard("今日血氧趋势图") {
            if (points.isEmpty()) {
                EmptySectionText()
            } else {
                BloodOxygenLineChart(points)
            }
        }

        SectionCard("血氧分布") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        color = Color(0xFFE2E8F0),
                        strokeWidth = 12.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { normalCount.toFloat() / total.toFloat() },
                        color = Color(0xFF10B981),
                        strokeWidth = 12.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(bloodOxygenStatus(latest), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("状态", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    StageLegend("正常 (>=95%)", "${normalCount * 100 / total}%", Color(0xFF10B981))
                    StageLegend("偏低 (90-94%)", "${lowCount * 100 / total}%", Color(0xFFF59E0B))
                    StageLegend("预警 (<90%)", "${warnCount * 100 / total}%", Color(0xFFEF4444))
                }
            }
        }

        InsightCard(
            title = "血氧提示",
            body = if (points.isEmpty()) {
                "今天还没有读取到血氧时间线。"
            } else {
                "今日血氧范围 ${minTrend(points)}% - ${maxTrend(points)}%，分布统计来自真实采样。"
            },
            highlight = true
        )
    }
}

@Composable
private fun BloodOxygenRangeSection(points: List<HealthTrendPoint>, isMonth: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), if (isMonth) "月平均" else "周平均", "${averageTrend(points)}%", "", Icons.Default.TrendingUp, Color(0xFF10B981))
            MetricCard(
                Modifier.weight(1f),
                if (isMonth) "异常天数" else "异常次数",
                points.count { it.value < 95 }.toString(),
                "",
                Icons.Default.Warning,
                Color(0xFFF59E0B)
            )
        }

        SectionCard(if (isMonth) "月度血氧概览" else "周度血氧趋势", formatRangeLabel(points)) {
            if (points.isEmpty()) {
                EmptySectionText()
            } else {
                BloodOxygenSummaryChart(points, showGrid = !isMonth)
            }
        }

        if (isMonth) {
            SectionCard("每日血氧热力图") {
                BloodOxygenMonthGrid(points)
            }
        }

        InsightCard(
            title = "趋势分析",
            body = if (points.isEmpty()) {
                "当前时间范围没有真实血氧汇总。"
            } else {
                "周月视图中的均值、异常计数和图表均由真实血氧汇总生成。"
            },
            highlight = false
        )
    }
}

@Composable
private fun BloodOxygenYearSection(points: List<HealthTrendPoint>) {
    val values = monthlyAverageValues(points)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "年均血氧", "${averageTrend(points)}%", "", Icons.Default.Favorite, Color(0xFF10B981))
            MetricCard(
                Modifier.weight(1f),
                "最低月份",
                monthLabelFromIndex(values.indices.minByOrNull { values[it] } ?: 0),
                "",
                Icons.Default.CalendarToday,
                Color(0xFFF59E0B)
            )
        }
        SectionCard("月均血氧趋势", "最近 12 个月") {
            if (points.isEmpty()) {
                EmptySectionText()
            } else {
                BloodOxygenYearChart(values)
            }
        }
        InsightCard(
            title = "年度趋势",
            body = if (points.isEmpty()) {
                "最近 12 个月没有读取到真实血氧汇总。"
            } else {
                "年度折线按真实月均血氧绘制，低点月份为 ${monthLabelFromIndex(values.indices.minByOrNull { values[it] } ?: 0)}。"
            },
            highlight = false
        )
    }
}

@Composable
private fun StageLegend(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
    }
}

@Composable
private fun BloodOxygenLineChart(points: List<HealthTrendPoint>) {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp)) {
            val minValue = 85f
            val maxValue = 100f
            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
            val linePath = Path()

            drawLine(Color(0xFFE2E8F0), Offset(0f, 0f), Offset(size.width, 0f), 2f)
            drawLine(Color(0xFFE2E8F0), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
            drawLine(
                Color(0xFFEF4444),
                Offset(0f, size.height - ((90f - minValue) / (maxValue - minValue)) * size.height),
                Offset(size.width, size.height - ((90f - minValue) / (maxValue - minValue)) * size.height),
                2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )

            points.forEachIndexed { index, point ->
                val x = stepX * index
                val ratio = (point.value - minValue) / (maxValue - minValue)
                val y = size.height - ratio * size.height
                if (index == 0) {
                    linePath.moveTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                }
            }
            drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 6f, cap = StrokeCap.Round))

            points.forEachIndexed { index, point ->
                val x = stepX * index
                val ratio = (point.value - minValue) / (maxValue - minValue)
                val y = size.height - ratio * size.height
                drawCircle(Color.White, radius = 9f, center = Offset(x, y))
                drawCircle(Color(0xFF10B981), radius = 6f, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun BloodOxygenSummaryChart(points: List<HealthTrendPoint>, showGrid: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp)) {
            val minValue = 85f
            val maxValue = 100f
            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
            val linePath = Path()

            if (showGrid) {
                drawLine(Color(0xFFE2E8F0), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
            }

            points.forEachIndexed { index, point ->
                val x = stepX * index
                val ratio = (point.value - minValue) / (maxValue - minValue)
                val y = size.height - ratio * size.height
                if (index == 0) {
                    linePath.moveTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                }
                drawCircle(Color.White, radius = 8f, center = Offset(x, y))
                drawCircle(Color(0xFF10B981), radius = 5f, center = Offset(x, y))
            }
            drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 6f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun BloodOxygenMonthGrid(points: List<HealthTrendPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lastDays(points, 30).chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { point ->
                    val color = when {
                        point.value < 90 -> Color(0xFFEF4444)
                        point.value < 95 -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(color, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = SimpleDateFormat("d", Locale.CHINA).format(Date(point.timestamp)),
                            color = Color.White,
                            fontSize = 12.sp
                        )
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
private fun BloodOxygenYearChart(values: List<Int>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val minValue = 85f
                val maxValue = (values.maxOrNull() ?: 100).toFloat().coerceAtLeast(minValue + 1f)
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    if (index == 0) {
                        linePath.moveTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                    }
                    drawCircle(Color.White, radius = 12f, center = Offset(x, y))
                    drawCircle(Color(0xFF10B981), radius = 8f, center = Offset(x, y))
                }
                drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 6f, cap = StrokeCap.Round))
            }
            Spacer(modifier = Modifier.height(16.dp))
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
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
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
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                if (subtitle != null) {
                    Text(subtitle, color = Color(0xFF007BFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun InsightCard(title: String, body: String, highlight: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlight) Color(0xFFECFDF5) else Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (highlight) Color.White else Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFF10B981))
            }
            Column {
                Text(title, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(body, color = Color(0xFF475569), fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun EmptySectionText() {
    Text("暂无真实数据", color = Color(0xFF94A3B8))
}

private fun bloodOxygenStatus(value: Int): String {
    return when {
        value >= 95 -> "正常"
        value >= 90 -> "偏低"
        else -> "预警"
    }
}

private fun formatRangeLabel(points: List<HealthTrendPoint>): String {
    if (points.isEmpty()) return "暂无数据"
    val format = SimpleDateFormat("M月d日", Locale.CHINA)
    return "${format.format(Date(points.first().timestamp))} - ${format.format(Date(points.last().timestamp))}"
}

private fun monthLabelFromIndex(index: Int): String = "${index + 1}月"
