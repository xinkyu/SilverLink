package com.silverlink.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.data.local.entity.MedicationLogEntity
import com.silverlink.app.data.local.entity.MoodLogEntity
import com.silverlink.app.ui.theme.WarmPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 历史记录主屏幕
 * 显示日历视图和每日健康摘要
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val monthSummaries by viewModel.monthSummaries.collectAsState()
    val medicationLogs by viewModel.medicationLogs.collectAsState()
    val moodLogs by viewModel.moodLogs.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFF8F0),
            Color(0xFFFFF0E6),
            Color(0xFFFFE8DA)
        )
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "健康记录",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = WarmPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 月份导航
            item {
                MonthNavigator(
                    currentMonth = currentMonth,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() }
                )
            }
            
            // 日历网格
            item {
                CalendarGrid(
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    summaries = monthSummaries,
                    onDateSelected = { viewModel.selectDate(it) }
                )
            }
            
            // 选中日期的详细信息
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    DayDetailCard(
                        date = selectedDate,
                        medicationLogs = medicationLogs,
                        moodLogs = moodLogs
                    )
                }
            }
            
            // 底部间距
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 月份导航组件
 */
@Composable
private fun MonthNavigator(
    currentMonth: Calendar,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthFormat = SimpleDateFormat("yyyy年MM月", Locale.CHINESE)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "上个月",
                    tint = WarmPrimary
                )
            }
            
            Text(
                text = monthFormat.format(currentMonth.time),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF5D4037)
            )
            
            IconButton(onClick = onNextMonth) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "下个月",
                    tint = WarmPrimary
                )
            }
        }
    }
}

/**
 * 日历网格
 */
