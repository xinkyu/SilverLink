package com.silverlink.app.ui.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.feature.health.HealthTrendPoint
import com.silverlink.app.feature.health.OppoHealthSdkManager
import com.silverlink.app.ui.components.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StressDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val dashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(TimeRange.DAY) }

    val stressData by produceState(
        initialValue = StressUiData.empty(dashboardData?.latestPressure ?: 0),
        key1 = selectedRange,
        key2 = dashboardData
    ) {
        value = loadStressUiData(
            context = context,
            selectedRange = selectedRange,
            fallbackCurrent = dashboardData?.latestPressure ?: 0,
            fallbackDayDetail = dashboardData?.pressureTimeline.orEmpty(),
            fallbackRecentSummary = dashboardData?.pressureDailySummary.orEmpty()
        )
    }

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = { Text("压力分析", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val summary = buildStressShareText(selectedRange, stressData)
                            if (summary.isBlank()) {
                                Toast.makeText(context, "暂无可分享的压力数据", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, summary)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享压力数据"))
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RangeTabs(
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRange = it }
                )
            }
            item {
                when (selectedRange) {
                    TimeRange.DAY -> DaySection(stressData)
                    TimeRange.WEEK -> WeekSection(stressData)
                    TimeRange.MONTH -> MonthSection(stressData)
                    TimeRange.YEAR -> YearSection(stressData)
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
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFF1F5F9), modifier = Modifier.fillMaxWidth()) {
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
private fun DaySection(data: StressUiData) {
    val current = data.current
    val progress = (current / 100f).coerceIn(0f, 1f)
    val level = stressLevel(current)
    val buckets = bucketIntraday(data.detail)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前压力等级", color = Color(0xFF64748B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 24.dp))

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(192.dp)) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF007bff),
                        strokeWidth = 12.dp,
                        trackColor = Color(0xFFF1F5F9),
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (current > 0) current.toString() else "--", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007bff))
                        Text("/ 100", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(color = level.background, shape = RoundedCornerShape(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = level.color, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(level.label, color = level.color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(level.description, color = Color(0xFF64748B), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("日间分布", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("真实数据", color = Color(0xFF007bff), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(128.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    buckets.forEach { bucket ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .fillMaxSize(bucket.value / 100f)
                                    .background(bucket.color.copy(alpha = bucket.alpha), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(bucket.label, fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("快速放松练习", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(top = 8.dp))

            ExerciseCard("盒式呼吸", "4 分钟", Icons.Default.SelfImprovement, Color(0xFF007bff), Color(0xFF007bff).copy(alpha = 0.1f))
            ExerciseCard("全身扫描", "10 分钟", Icons.Default.SelfImprovement, Color(0xFF4F46E5), Color(0xFF4F46E5).copy(alpha = 0.1f))

            Surface(color = Color(0xFF007bff).copy(alpha = 0.05f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007bff).copy(alpha = 0.1f))) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFF007bff))
                    Column {
                        Text("小贴士", color = Color(0xFF007bff), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("页面已经改成读取真实压力数据。当前建议以最近一次压力值和当天波动为参考，避免只看单点数值。", color = Color(0xFF334155), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSection(data: StressUiData) {
    val weekPoints = data.summary.takeLast(7)
    val average = averageValue(weekPoints)
    val highDays = weekPoints.count { it.value >= 60 }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本周平均", average.toString(), " /${stressLevel(average).shortLabel}", Icons.Default.TrendingDown, stressLevel(average).color)
            StatCard(Modifier.weight(1f), "高压力天数", highDays.toString(), "天", Icons.Default.Warning, Color(0xFFEF4444))
        }

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("周度压力趋势", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 16.dp))
                WeeklyStressBars(weekPoints)
            }
        }

        Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("建议反馈", fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(4.dp))
                Text("本周真实平均压力为 $average，达到高压区间的天数为 $highDays。建议优先关注压力峰值较高的日期，而不是只看单日波动。", color = Color(0xFF475569), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MonthSection(data: StressUiData) {
    val monthPoints = currentMonthStressPoints(data.summary)
    val average = averageValue(monthPoints)
    val relaxHours = (monthPoints.count { it.value < 40 } * 0.5f)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本月平均", average.toString(), " /${stressLevel(average).shortLabel}", Icons.Default.TrendingDown, stressLevel(average).color)
            StatCard(Modifier.weight(1f), "放松时间", String.format("%.1f", relaxHours), "h", Icons.Default.SelfImprovement, Color(0xFF007bff))
        }

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("月度压力分布", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 8.dp))
                WeekdayHeaderRow()
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Legend("低", Color(0xFF10B981))
                    Legend("中", Color(0xFFF59E0B))
                    Legend("高", Color(0xFFEF4444))
                }
                MonthStressGrid(monthPoints)
            }
        }
    }
}

@Composable
private fun YearSection(data: StressUiData) {
    val yearPoints = data.summary
    val average = averageValue(yearPoints)
    val highestMonth = highestPressureMonth(yearPoints)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "年均压力等级", average.toString(), " /${stressLevel(average).shortLabel}", Icons.Default.TrendingUp, stressLevel(average).color)
            StatCard(Modifier.weight(1f), "最高月", highestMonth, "", Icons.Default.Warning, Color(0xFFEF4444))
        }

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("年度趋势分析", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 16.dp))
                YearlyStressChart(yearPoints)
            }
        }
    }
}

