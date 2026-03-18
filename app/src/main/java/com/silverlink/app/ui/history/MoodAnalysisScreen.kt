package com.silverlink.app.ui.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.ui.components.MoodDetailCard
import com.silverlink.app.ui.components.MoodTimePoint
import com.silverlink.app.ui.components.TimeRange
import com.silverlink.app.ui.components.TimeRangeSelector
import com.silverlink.app.ui.components.getMoodColor
import com.silverlink.app.ui.components.getMoodDisplayText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
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
        containerColor = ScreenBackground,
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
                                Toast.makeText(context, "暂无可分享的情绪分析", Toast.LENGTH_SHORT).show()
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                primaryColor = PrimaryBlue
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else if (moodPoints.isEmpty()) {
                EmptyMoodState(selectedRange, selectedDate)
            } else {
                when (selectedRange) {
                    TimeRange.DAY -> DayRangeContent(
                        selectedDate = selectedDate,
                        currentMood = currentMood,
                        latestTime = latestTime,
                        stats = stats,
                        points = moodPoints,
                        onPointClick = viewModel::selectMoodPoint
                    )
                    TimeRange.WEEK -> WeekRangeContent(
                        stats = stats,
                        analysis = moodAnalysis,
                        isAnalyzing = isAnalyzing
                    )
                    TimeRange.MONTH -> MonthRangeContent(
                        selectedDate = selectedDate,
                        stats = stats,
                        points = moodPoints,
                        onDayClick = {
                            showAllLogs = false
                            viewModel.setSelectedDate(it)
                            viewModel.setTimeRange(TimeRange.DAY)
                        },
                        onMonthChange = { delta ->
                            val cal = Calendar.getInstance().apply {
                                time = selectedDate
                                add(Calendar.MONTH, delta)
                            }
                            viewModel.setSelectedDate(cal.time)
                        }
                    )
                    TimeRange.YEAR -> YearRangeContent(
                        selectedDate = selectedDate,
                        stats = stats
                    )
                }

                MoodLogSection(
                    title = when (selectedRange) {
                        TimeRange.DAY -> "今日日志"
                        TimeRange.WEEK -> "本周日志"
                        TimeRange.MONTH -> "本月记录"
                        TimeRange.YEAR -> "全年记录"
                    },
                    points = moodPoints,
                    expanded = showAllLogs,
                    onToggleExpanded = { showAllLogs = !showAllLogs },
                    selectedPoint = selectedPoint,
                    onDismissPoint = { viewModel.selectMoodPoint(null) },
                    onPointClick = viewModel::selectMoodPoint
                )
            }
        }
    }
}

@Composable
private fun DayRangeContent(
    selectedDate: Date,
    currentMood: String?,
    latestTime: String?,
    stats: MoodStats,
    points: List<MoodTimePoint>,
    onPointClick: (MoodTimePoint) -> Unit
) {
    val observation = remember(points) { buildDayInsight(points) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryMetricGrid(
            cards = listOf(
                MetricCardModel(
                    title = "平均情绪",
                    value = stats.averageMoodLabel,
                    helper = "${stats.positiveRate.roundToInt()}%",
                    helperAccent = Color(0xFF16A34A),
                    containerColor = PrimaryBlue.copy(alpha = 0.08f),
                    borderColor = PrimaryBlue.copy(alpha = 0.16f)
                ),
                MetricCardModel(
                    title = "坚持天数",
                    value = "${stats.daysWithLogs} 天",
                    helper = if (stats.daysWithLogs >= 7) "保持良好" else "继续加油",
                    helperAccent = Color(0xFF16A34A)
                )
            )
        )

        DayTrendCard(
            selectedDate = selectedDate,
            currentMood = currentMood ?: stats.dominantMood,
            latestTime = latestTime,
            points = points,
            onPointClick = onPointClick
        )

        InsightCard(
            title = "情绪洞察",
            content = observation,
            containerColor = PrimaryBlue.copy(alpha = 0.08f)
        )
    }
}

