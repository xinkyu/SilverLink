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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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
fun BloodOxygenDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(TimeRange.DAY) }

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
    val bloodOxygen = data?.bloodOxygen ?: 98

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("${bloodOxygen}%", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("最新血氧饱和度", color = Color(0xFF64748B), fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今日均值", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text("98%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color(0xFFE2E8F0)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("范围", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text("95-100%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
            }
        }
        
        SectionCard("今日血氧趋势图") {
            DailyBloodOxygenLineChart()
        }
        
        SectionCard("血氧分布") {
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
                        progress = { 0.85f },
                        color = Color(0xFF10B981), // Normal
                        strokeWidth = 12.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("正常", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("状态", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    }
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    StageLegend("正常(95-100%)", "85%", Color(0xFF10B981))
                    StageLegend("偏低(90-94%)", "15%", Color(0xFFF59E0B))
                    StageLegend("警告(<90%)", "0%", Color(0xFFEF4444))
                }
            }
        }
        
        InsightCard("血氧饱和度提示", "您的血氧饱和度在过去24小时内保持在正常范围内（95%及以上）。继续保持良好的呼吸和运动习惯！", true)
    }
}

@Composable
private fun WeekSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本周平均", "97%", "", Icons.Default.TrendingUp, Color(0xFF10B981))
            StatCard(Modifier.weight(1f), "异常次数", "2", "次", Icons.Default.Warning, Color(0xFFF59E0B))
        }
        
        SectionCard("周度血氧趋势") {
            WeeklyBloodOxygenLineChart()
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Legend("最高/最低范围", Color(0xFF86EFAC))
                Spacer(modifier = Modifier.width(16.dp))
                Legend("平均值", Color(0xFF10B981))
            }
        }
        
        InsightCard("个性化洞察", "本周有2个夜晚监测到血氧出现短暂降至93%的情况，可能与睡眠姿势有关。若频繁出现，建议咨询医生。", false)
    }
}

@Composable
private fun MonthSection() {
    val monthLabel = remember { SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date()) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本月平均", "98%", "", Icons.Default.Favorite, Color(0xFF10B981))
            StatCard(Modifier.weight(1f), "正常天数", "28", "天", Icons.Default.CalendarToday, Color(0xFF007BFF))
        }
        SectionCard("$monthLabel 概况") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Legend("<90% (警惕)", Color(0xFFEF4444))
                Legend("90-94% (关注)", Color(0xFFF59E0B))
                Legend(">94% (正常)", Color(0xFF10B981))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            MonthBloodOxygenGrid()
        }
        InsightCard("月度分析", "整体来说，您本月的血氧状况非常稳定，93%的天数均在正常健康范围内。", false)
    }
}

@Composable
private fun YearSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "年均血氧", "97%", "", Icons.Default.Favorite, Color(0xFF10B981))
            StatCard(Modifier.weight(1f), "最低记录", "92%", "", Icons.Default.Info, Color(0xFFF59E0B))
        }
        SectionCard("月度平均血氧趋势", "1月 - 12月") {
            YearBloodOxygenChart()
        }
        InsightCard("年度趋势", "您的血氧水平在全年保持在一个很高的水平（>96%），冬季稍有些波动，属于正常现象。", false)
    }
}

@Composable
private fun StageLegend(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
    }
}

@Composable
private fun DailyBloodOxygenLineChart() {
    val values = listOf(98f, 97f, 96f, 95f, 96f, 97f, 98f, 99f, 98f, 97f, 96f, 98f, 99f) // 0-24h
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp)) {
            val minValue = 90f
            val maxValue = 100f
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            val linePath = Path()
            
            // Draw limit lines
            drawLine(Color(0xFFE2E8F0), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 2f)
            drawLine(Color(0xFFE2E8F0), androidx.compose.ui.geometry.Offset(0f, size.height/2), androidx.compose.ui.geometry.Offset(size.width, size.height/2), 2f)
            drawLine(Color(0xFFEF4444), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            
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
            drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 6f, cap = StrokeCap.Round))
            
            values.forEachIndexed { index, value ->
                val x = stepX * index
                val ratio = (value - minValue) / (maxValue - minValue)
                val y = size.height - ratio * size.height
                drawCircle(Color.White, radius = 9f, center = androidx.compose.ui.geometry.Offset(x, y))
                drawCircle(Color(0xFF10B981), radius = 6f, center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach {
            Text(it, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun WeeklyBloodOxygenLineChart() {
    val avgValues = listOf(97f, 98f, 96f, 97f, 99f, 98f, 98f)
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp)) {
            val minValue = 90f
            val maxValue = 100f
            val stepX = size.width / (avgValues.size - 1).coerceAtLeast(1)
            val linePath = Path()
            
            // Draw Range Bands
            val ranges = listOf(Pair(95f, 99f), Pair(96f, 100f), Pair(93f, 98f), Pair(95f, 98f), Pair(96f, 100f), Pair(96f, 99f), Pair(97f, 100f))
            ranges.forEachIndexed { index, (min, max) ->
                val x = stepX * index
                val yMin = size.height - ((min - minValue) / (maxValue - minValue)) * size.height
                val yMax = size.height - ((max - minValue) / (maxValue - minValue)) * size.height
                drawLine(Color(0x3310B981), androidx.compose.ui.geometry.Offset(x, yMin), androidx.compose.ui.geometry.Offset(x, yMax), 16f, StrokeCap.Round)
            }
            
            avgValues.forEachIndexed { index, value ->
                val x = stepX * index
                val ratio = (value - minValue) / (maxValue - minValue)
                val y = size.height - ratio * size.height
                if (index == 0) {
                    linePath.moveTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                }
            }
            drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 6f, cap = StrokeCap.Round))
            
            avgValues.forEachIndexed { index, value ->
                val x = stepX * index
                val ratio = (value - minValue) / (maxValue - minValue)
                val y = size.height - ratio * size.height
                drawCircle(Color.White, radius = 9f, center = androidx.compose.ui.geometry.Offset(x, y))
                drawCircle(Color(0xFF10B981), radius = 6f, center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Text(it, fontSize = 11.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun MonthBloodOxygenGrid() {
    val cells = remember {
        buildList {
            repeat(5) { add("" to Color.Transparent) }
            for (day in 1..30) {
                val color = when {
                    day == 14 -> Color(0xFFEF4444) // one bad day
                    day % 7 == 0 -> Color(0xFFF59E0B) // some average days
                    else -> Color(0xFF10B981) // mostly good
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
                        Text(label, color = if (color == Color.Transparent) Color(0xFF94A3B8) else Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearBloodOxygenChart() {
    val values = listOf(98f, 97f, 98f, 99f, 98f, 97f, 96f, 97f, 98f, 98f, 97f, 96f)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val minValue = 90f
                val maxValue = 100f
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
                drawPath(linePath, color = Color(0xFF10B981), style = Stroke(width = 6f, cap = StrokeCap.Round))
                
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    drawCircle(Color.White, radius = 12f, center = androidx.compose.ui.geometry.Offset(x, y))
                    drawCircle(Color(0xFF10B981), radius = 8f, center = androidx.compose.ui.geometry.Offset(x, y))
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
        colors = CardDefaults.cardColors(containerColor = if (highlight) Color(0xFFECFDF5) else Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(if (highlight) Color.White else Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
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
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}
