package com.silverlink.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min

// ==================== 数据类 ====================

/**
 * 时间轴上的情绪数据点
 */
data class MoodTimePoint(
    val time: String,       // "08:30"
    val mood: String,       // "HAPPY" | "NEUTRAL" | "SAD" etc.
    val note: String = "",  // 对话摘要
    val timestamp: Long = 0
)

/**
 * 药品服用状态
 */
data class MedicationStatus(
    val name: String,
    val dosage: String,
    val times: List<String>,        // ["08:00", "12:00", "18:00"]
    val takenTimes: Set<String>     // 已服用的时间点
)

data class MedicationSummary(
    val takenCount: Int,
    val totalCount: Int,
    val missedByMedication: List<Pair<String, Int>>
)

data class MoodDistributionSlice(
    val label: String,
    val count: Int,
    val color: Color
)

// ==================== 颜色定义 ====================

val MoodColorHappy = Color(0xFFFF9800)      // 橙色 - 愉悦
val MoodColorNeutral = Color(0xFF4DD0E1)    // 青色 - 平静
val MoodColorSad = Color(0xFF9C27B0)        // 紫色 - 不愉悦
val MoodColorAnxious = Color(0xFFE91E63)    // 粉色 - 焦虑
val MoodColorAngry = Color(0xFFF44336)      // 红色 - 生气

fun getMoodColor(mood: String): Color {
    return when (mood.uppercase()) {
        "HAPPY", "愉悦" -> MoodColorHappy
        "NEUTRAL", "平静" -> MoodColorNeutral
        "SAD", "不愉悦", "难过" -> MoodColorSad
        "ANXIOUS", "焦虑" -> MoodColorAnxious
        "ANGRY", "生气" -> MoodColorAngry
        else -> MoodColorNeutral
    }
}

fun getMoodDisplayText(mood: String): String {
    return when (mood.uppercase()) {
        "HAPPY" -> "愉悦"
        "NEUTRAL" -> "平静"
        "SAD" -> "不愉悦"
        "ANXIOUS" -> "焦虑"
        "ANGRY" -> "生气"
        else -> mood
    }
}

private fun markdownToAnnotatedString(text: String) = buildAnnotatedString {
    val lines = text.trim().lines()
    var inCodeBlock = false

    fun appendInlineMarkdown(line: String) {
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    val end = line.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(line[i])
                        i++
                    }
                }
                line.startsWith("`", i) -> {
                    val end = line.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) {
                            append(line.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(line[i])
                        i++
                    }
                }
                line.startsWith("*", i) -> {
                    val end = line.indexOf('*', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(line.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(line[i])
                        i++
                    }
                }
                line.startsWith("__", i) -> {
                    val end = line.indexOf("__", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(line[i])
                        i++
                    }
                }
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
    }

    lines.forEachIndexed { index, rawLine ->
        var line = rawLine

        if (line.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            return@forEachIndexed
        }

        if (inCodeBlock) {
            withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) {
                append(line)
            }
        } else {
            when {
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                        append(line.removePrefix("# "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.matches(Regex("^[-*+]\\s+.*")) -> {
                    append("• ")
                    appendInlineMarkdown(line.replace(Regex("^[-*+]\\s+"), ""))
                }
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val number = line.substringBefore('.')
                    append(number)
                    append(". ")
                    appendInlineMarkdown(line.replace(Regex("^\\d+\\.\\s+"), ""))
                }
                else -> appendInlineMarkdown(line)
            }
        }

        if (index != lines.lastIndex) {
            append("\n")
        }
    }
}

private fun buildMoodDistribution(points: List<MoodTimePoint>): List<MoodDistributionSlice> {
    val grouped = points.groupBy { getMoodDisplayText(it.mood) }
    val order = listOf("愉悦", "平静", "不愉悦", "焦虑", "生气")
    return order.mapNotNull { label ->
        val count = grouped[label]?.size ?: 0
        if (count <= 0) return@mapNotNull null
        MoodDistributionSlice(
            label = label,
            count = count,
            color = getMoodColor(label)
        )
    }
}

