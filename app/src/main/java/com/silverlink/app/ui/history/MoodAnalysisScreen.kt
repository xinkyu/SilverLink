package com.silverlink.app.ui.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.ui.components.MoodAnalysisCard
import com.silverlink.app.ui.components.MoodDetailCard
import com.silverlink.app.ui.components.MoodTimePoint
import com.silverlink.app.ui.components.MoodTimelineChart
import com.silverlink.app.ui.components.TimeRange
import com.silverlink.app.ui.components.TimeRangeSelector
import com.silverlink.app.ui.components.getMoodColor
import com.silverlink.app.ui.components.getMoodDisplayText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAnalysisScreen(
    initialPeriod: String?,
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedRange by viewModel.selectedRange.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val moodPoints by viewModel.moodPoints.collectAsState()
    val currentMood by viewModel.currentMood.collectAsState()
    val latestTime by viewModel.latestTime.collectAsState()
    val selectedPoint by viewModel.selectedMoodPoint.collectAsState()
    val moodAnalysis by viewModel.moodAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAllLogs by rememberSaveable { mutableStateOf(false) }
    val stats = remember(moodPoints, selectedRange, selectedDate) {
        buildMoodStats(moodPoints, selectedRange, selectedDate)
    }
    val initialRange = remember(initialPeriod) { periodToRange(initialPeriod) }

    LaunchedEffect(initialRange) {
        viewModel.setTimeRange(initialRange)
    }

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "情绪分析",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val summary = buildShareText(selectedRange, selectedDate, stats, moodAnalysis)
                            if (summary.isBlank()) {
                                Toast.makeText(context, "暂无可分享的情绪数据", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, summary)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享情绪分析"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            TimeRangeSelector(
                selectedRange = selectedRange,
                selectedDate = selectedDate,
                onRangeSelected = {
                    showAllLogs = false
                    viewModel.setTimeRange(it)
                },
                onDateSelected = {
                    showAllLogs = false
                    viewModel.setSelectedDate(it)
                },
                primaryColor = Color(0xFF007BFF)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF007BFF))
                }
            } else if (moodPoints.isEmpty()) {
                EmptyMoodState(selectedRange, selectedDate)
            } else {
                MoodOverviewSection(
                    range = selectedRange,
                    selectedDate = selectedDate,
                    currentMood = currentMood,
                    latestTime = latestTime,
                    stats = stats
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedRange) {
                    TimeRange.DAY -> {
                        MoodTimelineChart(
                            moodPoints = moodPoints,
                            onPointClick = viewModel::selectMoodPoint,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MoodDetailCardWithPadding(
                            moodPoint = selectedPoint,
                            onDismiss = { viewModel.selectMoodPoint(null) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MoodInsightCard(
                            title = "今日观察",
                            content = buildDayInsight(moodPoints)
                        )
                    }
                    TimeRange.WEEK -> {
                        WeeklyTrendSection(stats.dailySummaries)
                        Spacer(modifier = Modifier.height(16.dp))
                        MoodDistributionSection(stats.moodCounts)
                    }
                    TimeRange.MONTH -> {
                        MonthlyCalendarSection(
                            selectedDate = selectedDate,
                            dailySummaries = stats.dailySummaries,
                            onDayClick = {
                                showAllLogs = false
                                viewModel.setSelectedDate(it)
                                viewModel.setTimeRange(TimeRange.DAY)
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MoodDistributionSection(stats.moodCounts)
                    }
                    TimeRange.YEAR -> {
                        YearlyTrendSection(stats.monthlySummaries)
                        Spacer(modifier = Modifier.height(16.dp))
                        MoodDistributionSection(stats.moodCounts)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedRange != TimeRange.DAY || moodPoints.any { it.note.isNotBlank() }) {
                    MoodAnalysisCard(
                        analysis = if (selectedRange == TimeRange.DAY) buildDayInsight(moodPoints) else moodAnalysis,
                        isLoading = isAnalyzing,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                MoodLogSection(
                    points = moodPoints,
                    expanded = showAllLogs,
                    onToggleExpanded = { showAllLogs = !showAllLogs },
                    onPointClick = viewModel::selectMoodPoint
                )
            }
        }
    }
}

@Composable
private fun MoodOverviewSection(
    range: TimeRange,
    selectedDate: Date,
    currentMood: String?,
    latestTime: String?,
    stats: MoodStats
) {
    val dateLabel = remember(range, selectedDate) { buildRangeLabel(range, selectedDate) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = dateLabel,
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentMood?.let(::getMoodDisplayText) ?: stats.dominantMood?.let(::getMoodDisplayText) ?: "暂无记录",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentMood?.let(::getMoodColor) ?: stats.dominantMood?.let(::getMoodColor) ?: Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = latestTime?.let { "最新记录时间 $it" } ?: "共 ${stats.totalLogs} 条记录",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewMetricCard(
                title = "平均情绪",
                value = stats.averageMoodLabel,
                helper = "均分 ${stats.averageScore.format1()} / 5",
                modifier = Modifier.weight(1f)
            )
            OverviewMetricCard(
                title = if (range == TimeRange.YEAR) "活跃月份" else "活跃天数",
                value = if (range == TimeRange.YEAR) "${stats.activePeriods}" else "${stats.daysWithLogs}",
                helper = "记录 ${stats.totalLogs} 次",
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewMetricCard(
                title = "积极情绪占比",
                value = "${stats.positiveRate.roundToInt()}%",
                helper = "愉悦 ${stats.moodCounts["HAPPY"] ?: 0} 次",
                modifier = Modifier.weight(1f)
            )
            OverviewMetricCard(
                title = "波动程度",
                value = stats.stabilityLabel,
                helper = "跨度 ${stats.minScore.format1()} - ${stats.maxScore.format1()}",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OverviewMetricCard(
    title: String,
    value: String,
    helper: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(4.dp))
            Text(helper, fontSize = 12.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun WeeklyTrendSection(dailySummaries: List<DailyMoodSummary>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("本周走势", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                dailySummaries.forEach { summary ->
                    val progress = (summary.averageScore / 5f).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height((120 * progress).dp.coerceAtLeast(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(summary.color.copy(alpha = 0.85f))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(summary.shortLabel, fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyCalendarSection(
    selectedDate: Date,
    dailySummaries: List<DailyMoodSummary>,
    onDayClick: (Date) -> Unit
) {
    val monthLabel = remember(selectedDate) {
        SimpleDateFormat("yyyy年M月", Locale.CHINA).format(selectedDate)
    }
    val calendar = Calendar.getInstance().apply {
        time = selectedDate
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val month = calendar.get(Calendar.MONTH)
    val startOffset = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val summariesByDay = dailySummaries.associateBy { it.dayOfMonth }
    val weekTitles = listOf("日", "一", "二", "三", "四", "五", "六")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF007BFF))
                Spacer(modifier = Modifier.width(8.dp))
                Text(monthLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekTitles.forEach { title ->
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            var dayCounter = 1
            repeat(6) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { column ->
                        val cellIndex = row * 7 + column
                        if (cellIndex < startOffset || dayCounter > daysInMonth) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            )
                        } else {
                            val summary = summariesByDay[dayCounter]
                            val dayDate = Calendar.getInstance().apply {
                                time = selectedDate
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, dayCounter)
                            }.time

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (summary != null) summary.color.copy(alpha = 0.10f) else Color.Transparent)
                                    .clickable(enabled = summary != null) { onDayClick(dayDate) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayCounter.toString(),
                                        color = if (summary != null) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                        fontWeight = if (summary != null) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(summary?.color ?: Color.Transparent)
                                    )
                                }
                            }
                            dayCounter += 1
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearlyTrendSection(monthlySummaries: List<MonthlyMoodSummary>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = Color(0xFF007BFF))
                Spacer(modifier = Modifier.width(8.dp))
                Text("年度月度概览", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
            }

            monthlySummaries.forEach { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(summary.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                        Text(
                            text = "${summary.recordCount} 条 · ${getMoodDisplayText(summary.dominantMood ?: "NEUTRAL")}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (summary.averageScore / 5f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = summary.color,
                        trackColor = summary.color.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodDistributionSection(moodCounts: Map<String, Int>) {
    val total = moodCounts.values.sum().coerceAtLeast(1)
    val displayOrder = listOf("HAPPY", "NEUTRAL", "ANXIOUS", "SAD", "ANGRY")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("情绪分布", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
            displayOrder.filter { (moodCounts[it] ?: 0) > 0 }.forEach { mood ->
                val count = moodCounts[mood] ?: 0
                val progress = count / total.toFloat()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(getMoodDisplayText(mood), fontSize = 14.sp, color = Color(0xFF0F172A))
                        Text("$count 次", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = getMoodColor(mood),
                        trackColor = getMoodColor(mood).copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodInsightCard(title: String, content: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF007BFF).copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF007BFF))
            Spacer(modifier = Modifier.height(8.dp))
            Text(content, fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFF475569))
        }
    }
}

@Composable
private fun MoodLogSection(
    points: List<MoodTimePoint>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPointClick: (MoodTimePoint) -> Unit
) {
    val visiblePoints = if (expanded) points.sortedByDescending { it.timestamp } else points.sortedByDescending { it.timestamp }.take(5)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("情绪记录", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            if (points.size > 5) {
                Text(
                    text = if (expanded) "收起" else "查看全部",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF007BFF),
                    modifier = Modifier.clickable(onClick = onToggleExpanded)
                )
            }
        }

        visiblePoints.forEach { point ->
            MoodLogItem(point = point, onClick = { onPointClick(point) })
        }
    }
}

@Composable
private fun MoodLogItem(
    point: MoodTimePoint,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(getMoodColor(point.mood).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(getMoodColor(point.mood))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${getMoodDisplayText(point.mood)} · ${if (point.date.isBlank()) point.time else "${point.date} ${point.time}"}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = point.note.ifBlank { "暂无对话摘要" },
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun MoodDetailCardWithPadding(
    moodPoint: MoodTimePoint?,
    onDismiss: () -> Unit
) {
    if (moodPoint == null) return
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MoodDetailCard(moodPoint = moodPoint, onDismiss = onDismiss)
    }
}

@Composable
private fun EmptyMoodState(range: TimeRange, selectedDate: Date) {
    val label = remember(range, selectedDate) { buildRangeLabel(range, selectedDate) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("暂无情绪记录", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$label 还没有同步到可分析的情绪数据。",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

private data class MoodStats(
    val averageScore: Float,
    val averageMoodLabel: String,
    val dominantMood: String?,
    val totalLogs: Int,
    val daysWithLogs: Int,
    val activePeriods: Int,
    val positiveRate: Float,
    val stabilityLabel: String,
    val minScore: Float,
    val maxScore: Float,
    val moodCounts: Map<String, Int>,
    val dailySummaries: List<DailyMoodSummary>,
    val monthlySummaries: List<MonthlyMoodSummary>
)

private data class DailyMoodSummary(
    val date: String,
    val shortLabel: String,
    val averageScore: Float,
    val dominantMood: String?,
    val recordCount: Int,
    val dayOfMonth: Int,
    val color: Color
)

private data class MonthlyMoodSummary(
    val label: String,
    val averageScore: Float,
    val dominantMood: String?,
    val recordCount: Int,
    val color: Color
)

private fun buildMoodStats(
    points: List<MoodTimePoint>,
    range: TimeRange,
    selectedDate: Date
): MoodStats {
    val totalLogs = points.size
    if (totalLogs == 0) {
        return MoodStats(
            averageScore = 0f,
            averageMoodLabel = "暂无",
            dominantMood = null,
            totalLogs = 0,
            daysWithLogs = 0,
            activePeriods = 0,
            positiveRate = 0f,
            stabilityLabel = "暂无",
            minScore = 0f,
            maxScore = 0f,
            moodCounts = emptyMap(),
            dailySummaries = emptyList(),
            monthlySummaries = emptyList()
        )
    }

    val scores = points.map { moodScore(it.mood) }
    val averageScore = scores.average().toFloat()
    val moodCounts = points.groupingBy { it.mood.uppercase(Locale.ROOT) }.eachCount()
    val dominantMood = moodCounts.maxByOrNull { it.value }?.key
    val positiveRate = ((moodCounts["HAPPY"] ?: 0) / totalLogs.toFloat()) * 100f

    val dailySummaries = points
        .filter { it.date.isNotBlank() }
        .groupBy { it.date }
        .toSortedMap()
        .map { (date, items) ->
            val avg = items.map { moodScore(it.mood) }.average().toFloat()
            val dominant = items.groupingBy { it.mood.uppercase(Locale.ROOT) }.eachCount().maxByOrNull { it.value }?.key
            val parsedDate = parseDate(date)
            DailyMoodSummary(
                date = date,
                shortLabel = when (range) {
                    TimeRange.WEEK -> weekdayLabel(parsedDate)
                    TimeRange.MONTH -> "${parsedDate?.let { dayOfMonth(it) } ?: 0}"
                    TimeRange.YEAR -> SimpleDateFormat("M/d", Locale.CHINA).format(parsedDate ?: selectedDate)
                    TimeRange.DAY -> SimpleDateFormat("HH:mm", Locale.CHINA).format(parsedDate ?: selectedDate)
                },
                averageScore = avg,
                dominantMood = dominant,
                recordCount = items.size,
                dayOfMonth = parsedDate?.let { dayOfMonth(it) } ?: 0,
                color = getMoodColor(dominant ?: "NEUTRAL")
            )
        }

    val monthlySummaries = points
        .filter { it.date.length >= 7 }
        .groupBy { it.date.substring(0, 7) }
        .toSortedMap()
        .map { (month, items) ->
            val avg = items.map { moodScore(it.mood) }.average().toFloat()
            val dominant = items.groupingBy { it.mood.uppercase(Locale.ROOT) }.eachCount().maxByOrNull { it.value }?.key
            MonthlyMoodSummary(
                label = month.substring(5).replace("-", "月") + "月",
                averageScore = avg,
                dominantMood = dominant,
                recordCount = items.size,
                color = getMoodColor(dominant ?: "NEUTRAL")
            )
        }

    return MoodStats(
        averageScore = averageScore,
        averageMoodLabel = scoreToMoodLabel(averageScore),
        dominantMood = dominantMood,
        totalLogs = totalLogs,
        daysWithLogs = dailySummaries.size,
        activePeriods = if (range == TimeRange.YEAR) monthlySummaries.size else dailySummaries.size,
        positiveRate = positiveRate,
        stabilityLabel = stabilityLabel(scores.minOrNull() ?: 0f, scores.maxOrNull() ?: 0f),
        minScore = scores.minOrNull() ?: 0f,
        maxScore = scores.maxOrNull() ?: 0f,
        moodCounts = moodCounts,
        dailySummaries = dailySummaries,
        monthlySummaries = monthlySummaries
    )
}

private fun buildDayInsight(points: List<MoodTimePoint>): String {
    if (points.isEmpty()) return "今天还没有记录到可分析的情绪数据。"

    val sorted = points.sortedBy { it.timestamp }
    val dominantMood = sorted.groupingBy { it.mood.uppercase(Locale.ROOT) }.eachCount().maxByOrNull { it.value }?.key ?: "NEUTRAL"
    val earliest = sorted.first().time
    val latest = sorted.last().time
    val withNotes = sorted.filter { it.note.isNotBlank() }
    val noteSummary = withNotes.take(2).joinToString("；") { it.note.take(24) }

    return buildString {
        append("今天主要呈现“")
        append(getMoodDisplayText(dominantMood))
        append("”情绪，记录时间覆盖 ")
        append(earliest)
        append(" 到 ")
        append(latest)
        append("。")
        if (withNotes.isNotEmpty()) {
            append(" 备注里反复提到：")
            append(noteSummary)
            append("。")
        }
    }
}

private fun buildShareText(
    range: TimeRange,
    selectedDate: Date,
    stats: MoodStats,
    analysis: String?
): String {
    if (stats.totalLogs == 0) return ""
    val header = buildRangeLabel(range, selectedDate)
    return buildString {
        append(header).append("情绪分析").append('\n')
        append("平均情绪：").append(stats.averageMoodLabel).append("（").append(stats.averageScore.format1()).append("/5）").append('\n')
        append("主导情绪：").append(stats.dominantMood?.let(::getMoodDisplayText) ?: "暂无").append('\n')
        append("记录次数：").append(stats.totalLogs).append('\n')
        append("积极情绪占比：").append(stats.positiveRate.roundToInt()).append("%").append('\n')
        if (!analysis.isNullOrBlank()) {
            append('\n')
            append(analysis.take(160))
        }
    }
}

private fun buildRangeLabel(range: TimeRange, selectedDate: Date): String {
    return when (range) {
        TimeRange.DAY -> SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(selectedDate)
        TimeRange.WEEK -> {
            val calendar = Calendar.getInstance().apply { time = selectedDate }
            while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }
            val start = calendar.time
            calendar.add(Calendar.DAY_OF_YEAR, 6)
            val end = calendar.time
            "${SimpleDateFormat("M月d日", Locale.CHINA).format(start)} - ${SimpleDateFormat("M月d日", Locale.CHINA).format(end)}"
        }
        TimeRange.MONTH -> SimpleDateFormat("yyyy年M月", Locale.CHINA).format(selectedDate)
        TimeRange.YEAR -> SimpleDateFormat("yyyy年", Locale.CHINA).format(selectedDate)
    }
}

private fun moodScore(mood: String): Float {
    return when (mood.uppercase(Locale.ROOT)) {
        "HAPPY" -> 5f
        "NEUTRAL" -> 3f
        "ANXIOUS" -> 2f
        "SAD" -> 2f
        "ANGRY" -> 1f
        else -> 3f
    }
}

private fun scoreToMoodLabel(score: Float): String {
    return when {
        score >= 4.5f -> "非常愉悦"
        score >= 3.8f -> "较为积极"
        score >= 2.8f -> "比较平稳"
        score >= 2.0f -> "略有波动"
        else -> "需要关注"
    }
}

private fun stabilityLabel(min: Float, max: Float): String {
    val delta = max - min
    return when {
        delta <= 1f -> "很稳定"
        delta <= 2f -> "有波动"
        else -> "波动较大"
    }
}

private fun periodToRange(period: String?): TimeRange {
    return when (period) {
        "week" -> TimeRange.WEEK
        "month" -> TimeRange.MONTH
        "year" -> TimeRange.YEAR
        else -> TimeRange.DAY
    }
}

private fun weekdayLabel(date: Date?): String {
    if (date == null) return "--"
    return when (Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> "周日"
    }
}

private fun parseDate(value: String): Date? {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)
    }.getOrNull()
}

private fun dayOfMonth(date: Date): Int {
    return Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_MONTH)
}

private fun Float.format1(): String = String.format(Locale.US, "%.1f", this)
