package com.silverlink.app.ui.history

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
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
import com.silverlink.app.feature.health.HealthTrendPoint
import com.silverlink.app.feature.health.OppoHealthDashboardData
import com.silverlink.app.feature.health.OppoHealthSdkManager
import com.silverlink.app.ui.components.TimeRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

private data class WeekBloodOxygenEntry(
    val date: LocalDate,
    val value: Int?,
    val isFuture: Boolean
)

private data class MonthBloodOxygenCell(
    val date: LocalDate?,
    val value: Int?,
    val isFuture: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodOxygenDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val dashboard by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(TimeRange.DAY) }
    val dayPoints = dashboard?.bloodOxygenTimeline.orEmpty().sortedBy { it.timestamp }
    val rangePoints by produceState(
        initialValue = emptyList<HealthTrendPoint>(),
        selectedRange,
        dashboard
    ) {
        value = when (selectedRange) {
            TimeRange.DAY -> dayPoints
            TimeRange.WEEK -> {
                val window = currentWeekWindow(zone)
                OppoHealthSdkManager.getBloodOxygenSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            TimeRange.MONTH -> {
                val window = currentMonthWindow(zone)
                OppoHealthSdkManager.getBloodOxygenSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
            else -> {
                val window = currentTimeWindow(selectedRange)
                OppoHealthSdkManager.getBloodOxygenSummary(context, window.start, window.end)
                    .getOrDefault(emptyList())
                    .sortedBy { it.timestamp }
            }
        }
    }

    val weekEntries = remember(rangePoints, zone) { buildWeekEntries(rangePoints, zone) }
    val monthPoints = remember(rangePoints, zone) { currentMonthPoints(rangePoints, zone) }
    val monthCells = remember(rangePoints, zone) { buildMonthCells(rangePoints, zone) }

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
                BloodOxygenTabs(selectedRange = selectedRange, onRangeSelected = { selectedRange = it })
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
                    TimeRange.DAY -> BloodOxygenDaySection(dashboard, dayPoints)
                    TimeRange.WEEK -> BloodOxygenWeekSection(weekEntries)
                    TimeRange.MONTH -> BloodOxygenMonthSection(monthPoints, monthCells, zone)
                    TimeRange.YEAR -> BloodOxygenYearSectionEnhanced(rangePoints)
                }
            }
        }
    }
}

@Composable
private fun BloodOxygenTabs(selectedRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
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
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
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
    val zone = ZoneId.systemDefault()
    val chartPoints = remember(points, zone) { buildHourlyBloodOxygenPoints(points, zone) }
    val latest = data?.bloodOxygen?.takeIf { it > 0 } ?: points.lastOrNull()?.value ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "当前血氧", "${latest}%", "", Icons.Default.Favorite, Color(0xFF10B981))
            MetricCard(Modifier.weight(1f), "今日均值", "${averageTrend(points)}%", "", Icons.Default.TrendingUp, Color(0xFF0EA5E9))
        }
        SectionCard("今日血氧趋势") {
            if (chartPoints.isEmpty()) {
                EmptySectionText()
            } else {
                BloodOxygenLineChart(chartPoints)
            }
        }
        InsightCard(
            title = "血氧提示",
            body = if (points.isEmpty()) {
                "今天还没有读取到血氧时间线。"
            } else {
                "今日血氧范围 ${minTrend(points)}% - ${maxTrend(points)}%，分布统计来自真实采样。"
            }
        )
    }
}

@Composable
private fun BloodOxygenWeekSection(entries: List<WeekBloodOxygenEntry>) {
    val actualPoints = entries.mapNotNull { entry ->
        entry.value?.let {
            HealthTrendPoint(
                timestamp = entry.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                value = it
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "周平均", "${averageTrend(actualPoints)}%", "", Icons.Default.TrendingUp, Color(0xFF10B981))
            MetricCard(Modifier.weight(1f), "异常次数", actualPoints.count { it.value < 95 }.toString(), "", Icons.Default.Warning, Color(0xFFF59E0B))
        }
        SectionCard("本周血氧", formatWeekLabel(ZoneId.systemDefault())) {
            if (entries.none { it.value != null }) {
                EmptySectionText()
            } else {
                BloodOxygenWeekChart(entries)
            }
        }
        InsightCard(
            title = "趋势分析",
            body = "周视图按本周一到周日展示，未来日期不显示数据，点击对应节点会显示当天血氧。"
        )
    }
}