// ==================== A. 顶栏导航 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTopBar(
    title: String = "健康记录",
    onRefresh: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = primaryColor
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

// ==================== B. 时间维度与日期选择 ====================

enum class TimeRange(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeSelector(
    selectedRange: TimeRange,
    selectedDate: Date,
    onRangeSelected: (TimeRange) -> Unit,
    onDateSelected: (Date) -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("M月d日 E", Locale.CHINESE) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Tab 切换
        val tabs = TimeRange.entries.toList()
        val selectedIndex = tabs.indexOf(selectedRange)
        
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = Color.Transparent,
            contentColor = primaryColor,
            indicator = { tabPositions ->
                if (selectedIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        height = 3.dp,
                        color = primaryColor
                    )
                }
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, range ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onRangeSelected(range) },
                    text = {
                        Text(
                            text = range.label,
                            fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 日期选择
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDatePicker = true }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateFormat.format(selectedDate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "选择日期",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    // 日期选择器对话框
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.time
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateSelected(Date(it))
                    }
                    showDatePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ==================== C. 核心状态展示 ====================

@Composable
fun HeroStatusDisplay(
    currentMood: String?,
    latestTime: String?,
    titlePrefix: String = ""
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentMood != null) {
            val moodColor = getMoodColor(currentMood)
            val moodText = getMoodDisplayText(currentMood)
            
            Text(
                text = moodText,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                ),
                color = moodColor
            )
            
            if (latestTime != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (titlePrefix.isNotEmpty()) {
                        "${titlePrefix}最新 ${sanitizeTime(latestTime)}"
                    } else {
                        "最新值 ${sanitizeTime(latestTime)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = if (titlePrefix.isNotEmpty()) "${titlePrefix}暂无记录" else "暂无记录",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun sanitizeTime(raw: String): String {
    val regex = Regex("\\d{2}:\\d{2}")
    return regex.find(raw)?.value ?: raw.takeLast(5)
}

private fun minutesOfDay(point: MoodTimePoint): Int {
    // 优先使用 time 字符串（已经是格式化好的本地时间）
    val normalized = point.time.replace("：", ":")
    val match = Regex("(\\d{1,2}):(\\d{2})").find(normalized)
    if (match != null) {
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        if (hours in 0..23 && minutes in 0..59) {
            return hours * 60 + minutes
        }
    }

    // 回退到时间戳计算
    if (point.timestamp > 0) {
        val millis = if (point.timestamp in 1L..9_999_999_999L) {
            point.timestamp * 1000L
        } else {
            point.timestamp
        }
        val cal = Calendar.getInstance().apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
            timeInMillis = millis
        }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    return 0
}

// ==================== D. 时间轴分布图 ====================

enum class ChartType(val label: String) {
    MOOD("情绪"),
    MEDICATION("用药记录"),
    COGNITIVE("认知评估")
}

@Composable
fun ChartTypeToggle(
    selectedType: ChartType,
    onTypeSelected: (ChartType) -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        ChartType.entries.forEach { type ->
            val isSelected = selectedType == type
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) primaryColor else Color.Transparent,
                label = "bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "text"
            )
            
            Surface(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTypeSelected(type) },
                color = backgroundColor,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = type.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun MoodTimelineChart(
    moodPoints: List<MoodTimePoint>,
    onPointClick: (MoodTimePoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeLabels = listOf(
        "00:00", "06:00", "12:00", "18:00", "24:00"
    )
    val lanes = listOf(
        "愉悦" to MoodColorHappy,
        "平静" to MoodColorNeutral,
        "不愉悦" to MoodColorSad
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .pointerInput(moodPoints) {
                            detectTapGestures { offset ->
                                if (moodPoints.isEmpty()) return@detectTapGestures
                                val width = size.width
                                val height = size.height
                                val laneHeight = height / lanes.size

                                fun pointToX(point: MoodTimePoint): Float {
                                    val totalMinutes = minutesOfDay(point)
                                    val xRatio = totalMinutes / (24f * 60f)
                                    return width * xRatio
                                }

                                fun pointToLaneIndex(point: MoodTimePoint): Int {
                                    return when (getMoodDisplayText(point.mood)) {
                                        "愉悦" -> 0
                                        "平静" -> 1
                                        else -> 2
                                    }
                                }

                                val candidates = moodPoints.map { point ->
                                    val x = pointToX(point)
                                    val laneIndex = pointToLaneIndex(point)
                                    val y = laneHeight * (laneIndex + 0.5f)
                                    Triple(point, x, y)
                                }

                                val nearest = candidates.minByOrNull { (_, x, y) ->
                                    abs(x - offset.x) + abs(y - offset.y)
                                }

                                val threshold = 18.dp.toPx()
                                if (nearest != null && abs(nearest.second - offset.x) < threshold) {
                                    onPointClick(nearest.first)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val laneHeight = height / lanes.size

                        lanes.forEachIndexed { index, _ ->
                            val y = laneHeight * (index + 0.5f)
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.2f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                            )
                        }

                        // 垂直时间参考线（每3小时）
                        timeLabels.forEachIndexed { index, _ ->
                            val x = width * (index / (timeLabels.size - 1f))
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.15f),
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
                            )
                        }

                        moodPoints.forEach { point ->
                            val totalMinutes = minutesOfDay(point)
                            val xRatio = totalMinutes / (24f * 60f)
                            val x = width * xRatio

                            val laneIndex = when (getMoodDisplayText(point.mood)) {
                                "愉悦" -> 0
                                "平静" -> 1
                                else -> 2
                            }
                            val centerY = laneHeight * (laneIndex + 0.5f)

                            drawLine(
                                color = getMoodColor(point.mood),
                                start = Offset(x, centerY - 14.dp.toPx()),
                                end = Offset(x, centerY + 14.dp.toPx()),
                                strokeWidth = 6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

        Spacer(modifier = Modifier.height(8.dp))

        // 时间标签 - 使用与图表相同的 padding，并精确定位每个标签
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            val totalWidth = maxWidth
            timeLabels.forEachIndexed { index, label ->
                val fraction = index / (timeLabels.size - 1f)
                val offsetX = totalWidth * fraction
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(48.dp)
                        .offset(x = offsetX - 24.dp) // 居中对齐，减去一半宽度
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            lanes.forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MoodDistributionDonutChart(
    moodPoints: List<MoodTimePoint>,
    modifier: Modifier = Modifier
) {
    val slices = buildMoodDistribution(moodPoints)
    val total = slices.sumOf { it.count }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "情绪分布",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (total == 0) {
                Text(
                    text = "暂无情绪记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 24.dp.toPx()
                        var startAngle = -90f
                        slices.forEach { slice ->
                            val sweep = (slice.count / total.toFloat()) * 360f
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += sweep
                        }
                    }

                    Text(
                        text = "$total 条",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    slices.forEach { slice ->
                        val percent = (slice.count * 100f / total).coerceAtMost(100f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(slice.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = slice.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = String.format(Locale.getDefault(), "%.0f%%", percent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = percent / 100f,
                            color = slice.color,
                            trackColor = slice.color.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoodAnalysisCard(
    analysis: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "AI 情绪分析",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    Text(
                        text = "正在分析情绪备注…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                analysis.isNullOrBlank() -> {
                    Text(
                        text = "暂无可分析的情绪备注",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = markdownToAnnotatedString(analysis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun MedicationStatusDisplay(
    medicationStatuses: List<MedicationStatus>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (medicationStatuses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无用药记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            medicationStatuses.forEach { med ->
                MedicationStatusCard(medication = med)
            }
        }
    }
}

@Composable
fun MedicationSummaryCard(
    summary: MedicationSummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "用药统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            val total = summary.totalCount.coerceAtLeast(0)
            val taken = summary.takenCount.coerceAtLeast(0).coerceAtMost(total)
            val progress = if (total == 0) 0f else taken / total.toFloat()

            Text(
                text = "已服用 $taken / $total 次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "未按时服用（药品/次数）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (summary.missedByMedication.isEmpty()) {
                Text(
                    text = "暂无未按时服用记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                summary.missedByMedication.forEach { (name, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF44336)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun MedicationStatusCard(
    medication: MedicationStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = medication.dosage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 服药进度
                val takenCount = medication.takenTimes.size
                val totalCount = medication.times.size
                Text(
                    text = "$takenCount/$totalCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (takenCount == totalCount) 
                        Color(0xFF4CAF50) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 时间点圆圈
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                medication.times.forEach { time ->
                    val isTaken = medication.takenTimes.contains(time)
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isTaken) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.3f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isTaken) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "已服用",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==================== E. 详细情况卡片 ====================

@Composable
fun MoodDetailCard(
    moodPoint: MoodTimePoint?,
    onDismiss: () -> Unit
) {
    if (moodPoint != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "详细情况",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(getMoodColor(moodPoint.mood))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${getMoodDisplayText(moodPoint.mood)} · ${moodPoint.time}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (moodPoint.note.isNotBlank()) {
                    Text(
                        text = moodPoint.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "暂无对话摘要",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==================== 认知评估相关 ====================

/**
 * 认知评估报告数据
 */
data class CognitiveReportUiData(
    val totalQuestions: Int,
    val correctAnswers: Int,
    val correctRate: Float,
    val averageResponseTimeMs: Long,
    val trend: String,              // "improving", "stable", "declining"
    val startDate: String,
    val endDate: String
)

/**
 * 认知评估报告卡片
 */
@Composable
fun CognitiveReportCard(
    report: CognitiveReportUiData?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "🧠 认知评估报告",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            when {
                isLoading -> {
                    Text(
                        text = "正在加载认知数据…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                report == null || report.totalQuestions == 0 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📝",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无认知测试记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "长辈可在「记忆相册」中进行记忆小游戏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    // 正确率显示
                    val ratePercent = (report.correctRate * 100).toInt()
                    val rateColor = when {
                        ratePercent >= 80 -> Color(0xFF4CAF50)
                        ratePercent >= 60 -> Color(0xFFFFC107)
                        else -> Color(0xFFFF5722)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${ratePercent}%",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = rateColor
                            )
                            Text(
                                text = "正确率",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            val trendEmoji = when (report.trend) {
                                "improving" -> "📈 进步中"
                                "declining" -> "📉 需关注"
                                else -> "➡️ 保持稳定"
                            }
                            val trendColor = when (report.trend) {
                                "improving" -> Color(0xFF4CAF50)
                                "declining" -> Color(0xFFFF5722)
                                else -> Color(0xFF9E9E9E)
                            }
                            Text(
                                text = trendEmoji,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = trendColor
                            )
                            Text(
                                text = "${report.startDate} ~ ${report.endDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 统计数据
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "总题数",
                            value = report.totalQuestions.toString()
                        )
                        StatItem(
                            label = "答对",
                            value = report.correctAnswers.toString()
                        )
                        StatItem(
                            label = "平均用时",
                            value = "${report.averageResponseTimeMs / 1000}秒"
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 正确率进度条
                    LinearProgressIndicator(
                        progress = report.correctRate.coerceIn(0f, 1f),
                        color = rateColor,
                        trackColor = rateColor.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun CognitiveAnalysisCard(
    analysis: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "AI 认知分析",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    Text(
                        text = "正在分析认知表现…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                analysis.isNullOrBlank() -> {
                    Text(
                        text = "暂无可分析的认知数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = markdownToAnnotatedString(analysis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
