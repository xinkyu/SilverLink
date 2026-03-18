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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.feature.health.OppoHealthDashboardData
import com.silverlink.app.feature.health.OppoHealthSdkManager
import com.silverlink.app.ui.components.TimeRange
import com.silverlink.sdk.health.SleepSummaryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(TimeRange.DAY) }
    val recentSummaries = healthDashboardData?.sleepDailySummary.orEmpty().sortedBy { it.timestamp }
    val rangePoints by produceState(
        initialValue = emptyList<SleepSummaryPoint>(),
        selectedRange,
        healthDashboardData
    ) {
        value = when (selectedRange) {
            TimeRange.DAY -> recentSummaries.takeLast(7)
            TimeRange.WEEK -> {
                val window = currentTimeWindow(TimeRange.WEEK)
                OppoHealthSdkManager.getSleepSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            TimeRange.MONTH -> {
                val window = currentTimeWindow(TimeRange.MONTH)
                OppoHealthSdkManager.getSleepSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            TimeRange.YEAR -> {
                val window = currentTimeWindow(TimeRange.YEAR)
                OppoHealthSdkManager.getSleepSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            else -> emptyList()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = { Text("睡眠分析", fontWeight = FontWeight.Bold) },
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
                SleepRangeTabs(
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRange = it }
                )
            }
            item {
                SleepSourceCard(
                    title = "数据来源",
                    body = if (selectedRange == TimeRange.DAY) {
                        "日视图使用真实睡眠时长、睡眠分期和最近 7 天评分。"
                    } else {
                        "周、月、年视图使用 OPPO 健康真实睡眠汇总。"
                    }
                )
            }
            item {
                when (selectedRange) {
                    TimeRange.DAY -> SleepDaySection(healthDashboardData, rangePoints)
                    TimeRange.WEEK -> SleepRangeSection(points = rangePoints, title = "本周睡眠", showGrid = false)
                    TimeRange.MONTH -> SleepRangeSection(points = rangePoints, title = "本月睡眠", showGrid = true)
                    TimeRange.YEAR -> SleepYearSection(points = rangePoints)
                    else -> SleepDaySection(healthDashboardData, rangePoints)
                }
            }
        }
    }
}

@Composable
private fun SleepRangeTabs(selectedRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
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
private fun SleepSourceCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF))
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
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF007BFF))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF475569))
            }
        }
    }
}

@Composable
private fun SleepDaySection(data: OppoHealthDashboardData?, recentScores: List<SleepSummaryPoint>) {
    val sleepMinutes = data?.sleepMinutes ?: 0
    val sleepScore = data?.sleepScore ?: 0
    val deepSleepMinutes = data?.sleepDeepMinutes ?: 0
    val lightSleepMinutes = data?.sleepLightMinutes ?: 0
    val remMinutes = data?.sleepRemMinutes ?: 0
    val awakeMinutes = data?.sleepAwakeMinutes ?: 0
    val totalStageMinutes = (deepSleepMinutes + lightSleepMinutes + remMinutes + awakeMinutes).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF007BFF), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(formatDuration(sleepMinutes), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("昨晚睡眠", color = Color(0xFF64748B), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("评分", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text(sleepScore.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007BFF))
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color(0xFFE2E8F0)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("清醒", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text(formatDuration(awakeMinutes), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                }
            }
        }

        SleepSectionCard("睡眠分期") {
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
                        progress = { deepSleepMinutes.toFloat() / totalStageMinutes.toFloat() },
                        color = Color(0xFF007BFF),
                        strokeWidth = 12.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(sleepScore.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("评分", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    }
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    SleepStageLegend("深睡", formatDuration(deepSleepMinutes), Color(0xFF007BFF))
                    SleepStageLegend("浅睡", formatDuration(lightSleepMinutes), Color(0xFF66B2FF))
                    SleepStageLegend("REM", formatDuration(remMinutes), Color(0xFFB2D8FF))
                    SleepStageLegend("清醒", formatDuration(awakeMinutes), Color(0xFFE2E8F0))
                }
            }
        }

        SleepSectionCard("评分历史", "最近 7 天") {
            if (recentScores.isEmpty()) {
                EmptySleepText()
            } else {
                SleepScoreBars(points = recentScores)
            }
        }

        SleepInsightCard(
            title = "睡眠提示",
            body = if (sleepMinutes <= 0) {
                "最近一晚还没有返回真实睡眠汇总。"
            } else {
                "深睡 ${formatDuration(deepSleepMinutes)}，浅睡 ${formatDuration(lightSleepMinutes)}，REM ${formatDuration(remMinutes)}。"
            },
            highlight = true
        )
    }
}

@Composable
private fun SleepRangeSection(points: List<SleepSummaryPoint>, title: String, showGrid: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SleepMetricCard(Modifier.weight(1f), "平均时长", formatDuration(averageSleepMinutes(points)), "", Icons.Default.TrendingUp, Color(0xFF10B981))
            SleepMetricCard(Modifier.weight(1f), "平均评分", averageSleepScore(points).toString(), "", Icons.Default.TrendingDown, Color(0xFFEF4444))
        }
        SleepSectionCard(title) {
            if (points.isEmpty()) {
                EmptySleepText()
            } else {
                SleepCompositionBars(points = points)
            }
        }
        if (showGrid) {
            SleepSectionCard("每日评分热力图") {
                SleepMonthGrid(points = points.takeLast(30))
            }
        } else {
            SleepSectionCard("睡眠质量分布") {
                SleepQualityDistribution(points = points)
            }
        }
        SleepInsightCard(
            title = "趋势洞察",
            body = if (points.isEmpty()) {
                "当前时间范围没有真实睡眠汇总。"
            } else {
                "当前页面的时长、评分和分期图都来自真实睡眠汇总。"
            },
            highlight = false
        )
    }
}