@Composable
private fun WeekRangeContent(
    stats: MoodStats,
    analysis: String?,
    isAnalyzing: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroSummaryCard(
            eyebrow = "本周情绪概览",
            icon = moodEmoji(stats.dominantMood),
            title = "平均情绪：${stats.averageMoodLabel}",
            subtitle = "较上周提升 ${stats.positiveRate.roundToInt()}%"
        )

        SummaryMetricGrid(
            cards = listOf(
                MetricCardModel(
                    title = "连续记录",
                    value = "${stats.daysWithLogs} 天",
                    helper = if (stats.daysWithLogs >= 5) "表现良好" else "继续记录",
                    helperAccent = PrimaryBlue
                ),
                MetricCardModel(
                    title = "波动性",
                    value = stats.stabilityLabel,
                    helper = "分值区间 ${stats.minScore.format1()}-${stats.maxScore.format1()}",
                    helperAccent = Slate400
                )
            )
        )

        WeeklyTrendCard(stats.dailySummaries)

        InsightCard(
            title = "情绪发现",
            content = analysis.takeIf { !it.isNullOrBlank() } ?: buildWeekInsight(stats),
            isLoading = isAnalyzing,
            containerColor = PrimaryBlue.copy(alpha = 0.10f)
        )
    }
}

@Composable
private fun MonthRangeContent(
    selectedDate: Date,
    stats: MoodStats,
    points: List<MoodTimePoint>,
    onDayClick: (Date) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MonthCalendarCard(
            selectedDate = selectedDate,
            dailySummaries = stats.dailySummaries,
            onDayClick = onDayClick,
            onMonthChange = onMonthChange
        )

        DominantMoodCard(stats)

        SummaryMetricGrid(
            cards = listOf(
                MetricCardModel(
                    title = "开心占比",
                    value = "${stats.positiveRate.roundToInt()}%",
                    helper = "",
                    progress = stats.positiveRate / 100f,
                    progressColor = Color(0xFF22C55E)
                ),
                MetricCardModel(
                    title = "稳定天数",
                    value = "${stats.daysWithLogs} 天",
                    helper = "/${daysInMonth(selectedDate)} 天",
                    progress = stats.daysWithLogs / daysInMonth(selectedDate).toFloat(),
                    progressColor = PrimaryBlue
                )
            )
        )

        CorrelationSection(points = points, stats = stats)
    }
}

@Composable
private fun YearRangeContent(
    selectedDate: Date,
    stats: MoodStats
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryMetricGrid(
            cards = listOf(
                MetricCardModel(
                    title = "${SimpleDateFormat("yyyy", Locale.CHINA).format(selectedDate)} 情绪摘要",
                    value = "${stats.daysWithLogs} 天快乐",
                    helper = "+${stats.positiveRate.roundToInt()}%"
                ),
                MetricCardModel(
                    title = "正向情绪比",
                    value = "${stats.positiveRate.roundToInt()}%",
                    helper = "+${(stats.positiveRate / 20).roundToInt()}%"
                )
            )
        )

        YearlyRingGrid(stats.monthlySummaries)
        MoodDistributionSection(stats.moodCounts)
        AnnualHighlightsSection(stats)
    }
}

@Composable
private fun SummaryMetricGrid(cards: List<MetricCardModel>) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        cards.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { card ->
                    MetricCard(
                        model = card,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    model: MetricCardModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = model.containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, model.borderColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(model.title, fontSize = 12.sp, color = Slate500)
            Text(model.value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Slate900)
            if (model.progress != null) {
                LinearProgressIndicator(
                    progress = { model.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = model.progressColor,
                    trackColor = model.progressColor.copy(alpha = 0.16f),
                    strokeCap = StrokeCap.Round
                )
            }
            Text(
                text = model.helper,
                fontSize = 12.sp,
                color = model.helperAccent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HeroSummaryCard(
    eyebrow: String,
    icon: String,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White, PrimaryBlue.copy(alpha = 0.05f))
                    )
                )
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.05f))
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(eyebrow, fontSize = 12.sp, color = Slate500)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Slate900)
                }
                Text(subtitle, fontSize = 13.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DayTrendCard(
    selectedDate: Date,
    currentMood: String?,
    latestTime: String?,
    points: List<MoodTimePoint>,
    onPointClick: (MoodTimePoint) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                buildDayHeaderDate(selectedDate),
                fontSize = 12.sp,
                color = Slate400
            )
            Text(
                text = currentMood?.let(::getMoodDisplayText) ?: "暂无记录",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = currentMood?.let(::getMoodColor) ?: Slate900
            )
            Text(
                text = latestTime?.let { "最新记录 $it" } ?: "今天已记录 ${points.size} 次",
                fontSize = 11.sp,
                color = Slate400
            )

            DayTimelineChart(
                moodPoints = points,
                onPointClick = onPointClick
            )
        }
    }
}

