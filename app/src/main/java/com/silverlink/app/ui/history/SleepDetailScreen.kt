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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max

private val SleepPrimaryBlue = Color(0xFF2F80ED)
private val SleepLightBlue = Color(0xFF6BB5FF)
private val SleepRemBlue = Color(0xFF8FD5FF)
private val SleepTrackBlue = Color(0xFFD7EAFE)
private val SleepMuted = Color(0xFF94A3B8)
private val SleepText = Color(0xFF0F172A)
private val SleepBody = Color(0xFF475569)

private data class DaySleepScoreEntry(
    val date: LocalDate,
    val score: Int?
)

private data class WeekSleepEntry(
    val date: LocalDate,
    val point: SleepSummaryPoint?,
    val isFuture: Boolean
)

private data class MonthSleepCell(
    val date: LocalDate?,
    val score: Int?,
    val isFuture: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
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
                val window = currentWeekWindow(zone)
                OppoHealthSdkManager.getSleepSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            TimeRange.MONTH -> {
                val window = currentMonthWindow(zone)
                OppoHealthSdkManager.getSleepSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            TimeRange.YEAR -> {
                val window = currentTimeWindow(TimeRange.YEAR, zone)
                OppoHealthSdkManager.getSleepSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
        }
    }

    val dayEntries = remember(recentSummaries, zone) { buildRecentDayScoreEntries(recentSummaries, zone) }
    val weekEntries = remember(rangePoints, zone) { buildWeekSleepEntries(rangePoints, zone) }
    val monthPoints = remember(rangePoints, zone) { currentMonthSleepPoints(rangePoints, zone) }
    val monthCells = remember(rangePoints, zone) { buildMonthSleepCells(rangePoints, zone) }

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
                    IconButton(
                        onClick = {
                            val summary = buildSleepShareText(
                                range = selectedRange,
                                dashboardData = healthDashboardData,
                                dayEntries = dayEntries,
                                weekEntries = weekEntries,
                                monthPoints = monthPoints,
                                yearPoints = rangePoints,
                                zone = zone
                            )
                            if (summary.isBlank()) {
                                Toast.makeText(context, "暂无可分享的睡眠分析", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, summary)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享睡眠分析"))
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
                SleepRangeTabs(
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRange = it }
                )
            }
            item {
                SleepSourceCard(
                    title = "数据来源",
                    body = if (selectedRange == TimeRange.DAY) {
                        "日视图使用最近 7 天睡眠评分、昨晚睡眠时长和睡眠分期。"
                    } else {
                        "周、月、年视图使用 OPPO 健康同步的真实睡眠汇总。"
                    }
                )
            }
            item {
                when (selectedRange) {
                    TimeRange.DAY -> SleepDaySection(healthDashboardData, dayEntries)
                    TimeRange.WEEK -> SleepWeekSection(weekEntries, zone)
                    TimeRange.MONTH -> SleepMonthSection(monthPoints, monthCells, zone)
                    TimeRange.YEAR -> SleepYearSection(rangePoints)
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
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEAF1F8),
        modifier = Modifier.fillMaxWidth()
    ) {
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
                            color = if (selected) SleepText else Color(0xFF64748B),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCEEFF))
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
                    .background(Color(0x263A8DFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = SleepPrimaryBlue)
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = SleepText)
                Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = SleepBody)
            }
        }
    }
}