@Composable
private fun CalendarGrid(
    currentMonth: Calendar,
    selectedDate: String,
    summaries: Map<String, DaySummary>,
    onDateSelected: (String) -> Unit
) {
    val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    // 计算本月天数和起始星期
    val tempCal = currentMonth.clone() as Calendar
    tempCal.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=周日
    val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    // 生成日期列表
    val dates = mutableListOf<String?>()
    repeat(firstDayOfWeek) { dates.add(null) } // 前面的空白
    for (day in 1..daysInMonth) {
        tempCal.set(Calendar.DAY_OF_MONTH, day)
        dates.add(dateFormat.format(tempCal.time))
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 星期标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weekDays.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 日期网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height((((dates.size + 6) / 7) * 56).dp),
                userScrollEnabled = false
            ) {
                items(dates) { dateStr ->
                    if (dateStr == null) {
                        Box(modifier = Modifier.aspectRatio(1f))
                    } else {
                        val summary = summaries[dateStr]
                        val isSelected = dateStr == selectedDate
                        val dayOfMonth = dateStr.substringAfterLast("-").toInt()
                        
                        DayCell(
                            day = dayOfMonth,
                            isSelected = isSelected,
                            summary = summary,
                            onClick = { onDateSelected(dateStr) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个日期格子
 */
@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    summary: DaySummary?,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> WarmPrimary
        summary != null -> Color(0xFFFFF3E0)
        else -> Color.Transparent
    }
    
    val textColor = when {
        isSelected -> Color.White
        else -> Color(0xFF5D4037)
    }
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .then(
                if (summary != null && !isSelected) {
                    Modifier.border(1.dp, WarmPrimary.copy(alpha = 0.3f), CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = textColor
            )
            
            // 显示情绪emoji或服药指示
            if (summary != null) {
                val emoji = getMoodEmoji(summary.dominantMood)
                if (emoji != null) {
                    Text(
                        text = emoji,
                        fontSize = 10.sp
                    )
                } else if (summary.hasMedicationLogs) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (summary.takenCount == summary.totalCount) 
                                    Color(0xFF4CAF50) else Color(0xFFFF9800),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

/**
 * 日期详情卡片
 */
@Composable
private fun DayDetailCard(
    date: String,
    medicationLogs: List<MedicationLogEntity>,
    moodLogs: List<MoodLogEntity>
) {
    val displayDate = try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINESE)
        outputFormat.format(inputFormat.parse(date)!!)
    } catch (e: Exception) {
        date
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 日期标题
            Text(
                text = displayDate,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF5D4037)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 服药记录
            if (medicationLogs.isNotEmpty()) {
                Text(
                    text = "💊 服药记录",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = WarmPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                medicationLogs.forEach { log ->
                    MedicationLogItem(log)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // 情绪记录
            if (moodLogs.isNotEmpty()) {
                Text(
                    text = "😊 情绪记录",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF7B1FA2)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                moodLogs.forEach { log ->
                    MoodLogItem(log)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            
            // 无记录时显示
            if (medicationLogs.isEmpty() && moodLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📝",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "这一天还没有记录哦",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

/**
 * 服药记录项
 */
@Composable
private fun MedicationLogItem(log: MedicationLogEntity) {
    val statusColor = when (log.status) {
        "taken" -> Color(0xFF4CAF50)
        "missed" -> Color(0xFFF44336)
        else -> Color(0xFFFF9800)
    }
    
    val statusIcon = when (log.status) {
        "taken" -> Icons.Default.Check
        else -> Icons.Default.Close
    }
    
    val statusText = when (log.status) {
        "taken" -> "已服用"
        "missed" -> "已错过"
        else -> "已推迟"
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = statusColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = log.scheduledTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = log.medicationName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF5D4037),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = log.dosage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    statusIcon,
                    contentDescription = statusText,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }
    }
}

/**
 * 情绪记录项
 */
@Composable
private fun MoodLogItem(log: MoodLogEntity) {
    val emoji = getMoodEmoji(log.mood) ?: "😐"
    val moodColor = getMoodColor(log.mood)
    
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val time = try {
        val fullFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        timeFormat.format(fullFormat.parse(log.createdAt.toString())!!)
    } catch (e: Exception) {
        // createdAt是时间戳,转换为时间字符串
        timeFormat.format(java.util.Date(log.createdAt))
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = moodColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getMoodDisplayName(log.mood),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF5D4037)
                )
                if (log.note.isNotBlank()) {
                    Text(
                        text = log.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
        }
    }
}

/**
 * 获取情绪对应的emoji
 */
private fun getMoodEmoji(mood: String?): String? {
    return when (mood?.uppercase()) {
        "HAPPY", "JOY" -> "😊"
        "SAD", "SADNESS" -> "😢"
        "ANGRY", "ANGER" -> "😠"
        "ANXIOUS", "ANXIETY", "FEAR" -> "😰"
        "NEUTRAL" -> "😐"
        "LOVE" -> "🥰"
        "SURPRISE" -> "😮"
        else -> null
    }
}

/**
 * 获取情绪对应的颜色
 */
private fun getMoodColor(mood: String): Color {
    return when (mood.uppercase()) {
        "HAPPY", "JOY" -> Color(0xFFFFEB3B)
        "SAD", "SADNESS" -> Color(0xFF2196F3)
        "ANGRY", "ANGER" -> Color(0xFFF44336)
        "ANXIOUS", "ANXIETY", "FEAR" -> Color(0xFF9C27B0)
        "LOVE" -> Color(0xFFE91E63)
        "SURPRISE" -> Color(0xFFFF9800)
        else -> Color(0xFF9E9E9E)
    }
}

/**
 * 获取情绪的显示名称
 */
private fun getMoodDisplayName(mood: String): String {
    return when (mood.uppercase()) {
        "HAPPY", "JOY" -> "开心"
        "SAD", "SADNESS" -> "难过"
        "ANGRY", "ANGER" -> "生气"
        "ANXIOUS", "ANXIETY", "FEAR" -> "焦虑"
        "NEUTRAL" -> "平静"
        "LOVE" -> "喜爱"
        "SURPRISE" -> "惊讶"
        else -> mood
    }
}
