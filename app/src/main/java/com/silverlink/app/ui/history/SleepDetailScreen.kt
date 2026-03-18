package com.silverlink.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.feature.health.OppoHealthDashboardData
import com.silverlink.app.ui.components.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(TimeRange.DAY) }

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
                    TimeRange.DAY -> DaySection(healthDashboardData)
                    TimeRange.WEEK -> WeekSection()
                    TimeRange.MONTH -> MonthSection()
                    TimeRange.YEAR -> YearSection()
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
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFEFF3F8)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (range, label) ->
                val selected = range == selectedRange
                Surface(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) Color.White else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
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
private fun DaySection(data: OppoHealthDashboardData?) {
    val sleepMinutes = data?.sleepMinutes ?: 0
    val hours = sleepMinutes / 60
    val minutes = sleepMinutes % 60
    
    // Fallbacks if data is zero/missing, for presentation matching the Stitch designs
    val displayHours = if (sleepMinutes > 0) hours else 7
    val displayMins = if (sleepMinutes > 0) minutes else 45

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF007bff), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("${displayHours}h ${displayMins}m", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("Total Duration (Last Night)", color = Color(0xFF64748B), fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Efficiency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text("92%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007bff))
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color(0xFFE2E8F0)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Debt", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text("-15m", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                }
            }
        }
        
        SectionCard("Sleep Stages") {
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
                        progress = { 0.8f },
                        color = Color(0xFF007BFF), // Deep
                        strokeWidth = 12.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { 0.5f },
                        color = Color(0xFF66B2FF), // Light
                        strokeWidth = 12.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("88", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("SCORE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    }
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    StageLegend("Deep", "1h 56m", Color(0xFF007BFF))
                    StageLegend("Light", "3h 52m", Color(0xFF66B2FF))
                    StageLegend("REM", "1h 33m", Color(0xFFB2D8FF))
                    StageLegend("Awake", "24m", Color(0xFFE2E8F0))
                }
            }
        }
        
        SectionCard("Sleep Score History", "Last 7 Days") {
            WeeklyScores()
        }
        
        InsightCard("Consistency is Key", "You went to bed 30 minutes later than your average. Try setting a wind-down alarm for 10:00 PM tonight.", true)
    }
}

@Composable
private fun WeekSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本周平均时长", "7小时45分", "", Icons.Default.TrendingUp, Color(0xFF10B981))
            StatCard(Modifier.weight(1f), "平均评分", "88分", "", Icons.Default.TrendingDown, Color(0xFFEF4444))
        }
        SectionCard("睡眠趋势") {
            WeeklySleepBars()
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Legend("深睡", Color(0xFF1E3A8A))
                Spacer(modifier = Modifier.width(16.dp))
                Legend("浅睡", Color(0xFF3B82F6))
                Spacer(modifier = Modifier.width(16.dp))
                Legend("快速眼动", Color(0xFF93C5FD))
            }
        }
        
        SectionCard("睡眠质量分布") {
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
                        progress = { 0.7f },
                        color = Color(0xFF007BFF),
                        strokeWidth = 8.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    Text("70%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    QualityBar("优 (5天)", "70%", 0.7f, Color(0xFF22C55E))
                    QualityBar("良 (2天)", "30%", 0.3f, Color(0xFFEAB308))
                }
            }
        }
        
        InsightCard("个性化睡眠洞察", "本周你的深睡比例有所提升。周三入睡时间偏晚导致次日效率下降，建议保持更规律的作息，在睡前1小时减少电子设备使用。", false)
    }
}

@Composable
private fun MonthSection() {
    val monthLabel = remember { SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date()) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本月达标天数", "21", "天", Icons.Default.Bedtime, Color(0xFF007BFF))
            StatCard(Modifier.weight(1f), "平均入睡时间", "23:15", "", Icons.Default.CalendarToday, Color(0xFF007BFF))
        }
        SectionCard(monthLabel) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Legend("不佳", Color(0xFFE2E8F0))
                Legend("良好", Color(0xFF93C5FD))
                Legend("优异", Color(0xFF007BFF))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            MonthSleepGrid()
        }
        InsightCard("月度分析", "当月睡眠达标率为 70%。月历热力图展现每天睡眠质量得分，颜色越深代表得分越高。", false)
    }
}