@Composable
private fun BloodOxygenMonthSection(
    points: List<HealthTrendPoint>,
    cells: List<MonthBloodOxygenCell>,
    zone: ZoneId
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "月平均", "${averageTrend(points)}%", "", Icons.Default.TrendingUp, Color(0xFF10B981))
            MetricCard(Modifier.weight(1f), "异常天数", points.count { it.value < 95 }.toString(), "", Icons.Default.Warning, Color(0xFFF59E0B))
        }
        SectionCard("每日血氧热力图", formatMonthLabel(zone)) {
            BloodOxygenMonthGrid(cells)
        }
        InsightCard(
            title = "热力说明",
            body = "自然月按日历排布，未来日期和无数据日期显示为浅灰色，并在下方给出颜色标注。"
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
                Icons.Default.Warning,
                Color(0xFFF59E0B)
            )
        }
        InsightCard(
            title = "年度趋势",
            body = if (points.isEmpty()) "最近 12 个月没有读取到真实血氧汇总。" else "年度统计按真实月均血氧生成。"
        )
    }
}

@Composable
private fun BloodOxygenYearSectionEnhanced(points: List<HealthTrendPoint>) {
    val values = monthlyAverageValues(points)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "年均血氧", "${averageTrend(points)}%", "", Icons.Default.Favorite, Color(0xFF10B981))
            MetricCard(
                Modifier.weight(1f),
                "最低月份",
                monthLabelFromIndex(values.indices.minByOrNull { values[it] } ?: 0),
                "",
                Icons.Default.Warning,
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
            body = if (points.isEmpty()) "最近 12 个月没有读取到真实血氧汇总。" else "年度统计按真实月均血氧生成。"
        )
    }
}