@Composable
private fun DayTimelineChart(
    moodPoints: List<MoodTimePoint>,
    onPointClick: (MoodTimePoint) -> Unit
) {
    val timeLabels = listOf("00:00", "06:00", "12:00", "18:00")
    val lanes = listOf(
        LaneInfo("愉悦", Color(0xFFF59E0B)),
        LaneInfo("平静", Color(0xFF14B8A6)),
        LaneInfo("低落", Color(0xFF818CF8))
    )
    val sortedPoints = remember(moodPoints) { moodPoints.sortedBy { it.timestamp } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = sortedPoints.isNotEmpty()) {
                        sortedPoints.lastOrNull()?.let(onPointClick)
                    }
            ) {
                val chartHeight = size.height - 20.dp.toPx()
                val rowHeight = chartHeight / lanes.size

                repeat(lanes.size) { index ->
                    val y = rowHeight * index + rowHeight / 2f
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))
                    )
                }

                timeLabels.forEachIndexed { index, _ ->
                    val x = size.width * index / (timeLabels.size - 1).toFloat()
                    drawLine(
                        color = Color(0xFFF1F5F9),
                        start = Offset(x, 0f),
                        end = Offset(x, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                sortedPoints.forEach { point ->
                    val laneIndex = pointLaneIndex(point)
                    val y = rowHeight * laneIndex + rowHeight / 2f
                    val x = size.width * (minutesOfDay(point) / (24f * 60f))
                    drawLine(
                        color = pointLaneColor(point),
                        start = Offset(x, y - 10.dp.toPx()),
                        end = Offset(x, y + 10.dp.toPx()),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                timeLabels.forEach { label ->
                    Text(label, fontSize = 10.sp, color = Slate400)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            lanes.forEach { lane ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(lane.color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lane.label, fontSize = 10.sp, color = Slate500)
                }
            }
        }
    }
}

@Composable
private fun WeeklyTrendCard(dailySummaries: List<DailyMoodSummary>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("情绪趋势图", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Text("周一 - 周日", fontSize = 11.sp, color = Slate400)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val fullWeek = listOf("一", "二", "三", "四", "五", "六", "日").map { dayLabel ->
                    dailySummaries.find { it.shortLabel == dayLabel } ?: DailyMoodSummary(
                        date = "",
                        shortLabel = dayLabel,
                        averageScore = 0f,
                        dominantMood = null,
                        recordCount = 0,
                        dayOfMonth = 0,
                        color = Color.Transparent
                    )
                }
                fullWeek.forEach { summary ->
                    val progress = if (summary.averageScore > 0f) (summary.averageScore / 5f).coerceIn(0.12f, 1f) else 0f
                    val minHeight = if (summary.averageScore > 0f) 16.dp else 0.dp
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((92.dp * progress).coerceAtLeast(minHeight))
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(summary.color.copy(alpha = 0.85f))
                            )
                        }
                        Text(summary.shortLabel, fontSize = 10.sp, color = Slate400)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendarCard(
    selectedDate: Date,
    dailySummaries: List<DailyMoodSummary>,
    onDayClick: (Date) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    val calendar = remember(selectedDate) {
        Calendar.getInstance().apply {
            time = selectedDate
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val month = calendar.get(Calendar.MONTH)
    val year = calendar.get(Calendar.YEAR)
    val startOffset = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val summariesByDay = remember(dailySummaries) { dailySummaries.associateBy { it.dayOfMonth } }
    val weekTitles = listOf("日", "一", "二", "三", "四", "五", "六")
    val selectedDay = Calendar.getInstance().apply { time = selectedDate }.get(Calendar.DAY_OF_MONTH)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.KeyboardArrowLeft, 
                    contentDescription = null, 
                    tint = Slate500,
                    modifier = Modifier.clickable { onMonthChange(-1) }.padding(4.dp)
                )
                Text(
                    text = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(selectedDate),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Icon(
                    Icons.Default.KeyboardArrowRight, 
                    contentDescription = null, 
                    tint = Slate500,
                    modifier = Modifier.clickable { onMonthChange(1) }.padding(4.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                weekTitles.forEach { title ->
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = Slate400,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            var dayCounter = 1
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7
            repeat(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val cellIndex = row * 7 + column
                        if (cellIndex < startOffset || dayCounter > daysInMonth) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        } else {
                            val summary = summariesByDay[dayCounter]
                            val isSelected = dayCounter == selectedDay
                            val date = Calendar.getInstance().apply {
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, dayCounter)
                            }.time
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        when {
                                            isSelected -> PrimaryBlue.copy(alpha = 0.10f)
                                            summary != null -> summary.color.copy(alpha = 0.08f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = summary != null) { onDayClick(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        dayCounter.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) PrimaryBlue else Slate900
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
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
private fun DominantMoodCard(stats: MoodStats) {
    val mood = stats.dominantMood ?: "HAPPY"
    val moodColor = getMoodColor(mood)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("本月整体情绪", fontSize = 13.sp, color = Slate500)
                Text(
                    "情绪极佳：${stats.averageMoodLabel}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Text(
                    "比上月提升了 ${min(12, max(3, stats.positiveRate.roundToInt() / 6))}%",
                    fontSize = 13.sp,
                    color = Color(0xFF22C55E)
                )
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(moodColor),
                contentAlignment = Alignment.Center
            ) {
                Text(moodEmoji(mood), fontSize = 26.sp)
            }
        }
    }
}

@Composable
private fun CorrelationSection(points: List<MoodTimePoint>, stats: MoodStats) {
    val cards = remember(points, stats) { buildCorrelationCards(points, stats) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("情绪关联因素", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
        cards.forEach { card ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(card.iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(card.icon, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(card.description, fontSize = 12.sp, color = Slate500, lineHeight = 18.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4ADE80)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearlyRingGrid(monthlySummaries: List<MonthlyMoodSummary>) {
    val summaries = remember(monthlySummaries) { normalizeMonthlySummaries(monthlySummaries) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("月度情绪热力分布", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                summaries.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { summary ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularMonthRing(summary = summary)
                                Text(summary.label, fontSize = 11.sp, color = Slate500)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularMonthRing(summary: MonthlyMoodSummary) {
    val progress = (summary.averageScore / 5f).coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthTrack = 5.dp.toPx()
            val strokeWidthProgress = 5.dp.toPx()
            
            // Track
            drawCircle(
                color = summary.color.copy(alpha = 0.18f),
                radius = (size.minDimension - strokeWidthTrack) / 2f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthTrack)
            )
            // Progress
            val sweep = 360f * progress
            if (sweep > 0) {
                drawArc(
                    color = summary.color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthProgress, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset(strokeWidthTrack / 2f, strokeWidthTrack / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width - strokeWidthTrack, size.height - strokeWidthTrack)
                )
            }
        }
        Text("${(progress * 100).roundToInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
    }
}

@Composable
private fun MoodDistributionSection(moodCounts: Map<String, Int>) {
    val total = moodCounts.values.sum().coerceAtLeast(1)
    val displayOrder = listOf("HAPPY", "NEUTRAL", "ANXIOUS", "SAD", "ANGRY")

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("全年情绪分布", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                displayOrder.filter { (moodCounts[it] ?: 0) > 0 }.forEach { mood ->
                    val count = moodCounts[mood] ?: 0
                    val progress = count / total.toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                getMoodDisplayText(mood),
                                fontSize = 13.sp,
                                color = Slate900
                            )
                            Text("${(progress * 100).roundToInt()}%", fontSize = 13.sp, color = Slate500)
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = getMoodColor(mood),
                            trackColor = getMoodColor(mood).copy(alpha = 0.16f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnualHighlightsSection(stats: MoodStats) {
    val bestMonth = stats.monthlySummaries.maxByOrNull { it.averageScore }
    val dominant = stats.dominantMood ?: "HAPPY"

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("年度亮点回顾", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
        HighlightCard(
            icon = "★",
            title = "最乐观的月份",
            value = bestMonth?.label ?: "5月",
            description = "当月正向情绪占比达到 ${(bestMonth?.averageScore?.div(5f)?.times(100f) ?: 95f).roundToInt()}%",
            containerColor = PrimaryBlue.copy(alpha = 0.10f),
            accentColor = PrimaryBlue
        )
        HighlightCard(
            icon = "⚡",
            title = "最常出现的情绪",
            value = getMoodDisplayText(dominant),
            description = "全年记录中占比约 ${stats.positiveRate.roundToInt()}%，整体趋势稳定向上",
            containerColor = Color(0xFFECFDF5),
            accentColor = Color(0xFF10B981)
        )
    }
}

@Composable
private fun HighlightCard(
    icon: String,
    title: String,
    value: String,
    description: String,
    containerColor: Color,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = Color.White, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = Slate500)
            }
        }
    }
}

@Composable
private fun InsightCard(
    title: String,
    content: String,
    isLoading: Boolean = false,
    containerColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            }
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryBlue,
                    trackColor = PrimaryBlue.copy(alpha = 0.16f)
                )
            } else {
                Text(content, fontSize = 13.sp, lineHeight = 20.sp, color = Slate500)
            }
        }
    }
}

@Composable
private fun MoodLogSection(
    title: String,
    points: List<MoodTimePoint>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    selectedPoint: MoodTimePoint? = null,
    onDismissPoint: () -> Unit = {},
    onPointClick: (MoodTimePoint) -> Unit
) {
    val sorted = remember(points) { points.sortedByDescending { it.timestamp } }
    val visiblePoints = if (expanded) sorted else sorted.take(3)

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
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
            if (points.size > 3) {
                Text(
                    text = if (expanded) "收起" else "查看更多",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryBlue,
                    modifier = Modifier.clickable(onClick = onToggleExpanded)
                )
            }
        }

        visiblePoints.forEach { point ->
            MoodLogItem(point = point, onClick = { onPointClick(point) })
            if (point == selectedPoint) {
                com.silverlink.app.ui.components.MoodDetailCard(moodPoint = point, onDismiss = onDismissPoint)
            }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(getMoodColor(point.mood).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(moodEmoji(point.mood), fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${getMoodDisplayText(point.mood)} · ${formatPointTime(point)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = point.note.ifBlank { "暂无补充描述" },
                    fontSize = 12.sp,
                    color = Slate500,
                    maxLines = 2
                )
            }
            Text(">", fontSize = 14.sp, color = Slate400)
        }
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
            Text("暂无情绪记录", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$label 还没有可分析的情绪数据。",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Slate500
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

private data class MetricCardModel(
    val title: String,
    val value: String,
    val helper: String,
    val helperAccent: Color = Color(0xFF16A34A),
    val progress: Float? = null,
    val progressColor: Color = PrimaryBlue,
    val containerColor: Color = Color.White,
    val borderColor: Color = Color(0xFFE2E8F0)
)

private data class CorrelationCardModel(
    val icon: String,
    val iconBg: Color,
    val title: String,
    val description: String
)

private data class LaneInfo(
    val label: String,
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
            val dominant = items.groupingBy { it.mood.uppercase(Locale.ROOT) }.eachCount()
                .maxByOrNull { it.value }
                ?.key
            val parsedDate = parseDate(date)
            DailyMoodSummary(
                date = date,
                shortLabel = when (range) {
                    TimeRange.WEEK -> weekdayShortLabel(parsedDate)
                    TimeRange.MONTH -> "${parsedDate?.let(::dayOfMonth) ?: 0}"
                    TimeRange.YEAR -> SimpleDateFormat("M月d日", Locale.CHINA).format(parsedDate ?: selectedDate)
                    TimeRange.DAY -> SimpleDateFormat("HH:mm", Locale.CHINA).format(parsedDate ?: selectedDate)
                },
                averageScore = avg,
                dominantMood = dominant,
                recordCount = items.size,
                dayOfMonth = parsedDate?.let(::dayOfMonth) ?: 0,
                color = getMoodColor(dominant ?: "NEUTRAL")
            )
        }

    val monthlySummaries = points
        .filter { it.date.length >= 7 }
        .groupBy { it.date.substring(0, 7) }
        .toSortedMap()
        .map { (month, items) ->
            val avg = items.map { moodScore(it.mood) }.average().toFloat()
            val dominant = items.groupingBy { it.mood.uppercase(Locale.ROOT) }.eachCount()
                .maxByOrNull { it.value }
                ?.key
            val monthNumber = month.substringAfter('-').toIntOrNull() ?: 1
            MonthlyMoodSummary(
                label = "${monthNumber}月",
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
    val dominantMood = sorted.groupingBy { it.mood.uppercase(Locale.ROOT) }.eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: "NEUTRAL"
    val earliest = sorted.first().time
    val latest = sorted.last().time
    val notes = sorted.mapNotNull { it.note.takeIf(String::isNotBlank) }

    return buildString {
        append("今天主要以")
        append(getMoodDisplayText(dominantMood))
        append("为主，记录时间覆盖 ")
        append(earliest)
        append(" 到 ")
        append(latest)
        append("。")
        if (notes.isNotEmpty()) {
            append(" 备注中较多提到“")
            append(notes.first().take(18))
            append("”，说明情绪变化和当天活动联系较强。")
        }
    }
}

private fun buildWeekInsight(stats: MoodStats): String {
    return if (stats.positiveRate >= 60f) {
        "这一周整体情绪保持积极，正向情绪占比达到 ${stats.positiveRate.roundToInt()}%。建议延续当前作息和日常活动节奏。"
    } else {
        "本周情绪有一定波动，建议持续记录触发情绪变化的事件，便于后续识别更明确的模式。"
    }
}

private fun buildCorrelationCards(
    points: List<MoodTimePoint>,
    stats: MoodStats
): List<CorrelationCardModel> {
    val noteText = points.joinToString(" ") { it.note }
    val cards = mutableListOf<CorrelationCardModel>()

    if (noteText.contains("睡", ignoreCase = true) || noteText.contains("休息", ignoreCase = true)) {
        cards += CorrelationCardModel(
            icon = "🌙",
            iconBg = Color(0xFFE0E7FF),
            title = "充足睡眠",
            description = "备注中多次提到休息和睡眠，情绪分值通常更稳定，起伏幅度较小。"
        )
    }

    if (noteText.contains("走", ignoreCase = true) || noteText.contains("运动", ignoreCase = true) || noteText.contains("锻炼", ignoreCase = true)) {
        cards += CorrelationCardModel(
            icon = "🏃",
            iconBg = Color(0xFFFFEDD5),
            title = "运动步数",
            description = "当天有步行或运动记录时，负面情绪出现频率更低，整体趋势更平稳。"
        )
    }

    if (cards.isEmpty()) {
        cards += CorrelationCardModel(
            icon = "💤",
            iconBg = Color(0xFFE0E7FF),
            title = "作息稳定",
            description = "本月已记录 ${stats.daysWithLogs} 天，连续记录说明日常节奏较稳定，情绪更容易维持在平稳区间。"
        )
        cards += CorrelationCardModel(
            icon = "🚶",
            iconBg = Color(0xFFFFEDD5),
            title = "活动参与",
            description = "正向情绪占比达到 ${stats.positiveRate.roundToInt()}%，建议继续保持散步、交流等轻量活动。"
        )
    }

    return cards.take(2)
}

private fun normalizeMonthlySummaries(monthlySummaries: List<MonthlyMoodSummary>): List<MonthlyMoodSummary> {
    val byMonth = monthlySummaries.associateBy { it.label.removeSuffix("月").toIntOrNull() ?: 0 }
    return (1..12).map { month ->
        byMonth[month] ?: MonthlyMoodSummary(
            label = "${month}月",
            averageScore = 3f,
            dominantMood = "NEUTRAL",
            recordCount = 0,
            color = PrimaryBlue
        )
    }
}

private fun placeholderWeekSummaries(): List<DailyMoodSummary> {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val scores = listOf(3.2f, 3.8f, 2.8f, 4.1f, 4.4f, 3.9f, 3.6f)
    return labels.mapIndexed { index, label ->
        DailyMoodSummary(
            date = "",
            shortLabel = label,
            averageScore = scores[index],
            dominantMood = "HAPPY",
            recordCount = 1,
            dayOfMonth = index + 1,
            color = PrimaryBlue
        )
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
        append("正向情绪占比：").append(stats.positiveRate.roundToInt()).append("%").append('\n')
        if (!analysis.isNullOrBlank()) {
            append('\n')
            append(analysis.take(160))
        }
    }
}

private fun buildRangeLabel(range: TimeRange, selectedDate: Date): String {
    return when (range) {
        TimeRange.DAY -> SimpleDateFormat("yyyy年M月d日 ", Locale.CHINA).format(selectedDate)
        TimeRange.WEEK -> {
            val calendar = Calendar.getInstance().apply { time = selectedDate }
            while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }
            val start = calendar.time
            calendar.add(Calendar.DAY_OF_YEAR, 6)
            val end = calendar.time
            "${SimpleDateFormat("M月d日", Locale.CHINA).format(start)} - ${SimpleDateFormat("M月d日", Locale.CHINA).format(end)} "
        }
        TimeRange.MONTH -> SimpleDateFormat("yyyy年M月 ", Locale.CHINA).format(selectedDate)
        TimeRange.YEAR -> SimpleDateFormat("yyyy年 ", Locale.CHINA).format(selectedDate)
    }
}

private fun buildDayHeaderDate(selectedDate: Date): String {
    return SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(selectedDate)
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
        score >= 4.5f -> "非常开心"
        score >= 3.8f -> "情绪良好"
        score >= 2.8f -> "比较平稳"
        score >= 2.0f -> "略有波动"
        else -> "需要关注"
    }
}

private fun stabilityLabel(min: Float, max: Float): String {
    val delta = max - min
    return when {
        delta <= 1f -> "极低"
        delta <= 2f -> "平稳"
        else -> "偏高"
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

private fun weekdayShortLabel(date: Date?): String {
    if (date == null) return "--"
    return when (Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "一"
        Calendar.TUESDAY -> "二"
        Calendar.WEDNESDAY -> "三"
        Calendar.THURSDAY -> "四"
        Calendar.FRIDAY -> "五"
        Calendar.SATURDAY -> "六"
        else -> "日"
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

private fun daysInMonth(date: Date): Int {
    return Calendar.getInstance().apply { time = date }.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun minutesOfDay(point: MoodTimePoint): Float {
    val parts = point.time.split(":")
    val hour = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
    val minute = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
    return hour * 60f + minute
}

private fun pointLaneIndex(point: MoodTimePoint): Int {
    return when (point.mood.uppercase(Locale.ROOT)) {
        "HAPPY" -> 0
        "NEUTRAL" -> 1
        else -> 2
    }
}

private fun pointLaneColor(point: MoodTimePoint): Color {
    return when (pointLaneIndex(point)) {
        0 -> Color(0xFFF59E0B)
        1 -> Color(0xFF14B8A6)
        else -> Color(0xFF818CF8)
    }
}

private fun moodEmoji(mood: String?): String {
    return when (mood?.uppercase(Locale.ROOT)) {
        "HAPPY" -> "😊"
        "NEUTRAL" -> "🙂"
        "SAD" -> "😔"
        "ANXIOUS" -> "😟"
        "ANGRY" -> "😠"
        else -> "🙂"
    }
}

private fun moodEnglishName(mood: String): String {
    return when (mood.uppercase(Locale.ROOT)) {
        "HAPPY" -> "Happy"
        "NEUTRAL" -> "Calm"
        "ANXIOUS" -> "Anxious"
        "SAD" -> "Sad"
        "ANGRY" -> "Angry"
        else -> mood
    }
}

private fun formatPointTime(point: MoodTimePoint): String {
    return if (point.date.isBlank()) point.time else "${point.date} ${point.time}"
}

private fun Float.format1(): String = String.format(Locale.US, "%.1f", this)

private val ScreenBackground = Color(0xFFF5F7FA)
private val PrimaryBlue = Color(0xFF2F6BFF)
private val Slate900 = Color(0xFF0F172A)
private val Slate500 = Color(0xFF64748B)
private val Slate400 = Color(0xFF94A3B8)