@Composable
private fun YearSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "年均睡眠时长", "7h 10m", "", Icons.Default.Bedtime, Color(0xFF007BFF))
            StatCard(Modifier.weight(1f), "最佳睡眠月", "4月", "", Icons.Default.Info, Color(0xFF10B981))
        }
        SectionCard("月均睡眠时长", "1月 - 12月") {
            YearSleepChart()
        }
        InsightCard("年度趋势", "数据显示您的春季睡眠质量最优，冬季入睡时间普遍偏晚。建议在冬季调整作息。", false)
    }
}

@Composable
private fun StageLegend(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
        }
        Text(value, fontSize = 14.sp, color = Color(0xFF64748B))
    }
}

@Composable
private fun QualityBar(label: String, value: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = Color(0xFF64748B))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(Color(0xFFE2E8F0), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
            drawRoundRect(color, size = Size(size.width * progress, size.height), cornerRadius = CornerRadius(size.height / 2, size.height / 2))
        }
    }
}

@Composable
private fun WeeklyScores() {
    val scores = listOf(0.7f, 0.85f, 0.6f, 0.9f, 0.75f, 0.4f, 0.8f)
    Row(modifier = Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        scores.forEachIndexed { index, value ->
            val isToday = index == 3
            val barColor = if (isToday) Color(0xFF007BFF) else Color(0xFFE2E8F0)
            val textColor = if (isToday) Color(0xFF007BFF) else Color(0xFF94A3B8)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp * value)
                        .background(barColor, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[index], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
        }
    }
}

@Composable
private fun WeeklySleepBars() {
    // Proportions: Deep, Light, REM
    val data = listOf(
        listOf(0.30f, 0.40f, 0.15f),
        listOf(0.35f, 0.35f, 0.20f),
        listOf(0.25f, 0.45f, 0.15f),
        listOf(0.40f, 0.30f, 0.25f),
        listOf(0.45f, 0.35f, 0.15f),
        listOf(0.30f, 0.40f, 0.20f),
        listOf(0.38f, 0.32f, 0.18f),
    )
    Row(modifier = Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        data.forEachIndexed { index, composition ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val isToday = index == 4
                    val color1 = if (isToday) Color(0xFF007BFF) else Color(0xFF1E3A8A)
                    val color2 = if (isToday) Color(0x99007BFF) else Color(0xFF3B82F6)
                    val color3 = if (isToday) Color(0x4D007BFF) else Color(0xFF93C5FD)
                    
                    Column(verticalArrangement = Arrangement.Bottom) {
                        Box(modifier = Modifier.fillMaxWidth().height(170.dp * composition[2]).background(color3, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                        Box(modifier = Modifier.fillMaxWidth().height(170.dp * composition[1]).background(color2))
                        Box(modifier = Modifier.fillMaxWidth().height(170.dp * composition[0]).background(color1, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val isToday = index == 4
                Text(listOf("周一", "周二", "周三", "周四", "今日", "周六", "周日")[index], fontSize = 11.sp, color = if (isToday) Color(0xFF007BFF) else Color(0xFF94A3B8), fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MonthSleepGrid() {
    val cells = remember {
        buildList {
            repeat(5) { add("" to Color.Transparent) }
            for (day in 1..30) {
                val color = when {
                    day % 5 == 0 -> Color(0xFF007BFF)
                    day % 2 == 0 -> Color(0xFF93C5FD)
                    else -> Color(0xFFF1F5F9)
                }
                add(day.toString() to color)
            }
            while (size % 7 != 0) add("" to Color.Transparent)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, color) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(color, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (color == Color.Transparent || color == Color(0xFFF1F5F9)) Color(0xFF94A3B8) else Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearSleepChart() {
    val values = listOf(7.2f, 7.5f, 6.8f, 7.8f, 7.0f, 6.5f, 6.8f, 7.2f, 7.4f, 7.1f, 6.9f, 6.5f)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val minValue = 5.0f
                val maxValue = 9.0f
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
                }
                drawPath(linePath, color = Color(0xFF007BFF), style = Stroke(width = 6f, cap = StrokeCap.Round))
                
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    drawCircle(Color.White, radius = 12f, center = androidx.compose.ui.geometry.Offset(x, y))
                    drawCircle(Color(0xFF007BFF), radius = 8f, center = androidx.compose.ui.geometry.Offset(x, y))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("1月", "3月", "5月", "7月", "9月", "11月").forEach {
                    Text(it, fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
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
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
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
private fun InsightCard(title: String, body: String, highlight: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlight) Color(0xFFEFF6FF) else Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(if (highlight) Color.White else Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
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
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}