@Composable
private fun BloodOxygenYearChart(values: List<Int>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val minValue = (values.minOrNull() ?: 0).coerceAtMost(90)
                val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(minValue + 1)
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue).toFloat() / (maxValue - minValue).toFloat()
                    val y = size.height - ratio * (size.height - 20.dp.toPx()) - 10.dp.toPx()
                    if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 5f, cap = StrokeCap.Round))
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue).toFloat() / (maxValue - minValue).toFloat()
                    val y = size.height - ratio * (size.height - 20.dp.toPx()) - 10.dp.toPx()
                    drawCircle(Color.White, radius = 10f, center = Offset(x, y))
                    drawCircle(Color(0xFF10B981), radius = 5.5f, center = Offset(x, y))
                }
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
private fun BloodOxygenLineChart(points: List<HealthTrendPoint>) {
    val zone = ZoneId.systemDefault()
    var selectedIndex by remember(points) {
        mutableStateOf(points.lastIndex.coerceAtLeast(0))
    }
    val selected = points.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        selected?.let { point ->
            val time = Instant.ofEpochMilli(point.timestamp).atZone(zone).toLocalTime()
            Text(
                text = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')} ${point.value}%",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 16.dp)
            ) {
                val minValue = 85f
                val maxValue = 100f
                val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                val linePath = Path()

                points.forEachIndexed { index, point ->
                    val x = stepX * index
                    val ratio = (point.value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }

                drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 6f, cap = StrokeCap.Round))
                points.forEachIndexed { index, point ->
                    val x = stepX * index
                    val ratio = (point.value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    drawCircle(Color.White, radius = if (index == selectedIndex) 9f else 8f, center = Offset(x, y))
                    drawCircle(
                        color = if (index == selectedIndex) Color(0xFF059669) else Color(0xFF10B981),
                        radius = if (index == selectedIndex) 6f else 5f,
                        center = Offset(x, y)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                points.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedIndex = index }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { index, point ->
                val showLabel = when {
                    points.size <= 5 -> true
                    index == 0 || index == points.lastIndex -> true
                    index % (points.size / 3).coerceAtLeast(1) == 0 -> true
                    else -> false
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showLabel) dayTimeLabel(point.timestamp, zone) else "",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun BloodOxygenWeekChart(entries: List<WeekBloodOxygenEntry>) {
    val maxValue = (entries.maxOfOrNull { it.value ?: 0 } ?: 100).coerceAtLeast(100)
    val minValue = 85
    var selectedIndex by remember(entries) {
        mutableStateOf(entries.indexOfLast { !it.isFuture && it.value != null }.coerceAtLeast(0))
    }
    val selected = entries.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        selected?.let { entry ->
            val text = if (entry.value != null) {
                "${entry.date.monthValue}月${entry.date.dayOfMonth}日 ${entry.value}%"
            } else {
                "${entry.date.monthValue}月${entry.date.dayOfMonth}日 暂无数据"
            }
            Text(text, fontSize = 13.sp, color = Color(0xFF475569))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { index, entry ->
                val ratio = (((entry.value ?: minValue) - minValue).toFloat() / (maxValue - minValue).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
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
                                .height(150.dp)
                                .background(Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
                        )
                        if (!entry.isFuture) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((150.dp * ratio).coerceAtLeast(6.dp))
                                    .background(
                                        if (index == selectedIndex) Color(0xFF10B981) else Color(0xFF86EFAC),
                                        RoundedCornerShape(999.dp)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = weekDayLabel(entry.date.dayOfWeek),
                        fontSize = 11.sp,
                        color = if (entry.isFuture) Color(0xFFCBD5E1) else Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun BloodOxygenMonthGrid(cells: List<MonthBloodOxygenCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 12.sp, color = Color(0xFF94A3B8))
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
                        if (cell.date != null) {
                            Text(
                                text = cell.date.dayOfMonth.toString(),
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
            LegendItem("正常", Color(0xFF10B981))
            LegendItem("偏低", Color(0xFFF59E0B))
            LegendItem("预警", Color(0xFFEF4444))
            LegendItem("未来/无数据", Color(0xFFF1F5F9), Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, textColor: Color = Color(0xFF475569)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, fontSize = 12.sp, color = textColor)
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
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = Color(0xFF007BFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun InsightCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
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
    val today = LocalDate.now(zone)
    val month = YearMonth.from(today)
    return TimeWindow(
        start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        end = month.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    )
}

private fun buildWeekEntries(points: List<HealthTrendPoint>, zone: ZoneId): List<WeekBloodOxygenEntry> {
    val today = LocalDate.now(zone)
    val monday = today.with(DayOfWeek.MONDAY)
    val valuesByDate = points.associateBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    return (0..6).map { offset ->
        val date = monday.plusDays(offset.toLong())
        val isFuture = date.isAfter(today)
        WeekBloodOxygenEntry(
            date = date,
            value = if (isFuture) null else valuesByDate[date]?.value,
            isFuture = isFuture
        )
    }
}

private fun currentMonthPoints(points: List<HealthTrendPoint>, zone: ZoneId): List<HealthTrendPoint> {
    val today = LocalDate.now(zone)
    val month = YearMonth.from(today)
    return points.filter {
        val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        !date.isAfter(today) && YearMonth.from(date) == month
    }
}

private fun buildMonthCells(points: List<HealthTrendPoint>, zone: ZoneId): List<MonthBloodOxygenCell> {
    val today = LocalDate.now(zone)
    val month = YearMonth.from(today)
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val valuesByDate = points.associateBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    val cells = mutableListOf<MonthBloodOxygenCell>()
    repeat(leadingBlanks) {
        cells += MonthBloodOxygenCell(date = null, value = null, isFuture = false)
    }
    (1..month.lengthOfMonth()).forEach { day ->
        val date = month.atDay(day)
        val isFuture = date.isAfter(today)
        cells += MonthBloodOxygenCell(
            date = date,
            value = if (isFuture) null else valuesByDate[date]?.value,
            isFuture = isFuture
        )
    }
    while (cells.size % 7 != 0) {
        cells += MonthBloodOxygenCell(date = null, value = null, isFuture = false)
    }
    return cells
}

private fun buildHourlyBloodOxygenPoints(points: List<HealthTrendPoint>, zone: ZoneId): List<HealthTrendPoint> {
    return points
        .groupBy {
            Instant.ofEpochMilli(it.timestamp)
                .atZone(zone)
                .toLocalDateTime()
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        }
        .toSortedMap(compareBy<LocalDateTime> { it })
        .map { (hour, values) ->
            HealthTrendPoint(
                timestamp = hour.atZone(zone).toInstant().toEpochMilli(),
                value = values.map { it.value }.average().toInt()
            )
        }
}

private fun monthCellColor(cell: MonthBloodOxygenCell): Color {
    if (cell.date == null) return Color.Transparent
    if (cell.value == null) return Color(0xFFF1F5F9)
    return when {
        cell.value < 90 -> Color(0xFFEF4444)
        cell.value < 95 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }
}

private fun monthCellTextColor(cell: MonthBloodOxygenCell): Color {
    if (cell.date == null) return Color.Transparent
    return if (cell.value == null) Color(0xFF94A3B8) else Color.White
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
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private fun dayTimeLabel(timestamp: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
    return "${time.hour.toString().padStart(2, '0')}:00"
}

private fun monthLabelFromIndex(index: Int): String = "${index + 1}月"