@Composable
private fun SleepDaySection(data: OppoHealthDashboardData?, dayEntries: List<DaySleepScoreEntry>) {
    val sleepMinutes = data?.sleepMinutes ?: 0
    val sleepScore = data?.sleepScore ?: 0
    val deepSleepMinutes = data?.sleepDeepMinutes ?: 0
    val lightSleepMinutes = data?.sleepLightMinutes ?: 0
    val remMinutes = data?.sleepRemMinutes ?: 0
    val awakeMinutes = data?.sleepAwakeMinutes ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F1FF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = SleepPrimaryBlue,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(formatDuration(sleepMinutes), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = SleepText)
                Text("昨晚睡眠", color = Color(0xFF5E7490), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("评分", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SleepMuted)
                        Text(sleepScore.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SleepPrimaryBlue)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color(0xFFBFD9F7)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("清醒", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SleepMuted)
                        Text(formatDuration(awakeMinutes), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                }
            }
        }

        SleepSectionCard("睡眠分期") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SleepStageRing(
                    score = sleepScore,
                    deepSleepMinutes = deepSleepMinutes,
                    lightSleepMinutes = lightSleepMinutes,
                    remMinutes = remMinutes,
                    awakeMinutes = awakeMinutes
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    SleepStageLegend("深睡", formatDuration(deepSleepMinutes), SleepPrimaryBlue)
                    SleepStageLegend("浅睡", formatDuration(lightSleepMinutes), SleepLightBlue)
                    SleepStageLegend("快速眼动", formatDuration(remMinutes), SleepRemBlue)
                    SleepStageLegend("清醒", formatDuration(awakeMinutes), Color(0xFFD9E2EC))
                }
            }
        }

        SleepSectionCard("评分历史", "最近 7 天") {
            if (dayEntries.none { it.score != null }) {
                EmptySleepText()
            } else {
                SleepScoreBars(entries = dayEntries)
            }
        }

        SleepInsightCard(
            title = "睡眠提示",
            body = if (sleepMinutes <= 0) {
                "昨晚还没有同步到真实睡眠汇总。"
            } else {
                "昨晚深睡 ${formatDuration(deepSleepMinutes)}，浅睡 ${formatDuration(lightSleepMinutes)}，快速眼动 ${formatDuration(remMinutes)}。"
            },
            highlight = true
        )
    }
}

@Composable
private fun SleepStageRing(
    score: Int,
    deepSleepMinutes: Int,
    lightSleepMinutes: Int,
    remMinutes: Int,
    awakeMinutes: Int
) {
    val total = (deepSleepMinutes + lightSleepMinutes + remMinutes + awakeMinutes).coerceAtLeast(1)
    val segments = listOf(
        deepSleepMinutes to SleepPrimaryBlue,
        lightSleepMinutes to SleepLightBlue,
        remMinutes to SleepRemBlue,
        awakeMinutes to Color(0xFFD9E2EC)
    ).filter { it.first > 0 }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            drawArc(
                color = SleepTrackBlue,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            var startAngle = -90f
            segments.forEach { (minutes, color) ->
                val sweep = 360f * minutes / total.toFloat()
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = max(sweep - 3f, 1f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += sweep
            }
        }
        Text(
            text = score.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = SleepText
        )
    }
}

@Composable
private fun SleepWeekSection(entries: List<WeekSleepEntry>, zone: ZoneId) {
    val actualPoints = entries.mapNotNull { it.point }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SleepMetricCard(
                Modifier.weight(1f),
                "平均时长",
                formatDuration(averageSleepMinutes(actualPoints)),
                "",
                Icons.Default.TrendingUp,
                Color(0xFF10B981)
            )
            SleepMetricCard(
                Modifier.weight(1f),
                "平均评分",
                averageSleepScore(actualPoints).toString(),
                "",
                Icons.Default.TrendingDown,
                SleepPrimaryBlue
            )
        }
        SleepSectionCard("本周睡眠", formatWeekLabel(zone)) {
            if (entries.none { it.point != null }) {
                EmptySleepText()
            } else {
                SleepWeekChart(entries)
            }
        }
        SleepSectionCard("睡眠质量分布") {
            if (actualPoints.isEmpty()) {
                EmptySleepText()
            } else {
                SleepQualityDistribution(actualPoints)
            }
        }
        SleepInsightCard(
            title = "趋势洞察",
            body = if (actualPoints.isEmpty()) {
                "本周还没有同步到睡眠汇总。"
            } else {
                "本周柱状图按周一到周日排列，未到的日期不显示数据，点击柱体可查看当天睡眠时长和评分。"
            },
            highlight = false
        )
    }
}

