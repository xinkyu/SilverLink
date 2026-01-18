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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

// ==================== A. 顶栏导航 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTopBar(
    title: String = "健康记录",
    onRefresh: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    TopAppBar(
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
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

// ==================== D. 时间轴分布图 ====================

enum class ChartType(val label: String) {
    MOOD("情绪"),
    MEDICATION("用药记录")
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
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTypeSelected(type) },
                color = backgroundColor,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = type.label,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
    val timeLabels = listOf("00:00", "06:00", "12:00", "18:00", "24:00")
    val lanes = listOf(
        "愉悦" to MoodColorHappy,
        "平静" to MoodColorNeutral,
        "不愉悦" to MoodColorSad
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .height(120.dp),
                    verticalArrangement = Arrangement.SpaceBetween
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

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .pointerInput(moodPoints) {
                            detectTapGestures { offset ->
                                if (moodPoints.isEmpty()) return@detectTapGestures
                                val width = size.width
                                val height = size.height
                                val laneHeight = height / lanes.size

                                fun pointToX(point: MoodTimePoint): Float {
                                    val timeParts = point.time.split(":")
                                    val hours = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
                                    val minutes = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                                    val totalMinutes = hours * 60 + minutes
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

                        lanes.forEachIndexed { index, (_, color) ->
                            val y = laneHeight * (index + 0.5f)
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.2f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                            )
                        }

                        moodPoints.forEach { point ->
                            val timeParts = point.time.split(":")
                            val hours = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
                            val minutes = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                            val totalMinutes = hours * 60 + minutes
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            timeLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                .fillMaxWidth()
                .padding(16.dp),
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