@Composable
private fun SleepYearSection(points: List<SleepSummaryPoint>) {
    val values = sleepMonthlyAverageHours(points)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SleepMetricCard(Modifier.weight(1f), "全年平均", formatDuration(averageSleepMinutes(points)), "", Icons.Default.Bedtime, Color(0xFF007BFF))
            SleepMetricCard(
                Modifier.weight(1f),
                "最佳月份",
                monthLabelFromIndex(values.indices.maxByOrNull { values[it] } ?: 0),
                "",
                Icons.Default.CalendarToday,
                Color(0xFF007BFF)
            )
        }
        SleepSectionCard("月均睡眠时长", "最近 12 个月") {
            if (points.isEmpty()) {
                EmptySleepText()
            } else {
                SleepYearChart(values = values)
            }
        }
        SleepInsightCard(
            title = "年度趋势",
            body = if (points.isEmpty()) {
                "最近 12 个月没有真实睡眠汇总。"
            } else {
                "年度折线按真实月均睡眠时长生成。"
            },
            highlight = false
        )
    }
}

@Composable
private fun SleepStageLegend(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
        }
        Text(value, fontSize = 14.sp, color = Color(0xFF64748B))
    }
}

@Composable
private fun SleepQualityDistribution(points: List<SleepSummaryPoint>) {
    val goodCount = points.count { it.score >= 80 }
    val fairCount = points.count { it.score in 60..79 }
    val poorCount = points.count { it.score < 60 }
    val total = points.size.coerceAtLeast(1)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                color = Color(0xFFE2E8F0),
                strokeWidth = 8.dp,
                modifier = Modifier.fillMaxSize(),
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { goodCount.toFloat() / total.toFloat() },
                color = Color(0xFF22C55E),
                strokeWidth = 8.dp,
                modifier = Modifier.fillMaxSize(),
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )
            Text("${goodCount * 100 / total}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            SleepQualityBar("优质", "${goodCount * 100 / total}%", goodCount.toFloat() / total.toFloat(), Color(0xFF22C55E))
            SleepQualityBar("一般", "${fairCount * 100 / total}%", fairCount.toFloat() / total.toFloat(), Color(0xFFEAB308))
            SleepQualityBar("欠佳", "${poorCount * 100 / total}%", poorCount.toFloat() / total.toFloat(), Color(0xFFEF4444))
        }
    }
}

@Composable
private fun SleepQualityBar(label: String, value: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = Color(0xFF64748B))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(Color(0xFFE2E8F0), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
            drawRoundRect(
                color,
                size = androidx.compose.ui.geometry.Size(size.width * progress.coerceIn(0f, 1f), size.height),
                cornerRadius = CornerRadius(size.height / 2, size.height / 2)
            )
        }
    }
}

@Composable
private fun SleepScoreBars(points: List<SleepSummaryPoint>) {
    val maxScore = 100f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEachIndexed { index, point ->
            val ratio = point.score / maxScore
            val isLast = index == points.lastIndex
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp * ratio)
                        .background(
                            if (isLast) Color(0xFF007BFF) else Color(0xFFE2E8F0),
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("E", Locale.CHINA).format(Date(point.timestamp)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLast) Color(0xFF007BFF) else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun SleepCompositionBars(points: List<SleepSummaryPoint>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEachIndexed { index, point ->
            val total = point.totalMinutes.coerceAtLeast(1)
            val deepRatio = point.deepSleepMinutes.toFloat() / total.toFloat()
            val lightRatio = point.lightSleepMinutes.toFloat() / total.toFloat()
            val remRatio = point.remMinutes.toFloat() / total.toFloat()
            val isLast = index == points.lastIndex
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Column(verticalArrangement = Arrangement.Bottom) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp * remRatio)
                                .background(if (isLast) Color(0x4D007BFF) else Color(0xFF93C5FD), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp * lightRatio)
                                .background(if (isLast) Color(0x99007BFF) else Color(0xFF3B82F6))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp * deepRatio)
                                .background(if (isLast) Color(0xFF007BFF) else Color(0xFF1E3A8A), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = SimpleDateFormat("E", Locale.CHINA).format(Date(point.timestamp)),
                    fontSize = 11.sp,
                    color = if (isLast) Color(0xFF007BFF) else Color(0xFF94A3B8),
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SleepMonthGrid(points: List<SleepSummaryPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { point ->
                    val color = when {
                        point.score >= 85 -> Color(0xFF007BFF)
                        point.score >= 70 -> Color(0xFF93C5FD)
                        else -> Color(0xFFF1F5F9)
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
                            color = if (color == Color(0xFFF1F5F9)) Color(0xFF94A3B8) else Color.White,
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
private fun SleepYearChart(values: List<Float>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val minValue = 0f
                val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
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
                    drawCircle(Color(0xFF007BFF), radius = 8f, center = Offset(x, y))
                }
                drawPath(linePath, color = Color(0xFF007BFF), style = Stroke(width = 6f, cap = StrokeCap.Round))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("1M", "3M", "5M", "7M", "9M", "11M").forEach {
                    Text(it, fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
private fun SleepMetricCard(
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
private fun SleepSectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
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
private fun SleepInsightCard(title: String, body: String, highlight: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlight) Color(0xFFEFF6FF) else Color.White)
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
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFF007BFF))
            }
            Column {
                Text(title, color = Color(0xFF007BFF), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(body, color = Color(0xFF475569), fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun EmptySleepText() {
    Text("暂无真实数据", color = Color(0xFF94A3B8))
}

private fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0h 0m"
    return "${minutes / 60}h ${minutes % 60}m"
}

private fun monthLabelFromIndex(index: Int): String = "${index + 1}月"