@Composable
private fun SleepMonthSection(points: List<SleepSummaryPoint>, cells: List<MonthSleepCell>, zone: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SleepMetricCard(
                Modifier.weight(1f),
                "月均时长",
                formatDuration(averageSleepMinutes(points)),
                "",
                Icons.Default.TrendingUp,
                Color(0xFF10B981)
            )
            SleepMetricCard(
                Modifier.weight(1f),
                "月均评分",
                averageSleepScore(points).toString(),
                "",
                Icons.Default.CalendarToday,
                SleepPrimaryBlue
            )
        }
        SleepSectionCard("评分热力图", formatMonthLabel(zone)) {
            SleepMonthGrid(cells)
        }
        SleepInsightCard(
            title = "热力图说明",
            body = "热力图只显示本月 1 日到最后一日，按周一到周日排列。无数据日期显示为灰色，1 号之前的空位留空。",
            highlight = false
        )
    }
}

@Composable
private fun SleepYearSection(points: List<SleepSummaryPoint>) {
    val values = sleepMonthlyAverageHours(points)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SleepMetricCard(
                Modifier.weight(1f),
                "全年平均",
                formatDuration(averageSleepMinutes(points)),
                "",
                Icons.Default.Bedtime,
                SleepPrimaryBlue
            )
            SleepMetricCard(
                Modifier.weight(1f),
                "最佳月份",
                monthLabelFromIndex(values.indices.maxByOrNull { values[it] } ?: 0),
                "",
                Icons.Default.CalendarToday,
                SleepPrimaryBlue
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SleepText)
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                drawArc(
                    color = Color(0xFFE2E8F0),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF22C55E),
                    startAngle = -90f,
                    sweepAngle = 360f * goodCount / total.toFloat(),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text("${goodCount * 100 / total}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SleepText)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            SleepQualityBar("优质", "${goodCount * 100 / total}%", goodCount.toFloat() / total.toFloat(), Color(0xFF22C55E))
            SleepQualityBar("一般", "${fairCount * 100 / total}%", fairCount.toFloat() / total.toFloat(), Color(0xFFF59E0B))
            SleepQualityBar("欠佳", "${poorCount * 100 / total}%", poorCount.toFloat() / total.toFloat(), Color(0xFFEF4444))
        }
    }
}

@Composable
private fun SleepQualityBar(label: String, value: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = Color(0xFF64748B))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SleepText)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(Color(0xFFE2E8F0), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
            drawRoundRect(
                color = color,
                size = androidx.compose.ui.geometry.Size(size.width * progress.coerceIn(0f, 1f), size.height),
                cornerRadius = CornerRadius(size.height / 2, size.height / 2)
            )
        }
    }
}

