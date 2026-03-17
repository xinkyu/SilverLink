package com.silverlink.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.data.remote.MedicationData
import com.silverlink.app.data.remote.MedicationLogData
import com.silverlink.app.ui.components.MedicationSummary
import com.silverlink.app.ui.components.TimeRange
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val selectedRange by viewModel.selectedRange.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val medicationSummary by viewModel.medicationSummary.collectAsState()
    val medicationStatuses by viewModel.medicationStatuses.collectAsState()
    val rangeMedicationLogs by viewModel.rangeMedicationLogs.collectAsState()
    val rangeMedications by viewModel.rangeMedications.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy年MM月", Locale.CHINA) }
    val currentDateText = dateFormat.format(selectedDate)

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "用药记录",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F1923),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color(0xFF475569))
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "日历", tint = Color(0xFF475569))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White.copy(alpha = 0.8f)),
                modifier = Modifier.background(Color.White)
            )
        }
    ) { innerPadding ->
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { viewModel.setSelectedDate(Date(it)) }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
        ) {
            // Segmented Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(50))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tabs = listOf(TimeRange.DAY to "日", TimeRange.WEEK to "周", TimeRange.MONTH to "月", TimeRange.YEAR to "年")
                tabs.forEach { (range, tabName) ->
                    val isSelected = selectedRange == range
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { viewModel.setTimeRange(range) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabName,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF0F1923) else Color(0xFF64748B)
                        )
                    }
                }
            }

            when (selectedRange) {
                TimeRange.DAY -> DailyView(selectedDate, currentDateText, viewModel, medicationStatuses, medicationSummary)
                TimeRange.WEEK -> WeeklyView(selectedDate, medicationSummary, rangeMedicationLogs, rangeMedications)
                TimeRange.MONTH -> MonthlyView(selectedDate, medicationSummary, rangeMedicationLogs, rangeMedications)
                TimeRange.YEAR -> YearlyView(selectedDate, rangeMedicationLogs, rangeMedications)
            }
        }
    }
}