@Composable
private fun ExerciseCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, bgColor: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().clickable { }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(bgColor, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B))
            }
            Box(modifier = Modifier.size(40.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
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
private fun WeeklyStressBars(points: List<HealthTrendPoint>) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val padded = if (points.size >= 7) points.takeLast(7) else List(7 - points.size) { HealthTrendPoint(0L, 0) } + points

    Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        padded.forEachIndexed { index, point ->
            val value = point.value.coerceAtLeast(0)
            val height = (value / 100f).coerceIn(0.08f, 1f)
            val level = stressLevel(value)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth(0.6f).fillMaxSize(height).background(level.color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                Spacer(modifier = Modifier.height(8.dp))
                Text(labels[index], fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun MonthStressGrid(points: List<HealthTrendPoint>) {
    val cells = buildMonthStressCells(points)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(cell.background, RoundedCornerShape(8.dp))
                            .then(
                                if (cell.border != null) {
                                    Modifier.border(1.dp, cell.border, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            cell.label,
                            color = cell.textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun YearlyStressChart(points: List<HealthTrendPoint>) {
    val monthlyValues = points
        .groupBy { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.timestamp), ZoneId.systemDefault()).monthValue }
        .toSortedMap()
        .mapValues { (_, values) -> averageValue(values) }

    Column {
        Row(modifier = Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            (1..12).forEach { month ->
                val value = monthlyValues[month] ?: 0
                val height = (value / 100f).coerceIn(0.05f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.fillMaxWidth(0.5f).fillMaxSize(height).background(stressLevel(value).color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${month}月", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
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

private suspend fun loadStressUiData(
    context: android.content.Context,
    selectedRange: TimeRange,
    fallbackCurrent: Int,
    fallbackDayDetail: List<HealthTrendPoint>,
    fallbackRecentSummary: List<HealthTrendPoint>
): StressUiData {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = when (selectedRange) {
        TimeRange.DAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
        TimeRange.WEEK -> today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        TimeRange.MONTH -> today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        TimeRange.YEAR -> today.minusDays(364).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    return when (selectedRange) {
        TimeRange.DAY -> {
            val detail = OppoHealthSdkManager.getPressureTimeline(context, start, end).getOrDefault(fallbackDayDetail)
            StressUiData(
                current = detail.lastOrNull()?.value ?: fallbackCurrent,
                detail = detail,
                summary = detail
            )
        }
        else -> {
            val summary = OppoHealthSdkManager.getPressureSummary(context, start, end)
                .getOrDefault(if (selectedRange == TimeRange.WEEK || selectedRange == TimeRange.MONTH) fallbackRecentSummary else emptyList())
            StressUiData(
                current = summary.lastOrNull()?.value ?: fallbackCurrent,
                detail = emptyList(),
                summary = summary
            )
        }
    }
}

private data class StressUiData(
    val current: Int,
    val detail: List<HealthTrendPoint>,
    val summary: List<HealthTrendPoint>
) {
    companion object {
        fun empty(current: Int) = StressUiData(current = current, detail = emptyList(), summary = emptyList())
    }
}

private data class StressLevel(
    val label: String,
    val shortLabel: String,
    val description: String,
    val color: Color,
    val background: Color,
    val alpha: Float
)

private fun stressLevel(value: Int): StressLevel {
    return when {
        value >= 70 -> StressLevel("高压力", "偏高", "当前压力处于较高区间，建议先做短时放松，再关注趋势变化。", Color(0xFFEA580C), Color(0xFFFFF7ED), 1f)
        value >= 40 -> StressLevel("中等压力", "中等", "当前压力略有波动，结合最近几天平均值一起判断会更可靠。", Color(0xFFF59E0B), Color(0xFFFEF3C7), 0.7f)
        value > 0 -> StressLevel("较低压力", "偏低", "当前压力相对稳定，可继续保持规律作息和活动节奏。", Color(0xFF10B981), Color(0xFFECFDF5), 0.45f)
        else -> StressLevel("待同步", "待同步", "当前还没有读取到足够的压力数据。", Color(0xFF94A3B8), Color(0xFFF1F5F9), 0.2f)
    }
}

private fun averageValue(points: List<HealthTrendPoint>): Int {
    return points.map { it.value }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
}

private fun highestPressureMonth(points: List<HealthTrendPoint>): String {
    if (points.isEmpty()) return "--"
    val month = points
        .groupBy { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.timestamp), ZoneId.systemDefault()).monthValue }
        .maxByOrNull { (_, values) -> averageValue(values) }
        ?.key
    return month?.let { "${it}月" } ?: "--"
}

private data class StressBucket(
    val label: String,
    val value: Int,
    val color: Color,
    val alpha: Float
)

private data class StressCalendarCell(
    val label: String,
    val background: Color,
    val textColor: Color,
    val border: Color? = null
)

private fun bucketIntraday(points: List<HealthTrendPoint>): List<StressBucket> {
    if (points.isEmpty()) {
        return listOf("08", "10", "12", "14", "16", "18").map {
            StressBucket("$it:00", 0, Color(0xFF94A3B8), 0.2f)
        }
    }

    val groups = listOf(8, 10, 12, 14, 16, 18).map { hour ->
        val window = points.filter {
            val h = java.time.Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).hour
            h in hour until hour + 2
        }
        val value = averageValue(window)
        val level = stressLevel(value)
        StressBucket("${hour}:00", value, level.color, level.alpha)
    }
    return groups
}

private fun currentMonthStressPoints(points: List<HealthTrendPoint>): List<HealthTrendPoint> {
    val zone = ZoneId.systemDefault()
    val currentMonth = YearMonth.now(zone)
    return points
        .filter {
            YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()) == currentMonth
        }
        .sortedBy { it.timestamp }
}

private fun buildMonthStressCells(points: List<HealthTrendPoint>): List<StressCalendarCell> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val currentMonth = YearMonth.now(zone)
    val valuesByDate = currentMonthStressPoints(points)
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .mapValues { (_, entries) -> averageValue(entries) }

    val cells = mutableListOf<StressCalendarCell>()
    repeat(currentMonth.atDay(1).dayOfWeek.value - 1) {
        cells += StressCalendarCell(
            label = "",
            background = Color.Transparent,
            textColor = Color.Transparent
        )
    }

    (1..currentMonth.lengthOfMonth()).forEach { day ->
        val date = currentMonth.atDay(day)
        val value = valuesByDate[date]
        if (value == null || date.isAfter(today)) {
            cells += StressCalendarCell(
                label = day.toString(),
                background = Color(0xFFF1F5F9),
                textColor = Color(0xFF94A3B8),
                border = Color(0xFFE2E8F0)
            )
        } else {
            cells += StressCalendarCell(
                label = day.toString(),
                background = stressLevel(value).color,
                textColor = Color.White
            )
        }
    }

    while (cells.size % 7 != 0) {
        cells += StressCalendarCell(
            label = "",
            background = Color.Transparent,
            textColor = Color.Transparent
        )
    }

    return cells
}

private fun buildStressShareText(range: TimeRange, data: StressUiData): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return when (range) {
        TimeRange.DAY -> {
            val points = data.detail.sortedBy { it.timestamp }
            if (points.isEmpty() && data.current <= 0) return ""
            val peak = points.maxByOrNull { it.value }?.value ?: data.current
            val average = averageValue(points.ifEmpty { listOf(HealthTrendPoint(today.atStartOfDay(zone).toInstant().toEpochMilli(), data.current)) })
            "压力指数日报\n日期：$today\n当前压力：${data.current}\n平均压力：$average\n峰值压力：$peak"
        }
        TimeRange.WEEK -> {
            val points = data.summary.takeLast(7)
            if (points.isEmpty()) return ""
            "压力指数周报\n统计天数：${points.size}天\n平均压力：${averageValue(points)}\n高压力天数：${points.count { it.value >= 60 }}天"
        }
        TimeRange.MONTH -> {
            val points = currentMonthStressPoints(data.summary)
            if (points.isEmpty() && data.current <= 0) return ""
            val currentMonth = YearMonth.now(zone)
            "压力指数月报\n月份：${currentMonth.year}-${currentMonth.monthValue.toString().padStart(2, '0')}\n平均压力：${averageValue(points)}\n低压力天数：${points.count { it.value in 1..39 }}天\n高压力天数：${points.count { it.value >= 60 }}天"
        }
        TimeRange.YEAR -> {
            val points = data.summary
            if (points.isEmpty() && data.current <= 0) return ""
            "压力指数年报\n平均压力：${averageValue(points)}\n最高压力月份：${highestPressureMonth(points)}"
        }
    }
}