@Composable
private fun SleepScoreBars(entries: List<DaySleepScoreEntry>) {
    var selectedIndex by remember(entries) {
        mutableStateOf(entries.indexOfLast { it.score != null }.coerceAtLeast(0))
    }
    val selected = entries.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        selected?.let { entry ->
            val detail = if (entry.score != null) {
                "${entry.date.monthValue}月${entry.date.dayOfMonth}日 睡眠评分 ${entry.score}"
            } else {
                "${entry.date.monthValue}月${entry.date.dayOfMonth}日 暂无评分"
            }
            Text(detail, fontSize = 13.sp, color = SleepBody)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { index, entry ->
                val ratio = ((entry.score ?: 0) / 100f).coerceIn(0f, 1f)
                val selectedBar = index == selectedIndex && entry.score != null
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .height(116.dp)
                            .fillMaxWidth()
                            .clickable(enabled = entry.score != null) { selectedIndex = index },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(116.dp)
                                .background(Color(0xFFE8EEF5), RoundedCornerShape(14.dp))
                        )
                        if (entry.score != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((116.dp * ratio).coerceAtLeast(8.dp))
                                    .background(
                                        if (selectedBar) SleepPrimaryBlue else Color(0xFFBFD6EE),
                                        RoundedCornerShape(14.dp)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${entry.date.dayOfMonth}号",
                        fontSize = 11.sp,
                        color = if (selectedBar) SleepPrimaryBlue else SleepMuted,
                        fontWeight = if (selectedBar) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepWeekChart(entries: List<WeekSleepEntry>) {
    var selectedIndex by remember(entries) {
        mutableStateOf(entries.indexOfLast { !it.isFuture && it.point != null }.coerceAtLeast(0))
    }
    val selected = entries.getOrNull(selectedIndex)
    val maxMinutes = (entries.maxOfOrNull { it.point?.totalMinutes ?: 0 } ?: 0).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        selected?.let { entry ->
            val detail = entry.point?.let { point ->
                "${entry.date.monthValue}月${entry.date.dayOfMonth}日 ${formatDuration(point.totalMinutes)}，评分 ${point.score}"
            } ?: "${entry.date.monthValue}月${entry.date.dayOfMonth}日 暂无睡眠数据"
            Text(detail, fontSize = 13.sp, color = SleepBody)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { index, entry ->
                val ratio = ((entry.point?.totalMinutes ?: 0) / maxMinutes.toFloat()).coerceIn(0f, 1f)
                val selectedBar = index == selectedIndex && entry.point != null
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .height(150.dp)
                            .fillMaxWidth()
                            .clickable(enabled = !entry.isFuture) { selectedIndex = index },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(Color(0xFFE7EEF6), RoundedCornerShape(22.dp))
                        )
                        if (!entry.isFuture && entry.point != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((150.dp * ratio).coerceAtLeast(10.dp))
                                    .background(
                                        if (selectedBar) SleepPrimaryBlue else Color(0xFFA8D0FF),
                                        RoundedCornerShape(22.dp)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = weekDayLabel(entry.date.dayOfWeek),
                        fontSize = 11.sp,
                        color = if (selectedBar) SleepPrimaryBlue else if (entry.isFuture) Color(0xFFCBD5E1) else SleepMuted,
                        fontWeight = if (selectedBar) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = entry.date.dayOfMonth.toString(),
                        fontSize = 11.sp,
                        color = if (selectedBar) SleepPrimaryBlue else if (entry.isFuture) Color(0xFFCBD5E1) else SleepMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepMonthGrid(cells: List<MonthSleepCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 12.sp, color = SleepMuted)
                }
            }
        }

        cells.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(monthCellColor(cell), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        cell.date?.let { date ->
                            Text(
                                text = date.dayOfMonth.toString(),
                                color = monthCellTextColor(cell),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem("高分", SleepPrimaryBlue)
            LegendItem("中等", SleepLightBlue)
            LegendItem("较低", Color(0xFFCDE4FF))
            LegendItem("无数据", Color(0xFFE5E7EB), SleepMuted)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, textColor: Color = SleepBody) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, fontSize = 12.sp, color = textColor)
    }
}

@Composable
private fun SleepYearChart(values: List<Float>) {
    var selectedIndex by remember(values) {
        mutableStateOf(values.indices.maxByOrNull { values[it] } ?: 0)
    }
    val selectedValue = values.getOrNull(selectedIndex) ?: 0f

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${monthLabelFromIndex(selectedIndex)} 平均睡眠 ${"%.1f".format(selectedValue)} 小时",
                fontSize = 13.sp,
                color = SleepBody
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = value / maxValue
                    val y = size.height - ratio * size.height
                    if (index == 0) {
                        linePath.moveTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                    }
                }
                drawPath(linePath, color = SleepPrimaryBlue, style = Stroke(width = 6f, cap = StrokeCap.Round))
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = value / maxValue
                    val y = size.height - ratio * size.height
                    drawCircle(
                        color = if (index == selectedIndex) SleepPrimaryBlue.copy(alpha = 0.18f) else Color.White,
                        radius = if (index == selectedIndex) 12f else 10f,
                        center = Offset(x, y)
                    )
                    drawCircle(Color.White, radius = if (index == selectedIndex) 7f else 6f, center = Offset(x, y))
                    drawCircle(SleepPrimaryBlue, radius = if (index == selectedIndex) 4.5f else 4f, center = Offset(x, y))
                }
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    values.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable { selectedIndex = index }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("1月", "3月", "5月", "7月", "9月", "11月").forEach {
                    Text(it, fontSize = 10.sp, color = SleepMuted)
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SleepText)
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
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SleepText)
                if (subtitle != null) {
                    Text(subtitle, color = SleepPrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = if (highlight) Color(0xFFEAF4FF) else Color.White)
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
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = SleepPrimaryBlue)
            }
            Column {
                Text(title, color = SleepPrimaryBlue, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(body, color = SleepBody, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun EmptySleepText() {
    Text("暂无真实数据", color = SleepMuted)
}

private fun buildRecentDayScoreEntries(points: List<SleepSummaryPoint>, zone: ZoneId): List<DaySleepScoreEntry> {
    val today = LocalDate.now(zone)
    val valuesByDate = latestSleepPointByDate(points, zone)
    return (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        DaySleepScoreEntry(date = date, score = valuesByDate[date]?.score)
    }
}

private fun buildWeekSleepEntries(points: List<SleepSummaryPoint>, zone: ZoneId): List<WeekSleepEntry> {
    val today = LocalDate.now(zone)
    val monday = today.with(DayOfWeek.MONDAY)
    val valuesByDate = latestSleepPointByDate(points, zone)
    return (0..6).map { offset ->
        val date = monday.plusDays(offset.toLong())
        val isFuture = date.isAfter(today)
        WeekSleepEntry(
            date = date,
            point = if (isFuture) null else valuesByDate[date],
            isFuture = isFuture
        )
    }
}

private fun currentMonthSleepPoints(points: List<SleepSummaryPoint>, zone: ZoneId): List<SleepSummaryPoint> {
    val month = YearMonth.from(LocalDate.now(zone))
    return points.filter {
        val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        YearMonth.from(date) == month
    }
}

private fun buildMonthSleepCells(points: List<SleepSummaryPoint>, zone: ZoneId): List<MonthSleepCell> {
    val month = YearMonth.from(LocalDate.now(zone))
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val valuesByDate = latestSleepPointByDate(points, zone)
    val cells = mutableListOf<MonthSleepCell>()
    repeat(leadingBlanks) {
        cells += MonthSleepCell(date = null, score = null, isFuture = false)
    }
    (1..month.lengthOfMonth()).forEach { day ->
        val date = month.atDay(day)
        val isFuture = date.isAfter(LocalDate.now(zone))
        cells += MonthSleepCell(
            date = date,
            score = if (isFuture) null else valuesByDate[date]?.score,
            isFuture = isFuture
        )
    }
    while (cells.size % 7 != 0) {
        cells += MonthSleepCell(date = null, score = null, isFuture = false)
    }
    return cells
}

private fun latestSleepPointByDate(points: List<SleepSummaryPoint>, zone: ZoneId): Map<LocalDate, SleepSummaryPoint> {
    return points
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .mapValues { (_, values) -> values.maxByOrNull { it.timestamp } }
        .mapNotNull { (date, point) -> point?.let { date to it } }
        .toMap()
}

private fun monthCellColor(cell: MonthSleepCell): Color {
    if (cell.date == null) return Color.Transparent
    if (cell.isFuture) return Color(0xFFE5E7EB)
    val score = cell.score ?: return Color(0xFFE5E7EB)
    return when {
        score >= 85 -> SleepPrimaryBlue
        score >= 70 -> SleepLightBlue
        else -> Color(0xFFCDE4FF)
    }
}

private fun monthCellTextColor(cell: MonthSleepCell): Color {
    if (cell.date == null) return Color.Transparent
    return if (cell.score == null || cell.isFuture) SleepMuted else Color.White
}

private fun currentWeekWindow(zone: ZoneId): TimeWindow {
    val today = LocalDate.now(zone)
    val monday = today.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    return TimeWindow(
        start = monday.atStartOfDay(zone).toInstant().toEpochMilli(),
        end = sunday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    )
}

private fun currentMonthWindow(zone: ZoneId): TimeWindow {
    val month = YearMonth.from(LocalDate.now(zone))
    return TimeWindow(
        start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        end = month.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    )
}

private fun formatWeekLabel(zone: ZoneId): String {
    val today = LocalDate.now(zone)
    val monday = today.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    return "${monday.monthValue}月${monday.dayOfMonth}日 - ${sunday.monthValue}月${sunday.dayOfMonth}日"
}

private fun formatMonthLabel(zone: ZoneId): String {
    val month = YearMonth.from(LocalDate.now(zone))
    return "${month.monthValue}月1日 - ${month.monthValue}月${month.lengthOfMonth()}日"
}

private fun weekDayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private fun buildSleepShareText(
    range: TimeRange,
    dashboardData: OppoHealthDashboardData?,
    dayEntries: List<DaySleepScoreEntry>,
    weekEntries: List<WeekSleepEntry>,
    monthPoints: List<SleepSummaryPoint>,
    yearPoints: List<SleepSummaryPoint>,
    zone: ZoneId
): String {
    return when (range) {
        TimeRange.DAY -> {
            val sleepMinutes = dashboardData?.sleepMinutes ?: 0
            val sleepScore = dashboardData?.sleepScore ?: 0
            if (sleepMinutes <= 0 && sleepScore <= 0) return ""
            val latestDayScore = dayEntries.lastOrNull { it.score != null }
            buildString {
                append("今日睡眠分析\n")
                append("昨晚睡眠：").append(formatDuration(sleepMinutes)).append('\n')
                append("睡眠评分：").append(sleepScore).append('\n')
                append("深睡：").append(formatDuration(dashboardData?.sleepDeepMinutes ?: 0)).append('\n')
                append("浅睡：").append(formatDuration(dashboardData?.sleepLightMinutes ?: 0)).append('\n')
                append("快速眼动：").append(formatDuration(dashboardData?.sleepRemMinutes ?: 0)).append('\n')
                latestDayScore?.let {
                    append("最近一次评分日期：${it.date.monthValue}月${it.date.dayOfMonth}日").append('\n')
                }
            }
        }
        TimeRange.WEEK -> {
            val actualPoints = weekEntries.mapNotNull { it.point }
            if (actualPoints.isEmpty()) return ""
            buildString {
                append("本周睡眠分析 ").append(formatWeekLabel(zone)).append('\n')
                append("平均睡眠：").append(formatDuration(averageSleepMinutes(actualPoints))).append('\n')
                append("平均评分：").append(averageSleepScore(actualPoints)).append('\n')
                weekEntries.firstOrNull { !it.isFuture && it.point != null }?.let { first ->
                    append("本周首个有数据日期：${first.date.monthValue}月${first.date.dayOfMonth}日").append('\n')
                }
            }
        }
        TimeRange.MONTH -> {
            if (monthPoints.isEmpty()) return ""
            buildString {
                append("本月睡眠分析 ").append(formatMonthLabel(zone)).append('\n')
                append("记录天数：").append(monthPoints.size).append('\n')
                append("月均睡眠：").append(formatDuration(averageSleepMinutes(monthPoints))).append('\n')
                append("月均评分：").append(averageSleepScore(monthPoints)).append('\n')
            }
        }
        TimeRange.YEAR -> {
            if (yearPoints.isEmpty()) return ""
            val values = sleepMonthlyAverageHours(yearPoints)
            buildString {
                append("年度睡眠分析\n")
                append("全年平均睡眠：").append(formatDuration(averageSleepMinutes(yearPoints))).append('\n')
                append("全年平均评分：").append(averageSleepScore(yearPoints)).append('\n')
                append("最佳月份：").append(monthLabelFromIndex(values.indices.maxByOrNull { values[it] } ?: 0))
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0小时0分"
    return "${minutes / 60}小时${minutes % 60}分"
}

private fun monthLabelFromIndex(index: Int): String = "${index + 1}月"
