package com.silverlink.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
fun StressDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(TimeRange.DAY) }

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
                    val actionIcon = when (selectedRange) {
                        TimeRange.DAY, TimeRange.WEEK -> Icons.Default.MoreHoriz
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
                    TimeRange.DAY -> DaySection()
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
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF1F5F9)) {
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
private fun DaySection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Gauge Section
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前压力等级", color = Color(0xFF64748B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 24.dp))
                
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(192.dp)) {
                    CircularProgressIndicator(
                        progress = { 0.75f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF007bff),
                        strokeWidth = 12.dp,
                        trackColor = Color(0xFFF1F5F9),
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("75", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007bff))
                        Text("/ 100", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Surface(color = Color(0xFFFFF7ED), shape = RoundedCornerShape(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("高压力", color = Color(0xFFEA580C), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("您的心率变异性低于 7 天平均值。\n花点时间深呼吸一下吧。", color = Color(0xFF64748B), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }

        // Daily Timeline
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("日间分布", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("查看趋势", color = Color(0xFF007bff), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().height(128.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val heights = listOf(0.4f, 0.6f, 0.95f, 0.8f, 0.5f, 0.3f)
                    val times = listOf("8点", "10点", "12点", "14点", "16点", "18点")
                    val opacities = listOf(0.2f, 0.4f, 1f, 0.7f, 0.5f, 0.3f)
                    
                    heights.forEachIndexed { index, h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight(h).background(Color(0xFF007bff).copy(alpha = opacities[index]), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(times[index], fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }

        // Quick Relaxation
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("快速放松练习", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(top = 8.dp))
            
            ExerciseCard("盒式呼吸", "4 分钟 • 专注与平静", Icons.Default.SelfImprovement, Color(0xFF007bff), Color(0xFF007bff).copy(alpha = 0.1f))
            ExerciseCard("全身扫描", "10 分钟 • 释放身体紧张", Icons.Default.SelfImprovement, Color(0xFF4F46E5), Color(0xFF4F46E5).copy(alpha = 0.1f))
            
            // Insight Card
            Surface(color = Color(0xFF007bff).copy(alpha = 0.05f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007bff).copy(alpha = 0.1f))) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFF007bff))
                    Column {
                        Text("小贴士", color = Color(0xFF007bff), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("尝试“5-4-3-2-1”五感抽离技术，有助于快速摆脱当前的压力状态。", color = Color(0xFF334155), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本周平均", "42", " /中等", Icons.Default.TrendingDown, Color(0xFFF59E0B))
            StatCard(Modifier.weight(1f), "高压力天数", "2", "天", Icons.Default.Warning, Color(0xFFEF4444))
        }
        
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("周度压力趋势", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 16.dp))
                WeeklyStressBars()
            }
        }
        
        Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("建议反馈", fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(4.dp))
                Text("本周三和周四下午记录到较高压力。这可能与行程安排较紧凑有关，建议在那个时段预留10分钟空隙。", color = Color(0xFF475569), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MonthSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "本月平均", "38", " /偏低", Icons.Default.TrendingDown, Color(0xFF10B981))
            StatCard(Modifier.weight(1f), "放松时间", "12h", " 30m", Icons.Default.SelfImprovement, Color(0xFF007bff))
        }
        
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("月度压力分布", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Legend("低", Color(0xFF10B981))
                    Legend("中", Color(0xFFF59E0B))
                    Legend("高", Color(0xFFEF4444))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                        Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
                MonthStressGrid()
            }
        }
    }
}

@Composable
private fun YearSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "年均压力等级", "45", " /中等", Icons.Default.TrendingUp, Color(0xFFF59E0B))
            StatCard(Modifier.weight(1f), "最高月", "11月", "", Icons.Default.Warning, Color(0xFFEF4444))
        }
        
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("年度趋势分析", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 16.dp))
                YearlyStressChart()
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
private fun WeeklyStressBars() {
    val heights = listOf(0.4f, 0.5f, 0.8f, 0.9f, 0.6f, 0.3f, 0.2f)
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    
    Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        heights.forEachIndexed { index, h ->
            val color = if (h > 0.7f) Color(0xFFEF4444) else if (h > 0.4f) Color(0xFFF59E0B) else Color(0xFF10B981)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight(h).background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                Spacer(modifier = Modifier.height(8.dp))
                Text(labels[index], fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun MonthStressGrid() {
    val cells = remember {
        buildList {
            repeat(3) { add("" to Color.Transparent) }
            for (day in 1..31) {
                val color = when {
                    day in listOf(5, 12, 18, 19, 26) -> Color(0xFFEF4444)
                    day % 4 == 0 -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
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
                            .background(color, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (color == Color.Transparent) Color(0xFF94A3B8) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearlyStressChart() {
    val values = listOf(40f, 42f, 45f, 41f, 38f, 35f, 40f, 44f, 48f, 55f, 60f, 52f)
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                val minValue = 20f
                val maxValue = 80f
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()
                
                // Draw limit lines
                drawLine(Color(0xFFEF4444).copy(alpha = 0.2f), Offset(0f, size.height * 0.2f), Offset(size.width, size.height * 0.2f), 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                
                drawPath(linePath, color = Color(0xFF007bff), style = Stroke(width = 4f, cap = StrokeCap.Round))
                
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    val dotColor = if (value > 50f) Color(0xFFEF4444) else Color(0xFF007bff)
                    drawCircle(Color.White, radius = 6f, center = Offset(x, y))
                    drawCircle(dotColor, radius = 4f, center = Offset(x, y))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1月", "3月", "5月", "7月", "9月", "11月").forEach {
                Text(it, fontSize = 10.sp, color = Color(0xFF94A3B8))
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