@Composable
private fun DailyView(
    selectedDate: Date,
    currentDateText: String,
    viewModel: HistoryViewModel,
    medicationStatuses: List<com.silverlink.app.ui.components.MedicationStatus>,
    medicationSummary: com.silverlink.app.ui.components.MedicationSummary?
) {
    // Calendar Component (7Days)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    val cal = Calendar.getInstance().apply { time = selectedDate; add(Calendar.DAY_OF_YEAR, -1) }
                    viewModel.setSelectedDate(cal.time) 
                }, 
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev", tint = Color.Black)
            }
            Text(currentDateText, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
            IconButton(
                onClick = { 
                    val cal = Calendar.getInstance().apply { time = selectedDate; add(Calendar.DAY_OF_YEAR, 1) }
                    viewModel.setSelectedDate(cal.time) 
                }, 
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = Color.Black)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val days = listOf("日", "一", "二", "三", "四", "五", "六")
            val cal = Calendar.getInstance()
            cal.time = selectedDate
            val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            cal.add(Calendar.DAY_OF_YEAR, -(currentDayOfWeek - 1)) // 回到周日

            for (i in 0 until 7) {
                val isSelected = i + 1 == currentDayOfWeek
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                val dayStr = days[i]
                
                // 为了能在点击时把当时的cal存下
                val targetDate = cal.time

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(dayStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF007bff) else Color.Transparent)
                            .clickable {
                                viewModel.setSelectedDate(targetDate)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$currentDay",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.Black
                            )
                            if (isSelected) {
                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    Divider(color = Color(0xFFF1F5F9))

    // Daily Progress Section
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF007bff).copy(alpha = 0.05f))
            .padding(24.dp)
    ) {
        val total = medicationSummary?.totalCount ?: 0
        val taken = medicationSummary?.takenCount ?: 0
        val percentage = if (total > 0) (taken.toFloat() / total * 100).toInt() else 100
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("今日进度", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                    Text("已完成 $taken/$total 次用药", fontSize = 14.sp, color = Color(0xFF64748B))
                }
                Text("$percentage%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007bff))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (total > 0) taken.toFloat() / total else 1f)
                        .fillMaxHeight()
                        .background(Color(0xFF007bff))
                )
            }
        }
    }

    // Medication Schedule List
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "当日日程",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        val flattenedTimes = medicationStatuses.flatMap { status ->
            status.times.map { time ->
                Triple(time, status, status.takenTimes.contains(time))
            }
        }.sortedBy { it.first }

        if (flattenedTimes.isEmpty()) {
            Text("暂无服药计划", fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(16.dp))
        }

        flattenedTimes.forEachIndexed { index, (time, status, isTaken) ->
            MedicationTimelineItem(
                time = time,
                isLast = index == flattenedTimes.size - 1,
                title = status.name,
                statusText = if (isTaken) "已服用" else "待服用",
                isTaken = isTaken,
                description = "剂量: ${status.dosage}",
                confirmText = if (isTaken) "今日 $time 已确认" else "",
                onMarkTaken = {
                    viewModel.markMedicationAsTaken(status.name, status.dosage, time)
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun WeeklyView(
    selectedDate: Date,
    medicationSummary: MedicationSummary?,
    rangeMedicationLogs: List<MedicationLogData>,
    rangeMedications: List<MedicationData>
) {
    val weekDates = remember(selectedDate) { weekDates(selectedDate) }
    val statsByDate = remember(rangeMedicationLogs, rangeMedications, weekDates) {
        buildDailyStats(rangeMedicationLogs, rangeMedications, weekDates)
    }
    val todayDateStr = formatDate(selectedDate)
    val expectedPerDay = expectedDosePerDay(rangeMedications)
    val chartRows = weekDates.mapIndexed { index, dateStr ->
        val stat = statsByDate[dateStr] ?: DailyMedicationStat(0, expectedPerDay)
        val expected = stat.expected.coerceAtLeast(1)
        val takenRatio = (stat.taken.toFloat() / expected).coerceIn(0f, 1f)
        val missedRatio = ((expected - stat.taken).coerceAtLeast(0).toFloat() / expected).coerceIn(0f, 1f)
        val cal = Calendar.getInstance().apply { time = parseDate(dateStr) ?: Date() }
        WeekBarData(
            label = if (dateStr == todayDateStr) "今日" else "周${weekDayCn(cal.get(Calendar.DAY_OF_WEEK))}",
            taken = takenRatio,
            missed = missedRatio,
            isToday = dateStr == todayDateStr
        )
    }
    val adherence = calcPercentage(medicationSummary?.takenCount ?: 0, medicationSummary?.totalCount ?: 0)
    val deltaText = if (adherence >= 80) "保持稳定" else "仍可提升"
    val todayItems = remember(rangeMedicationLogs, rangeMedications, selectedDate) {
        buildScheduleForDay(todayDateStr, rangeMedicationLogs, rangeMedications)
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("本周服药依从性", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                    Text("$adherence%", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(deltaText, fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    chartRows.forEach { row ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(12.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (row.missed > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(row.missed)
                                            .background(Color(0xFF007BFF).copy(alpha = 0.2f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    )
                                }
                                if (row.taken > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(row.taken)
                                            .background(
                                                Color(0xFF007BFF),
                                                RoundedCornerShape(
                                                    topStart = if (row.missed == 0f) 4.dp else 0.dp,
                                                    topEnd = if (row.missed == 0f) 4.dp else 0.dp,
                                                    bottomStart = 4.dp,
                                                    bottomEnd = 4.dp
                                                )
                                            )
                                    )
                                }
                                if (row.isToday) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .background(Color(0xFFE2E8F0), RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                row.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (row.isToday) Color(0xFF0F1923) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendDot(Color(0xFF007BFF), "已按时服用")
                    Spacer(modifier = Modifier.width(16.dp))
                    LegendDot(Color(0xFF007BFF).copy(alpha = 0.2f), "漏服/推迟")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF007BFF).copy(alpha = 0.05f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007BFF).copy(alpha = 0.12f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFF007BFF), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("每周洞察", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "本周累计完成 ${medicationSummary?.takenCount ?: 0}/${medicationSummary?.totalCount ?: 0} 次用药。建议在固定时段设置提醒，进一步减少漏服。",
                        fontSize = 13.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("今日计划", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                Text(formatDisplayDate(selectedDate), fontSize = 12.sp, color = Color(0xFF64748B))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (todayItems.isEmpty()) {
                    Text("当天暂无用药计划", fontSize = 13.sp, color = Color(0xFF64748B), modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    todayItems.forEach { item ->
                        WeeklyScheduleCard(item)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun MonthlyView(
    selectedDate: Date,
    medicationSummary: MedicationSummary?,
    rangeMedicationLogs: List<MedicationLogData>,
    rangeMedications: List<MedicationData>
) {
    val dayHeaders = listOf("日", "一", "二", "三", "四", "五", "六")
    val selectedDay = Calendar.getInstance().apply { time = selectedDate }.get(Calendar.DAY_OF_MONTH)
    val monthMeta = remember(selectedDate) { monthMeta(selectedDate) }
    val monthDates = remember(selectedDate) { monthDateStrings(selectedDate) }
    val statsByDate = remember(rangeMedicationLogs, rangeMedications, monthDates) {
        buildDailyStats(rangeMedicationLogs, rangeMedications, monthDates)
    }
    val monthCells = monthDates.map { dateStr ->
        val day = dateStr.takeLast(2).toIntOrNull() ?: 1
        val stat = statsByDate[dateStr] ?: DailyMedicationStat(0, 0)
        MonthDayCell(day = day, status = dayStatusFromStat(stat))
    }
    val streakDays = longestStreak(statsByDate)
    val adherence = calcPercentage(medicationSummary?.takenCount ?: 0, medicationSummary?.totalCount ?: 0)
    val stableMedication = mostStableMedication(rangeMedicationLogs, rangeMedications)

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上月", tint = Color(0xFF0F1923))
                    }
                    Text(monthMeta.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下月", tint = Color(0xFF0F1923))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    dayHeaders.forEach { header ->
                        Text(
                            header,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val firstDayOffset = monthMeta.firstDayOffset
                val totalSlots = ((firstDayOffset + monthCells.size + 6) / 7) * 7
                val paddedCells = buildList {
                    repeat(firstDayOffset) { add(null) }
                    addAll(monthCells)
                    repeat(totalSlots - firstDayOffset - monthCells.size) { add(null) }
                }

                paddedCells.chunked(7).forEach { weekRow ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekRow.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cell != null) {
                                    val isSelected = cell.day == selectedDay
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(if (isSelected) 30.dp else 24.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color(0xFF007BFF) else Color.Transparent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cell.day.toString(),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF0F1923)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isSelected && cell.status != DayDotStatus.NoPlan -> Color.White
                                                        else -> cell.status.color
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendDot(Color(0xFF16A34A), "全服")
                    Spacer(modifier = Modifier.width(12.dp))
                    LegendDot(Color(0xFFF59E0B), "部分")
                    Spacer(modifier = Modifier.width(12.dp))
                    LegendDot(Color(0xFFEF4444), "未服")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("本月统计", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("服药达成率", fontSize = 12.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$adherence", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007BFF))
                            Text("%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (adherence / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF007BFF),
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("连续打卡", fontSize = 12.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$streakDays", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                            Text("天", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("本月最佳", fontSize = 11.sp, color = Color(0xFF16A34A))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF007BFF).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Color(0xFF007BFF))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("最稳定用药", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text(stableMedication.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                        Text("达成率 ${stableMedication.rate}%（${stableMedication.dosage}）", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

@Composable
fun YearlyView(
    selectedDate: Date,
    rangeMedicationLogs: List<MedicationLogData>,
    rangeMedications: List<MedicationData>
) {
    val year = Calendar.getInstance().apply { time = selectedDate }.get(Calendar.YEAR)
    val yearDates = remember(selectedDate) { yearDateStrings(year) }
    val statsByDate = remember(rangeMedicationLogs, rangeMedications, yearDates) {
        buildDailyStats(rangeMedicationLogs, rangeMedications, yearDates)
    }
    val monthRates = remember(statsByDate) { monthlyRates(statsByDate) }
    val yearlySummary = remember(statsByDate) { yearlySummary(statsByDate) }
    val yearReview = remember(rangeMedicationLogs, rangeMedications, statsByDate) {
        yearlyReview(rangeMedicationLogs, rangeMedications, statsByDate)
    }
    val heatMapRows = listOf("一", "二", "三", "四", "五", "六", "日")
    val heatMap = remember(statsByDate, yearDates) { buildHeatMapByWeek(statsByDate, yearDates) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("$year 年依从性概览", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AnnualSummaryCard("达标天数", yearlySummary.perfectDays.toString(), "${yearDates.size}天中", Color(0xFF007BFF), modifier = Modifier.weight(1f))
            AnnualSummaryCard("平均依从率", "${yearlySummary.avgRate}%", "全年统计", Color(0xFF007BFF), modifier = Modifier.weight(1f))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("月度一致性", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                Spacer(modifier = Modifier.height(10.dp))
                monthRates.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { month ->
                            MonthRing(month)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("年度热力图", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("少", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.width(6.dp))
                    (0..4).forEach { level ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(heatColor(level))
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("多", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Column(
                        modifier = Modifier.padding(top = 2.dp, end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        heatMapRows.forEach { d ->
                            Text(d, fontSize = 9.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 2.dp)
                    ) {
                        heatMap.forEach { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(end = 3.dp)) {
                                week.forEach { level ->
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(heatColor(level))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("年度回顾", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                YearlyReviewRow(
                    title = "最高频用药",
                    value = yearReview.mostFrequentName,
                    rightText = "${yearReview.mostFrequentCount} 次",
                    icon = Icons.Default.LocalHospital,
                    iconColor = Color(0xFF2563EB),
                    iconBg = Color(0xFFDBEAFE)
                )
                YearlyReviewRow(
                    title = "最长连续全服",
                    value = "${yearReview.longestStreakDays} 天",
                    rightText = yearReview.streakLabel,
                    icon = Icons.Default.Verified,
                    iconColor = Color(0xFF16A34A),
                    iconBg = Color(0xFFDCFCE7)
                )
                YearlyReviewRow(
                    title = "漏服总数",
                    value = "${yearReview.totalMissed} 次",
                    rightText = "按计划统计",
                    icon = Icons.Default.Notifications,
                    iconColor = Color(0xFFEA580C),
                    iconBg = Color(0xFFFFEDD5)
                )
            }
        }
    }
}

private data class WeekBarData(
    val label: String,
    val taken: Float,
    val missed: Float,
    val isToday: Boolean = false
)

private data class WeeklyMedicationItem(
    val name: String,
    val time: String,
    val dose: String,
    val taken: Boolean,
    val badgeColor: Color
)

private enum class DayDotStatus(val color: Color) {
    Done(Color(0xFF16A34A)),
    Partial(Color(0xFFF59E0B)),
    Missed(Color(0xFFEF4444)),
    NoPlan(Color(0xFFE2E8F0))
}

private data class MonthDayCell(
    val day: Int,
    val status: DayDotStatus
)

private data class MonthRate(
    val label: String,
    val progress: Float
)

private data class DailyMedicationStat(
    val taken: Int,
    val expected: Int
)

private data class StableMedication(
    val name: String,
    val dosage: String,
    val rate: Int
)

private data class YearlySummary(
    val perfectDays: Int,
    val avgRate: Int
)

private data class YearlyReviewData(
    val mostFrequentName: String,
    val mostFrequentCount: Int,
    val longestStreakDays: Int,
    val streakLabel: String,
    val totalMissed: Int
)

private data class MonthMeta(
    val title: String,
    val firstDayOffset: Int
)

private fun expectedDosePerDay(rangeMedications: List<MedicationData>): Int {
    return rangeMedications.sumOf { med ->
        med.times.split(",").map { it.trim() }.count { it.isNotEmpty() }
    }
}

private fun weekDates(selectedDate: Date): List<String> {
    val cal = Calendar.getInstance().apply { time = selectedDate }
    val first = cal.firstDayOfWeek
    while (cal.get(Calendar.DAY_OF_WEEK) != first) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    return buildList {
        repeat(7) {
            add(formatDate(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun monthDateStrings(selectedDate: Date): List<String> {
    val cal = Calendar.getInstance().apply { time = selectedDate }
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return buildList {
        repeat(maxDay) {
            add(formatDate(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

private fun yearDateStrings(year: Int): List<String> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val max = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
    return buildList {
        repeat(max) {
            add(formatDate(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun buildDailyStats(
    rangeMedicationLogs: List<MedicationLogData>,
    rangeMedications: List<MedicationData>,
    dates: List<String>
): Map<String, DailyMedicationStat> {
    val expected = expectedDosePerDay(rangeMedications)
    val uniqueTaken = rangeMedicationLogs
        .asSequence()
        .filter { it.status == "taken" }
        .distinctBy { "${it.date}|${it.medicationName}|${it.scheduledTime}" }
        .groupBy { it.date }
        .mapValues { it.value.size }

    return dates.associateWith { date ->
        DailyMedicationStat(
            taken = uniqueTaken[date] ?: 0,
            expected = expected
        )
    }
}

private fun buildScheduleForDay(
    date: String,
    rangeMedicationLogs: List<MedicationLogData>,
    rangeMedications: List<MedicationData>
): List<WeeklyMedicationItem> {
    val dayLogs = rangeMedicationLogs.filter { it.date == date }
    val takenKeys = dayLogs
        .filter { it.status == "taken" }
        .map { "${it.medicationName}|${it.scheduledTime}" }
        .toSet()

    val colors = listOf(Color(0xFFDBEAFE), Color(0xFFEDE9FE), Color(0xFFFFEDD5), Color(0xFFD1FAE5))
    val items = mutableListOf<WeeklyMedicationItem>()

    rangeMedications.forEachIndexed { medIndex, med ->
        val times = med.times.split(",").map { it.trim() }.filter { it.isNotEmpty() }.sorted()
        times.forEach { time ->
            items.add(
                WeeklyMedicationItem(
                    name = med.name,
                    time = time,
                    dose = med.dosage,
                    taken = takenKeys.contains("${med.name}|$time"),
                    badgeColor = colors[medIndex % colors.size]
                )
            )
        }
    }

    return items.sortedBy { it.time }
}

private fun weekDayCn(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        Calendar.MONDAY -> "一"
        Calendar.TUESDAY -> "二"
        Calendar.WEDNESDAY -> "三"
        Calendar.THURSDAY -> "四"
        Calendar.FRIDAY -> "五"
        Calendar.SATURDAY -> "六"
        else -> "日"
    }
}

private fun dayStatusFromStat(stat: DailyMedicationStat): DayDotStatus {
    if (stat.expected <= 0) return DayDotStatus.NoPlan
    if (stat.taken >= stat.expected) return DayDotStatus.Done
    if (stat.taken <= 0) return DayDotStatus.Missed
    return DayDotStatus.Partial
}

private fun monthMeta(selectedDate: Date): MonthMeta {
    val cal = Calendar.getInstance().apply {
        time = selectedDate
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val title = SimpleDateFormat("yyyy年MM月", Locale.CHINA).format(cal.time)
    val firstDayOffset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
    return MonthMeta(title, firstDayOffset)
}

private fun longestStreak(statsByDate: Map<String, DailyMedicationStat>): Int {
    var best = 0
    var current = 0
    statsByDate.toSortedMap().forEach { (_, stat) ->
        val full = stat.expected > 0 && stat.taken >= stat.expected
        if (full) {
            current += 1
            if (current > best) best = current
        } else {
            current = 0
        }
    }
    return best
}

private fun mostStableMedication(
    rangeMedicationLogs: List<MedicationLogData>,
    rangeMedications: List<MedicationData>
): StableMedication {
    if (rangeMedications.isEmpty()) return StableMedication("暂无", "--", 0)

    val takenByMed = rangeMedicationLogs
        .asSequence()
        .filter { it.status == "taken" }
        .distinctBy { "${it.date}|${it.medicationName}|${it.scheduledTime}" }
        .groupBy { it.medicationName }
        .mapValues { it.value.size }

    val dayCount = rangeMedicationLogs.map { it.date }.distinct().size.coerceAtLeast(1)
    val candidate = rangeMedications.maxByOrNull { med ->
        val perDay = med.times.split(",").map { it.trim() }.count { it.isNotEmpty() }
        val expected = (perDay * dayCount).coerceAtLeast(1)
        val taken = (takenByMed[med.name] ?: 0).coerceAtMost(expected)
        taken.toDouble() / expected.toDouble()
    } ?: return StableMedication("暂无", "--", 0)

    val perDay = candidate.times.split(",").map { it.trim() }.count { it.isNotEmpty() }
    val expected = (perDay * dayCount).coerceAtLeast(1)
    val taken = (takenByMed[candidate.name] ?: 0).coerceAtMost(expected)
    return StableMedication(candidate.name, candidate.dosage, calcPercentage(taken, expected))
}

private fun monthlyRates(statsByDate: Map<String, DailyMedicationStat>): List<MonthRate> {
    val grouped = statsByDate.entries.groupBy { it.key.substring(5, 7).toIntOrNull() ?: 1 }
    return (1..12).map { month ->
        val stats = grouped[month].orEmpty().map { it.value }
        val taken = stats.sumOf { it.taken }
        val expected = stats.sumOf { it.expected }
        MonthRate("${month}月", (if (expected > 0) taken.toFloat() / expected.toFloat() else 0f).coerceIn(0f, 1f))
    }
}

private fun yearlySummary(statsByDate: Map<String, DailyMedicationStat>): YearlySummary {
    val stats = statsByDate.values
    val perfectDays = stats.count { it.expected > 0 && it.taken >= it.expected }
    val avgRate = calcPercentage(stats.sumOf { it.taken }, stats.sumOf { it.expected })
    return YearlySummary(perfectDays, avgRate)
}

private fun yearlyReview(
    rangeMedicationLogs: List<MedicationLogData>,
    rangeMedications: List<MedicationData>,
    statsByDate: Map<String, DailyMedicationStat>
): YearlyReviewData {
    val takenLogs = rangeMedicationLogs
        .asSequence()
        .filter { it.status == "taken" }
        .distinctBy { "${it.date}|${it.medicationName}|${it.scheduledTime}" }
        .toList()
    val freq = takenLogs.groupingBy { it.medicationName }.eachCount()
    val top = freq.maxByOrNull { it.value }
    val streakDays = longestStreak(statsByDate)
    val firstDate = statsByDate.toSortedMap().entries.firstOrNull { it.value.expected > 0 && it.value.taken >= it.value.expected }?.key
    val lastDate = statsByDate.toSortedMap().entries.lastOrNull { it.value.expected > 0 && it.value.taken >= it.value.expected }?.key
    val streakLabel = if (firstDate != null && lastDate != null) {
        "${firstDate.substring(5)}-${lastDate.substring(5)}"
    } else {
        "无连续记录"
    }
    val expected = statsByDate.values.sumOf { it.expected }
    val taken = statsByDate.values.sumOf { it.taken }
    val totalMissed = (expected - taken).coerceAtLeast(0)
    return YearlyReviewData(
        mostFrequentName = top?.key ?: "暂无",
        mostFrequentCount = top?.value ?: 0,
        longestStreakDays = streakDays,
        streakLabel = streakLabel,
        totalMissed = totalMissed
    )
}

private fun buildHeatMapByWeek(
    statsByDate: Map<String, DailyMedicationStat>,
    orderedDates: List<String>
): List<List<Int>> {
    val dates = orderedDates.mapNotNull { parseDate(it) }
    if (dates.isEmpty()) return emptyList()

    val first = Calendar.getInstance().apply {
        time = dates.first()
        while (get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) {
            add(Calendar.DAY_OF_YEAR, -1)
        }
    }
    val last = Calendar.getInstance().apply { time = dates.last() }
    val weekMap = mutableListOf<MutableList<Int>>()
    val cursor = first.clone() as Calendar

    while (cursor.time <= last.time) {
        val week = mutableListOf<Int>()
        repeat(7) {
            val dateStr = formatDate(cursor.time)
            val stat = statsByDate[dateStr] ?: DailyMedicationStat(0, 0)
            val ratio = if (stat.expected > 0) stat.taken.toFloat() / stat.expected.toFloat() else 0f
            week.add(
                when {
                    ratio >= 0.95f -> 4
                    ratio >= 0.75f -> 3
                    ratio >= 0.5f -> 2
                    ratio > 0f -> 1
                    else -> 0
                }
            )
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        weekMap.add(week)
    }

    return weekMap
}

private fun calcPercentage(taken: Int, total: Int): Int {
    if (total <= 0) return 0
    return ((taken.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
}

private fun formatDate(date: Date): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
}

private fun parseDate(date: String): Date? {
    return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull()
}

private fun formatDisplayDate(date: Date): String {
    return SimpleDateFormat("M月d日 E", Locale.CHINA).format(date)
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, fontSize = 11.sp, color = Color(0xFF64748B))
    }
}

@Composable
private fun WeeklyScheduleCard(item: WeeklyMedicationItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(item.badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Medication, contentDescription = null, tint = Color(0xFF1E3A8A), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                    Text("${item.time} • ${item.dose}", fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.taken) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (item.taken) Color(0xFF16A34A) else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (item.taken) "已服" else "等待",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.taken) Color(0xFF16A34A) else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun AnnualSummaryCard(
    title: String,
    value: String,
    hint: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF007BFF).copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007BFF).copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(hint, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun MonthRing(month: MonthRate) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(44.dp),
                color = Color(0xFFE2E8F0),
                strokeWidth = 4.dp,
                trackColor = Color(0xFFE2E8F0)
            )
            CircularProgressIndicator(
                progress = { month.progress },
                modifier = Modifier.size(44.dp),
                color = if (month.progress == 0f) Color(0xFFBFDBFE) else Color(0xFF007BFF),
                strokeWidth = 4.dp
            )
            Text(if (month.progress == 0f) "--" else "${(month.progress * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(month.label, fontSize = 11.sp, color = Color(0xFF64748B))
    }
}

@Composable
private fun YearlyReviewRow(
    title: String,
    value: String,
    rightText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
        }
        Text(rightText, fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
    }
}

private fun heatColor(level: Int): Color {
    return when (level) {
        4 -> Color(0xFF007BFF)
        3 -> Color(0xFF007BFF).copy(alpha = 0.75f)
        2 -> Color(0xFF007BFF).copy(alpha = 0.5f)
        1 -> Color(0xFF007BFF).copy(alpha = 0.25f)
        else -> Color(0xFFE2E8F0)
    }
}

@Composable
fun MedicationTimelineItem(
    time: String,
    isLast: Boolean,
    title: String,
    statusText: String,
    isTaken: Boolean,
    description: String,
    confirmText: String,
    onMarkTaken: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isTaken) Color(0xFFF8FAFC) else Color.White)
            .border(
                if (isTaken) 1.dp else 2.dp,
                if (isTaken) Color(0xFFF1F5F9) else Color(0xFF007bff).copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        // Timeline vertical strip
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 16.dp)
        ) {
            Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isTaken) Color(0xFF94A3B8) else Color(0xFF007bff))
            if (!isLast) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.width(2.dp).height(40.dp).background(if (isTaken) Color(0xFFE2E8F0) else Color(0xFF007bff).copy(alpha = 0.2f)))
            }
        }

        // Details content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                Box(
                    modifier = Modifier
                        .background(if (isTaken) Color(0xFFDCFCE7) else Color(0xFF007bff).copy(alpha = 0.1f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTaken) Color(0xFF16A34A) else Color(0xFF007bff)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, fontSize = 14.sp, color = Color(0xFF64748B))

            if (isTaken) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Checked", tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(confirmText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF16A34A))
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onMarkTaken,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007bff))
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Mark", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("标记为已服用", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
