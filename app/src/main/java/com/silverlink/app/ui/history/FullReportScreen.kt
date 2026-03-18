package com.silverlink.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.InsertChartOutlined
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    val medicationSummary by viewModel.medicationSummary.collectAsState()
    
    val steps = healthDashboardData?.steps ?: 0
    val calories = healthDashboardData?.calories ?: 0
    val activeMinutes = healthDashboardData?.moveMinutes ?: 0
    val sleepMinutes = healthDashboardData?.sleepMinutes ?: 0
    val heartRate = healthDashboardData?.latestHeartRate ?: 60
    val bloodOxygen = healthDashboardData?.bloodOxygen ?: 98
    
    val todayLabel = remember { SimpleDateFormat("M月d日", Locale.CHINA).format(Date()) }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            TopAppBar(
                title = { Text("完整健康报告", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color(0xFF1E293B)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color(0xFF475569))
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color(0xFF475569))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { /* TODO Download PDF */ },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007bff))
                    ) {
                        Text("下载报告 PDF", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = { /* Share */ },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color(0xFF475569))
                    }
                }
            }
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
                ReportPeriodSelector(todayLabel)
            }
            item {
                ActivitySummarySection(steps = steps, calories = calories)
            }
            item {
                SleepAnalysisSection(sleepMinutes = sleepMinutes)
            }
            item {
                HeartRateTrendSection(heartRate = heartRate)
            }
            item {
                SpO2AndStressSection(bloodOxygen = bloodOxygen)
            }
            item {
                HealthInsightsSection()
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReportPeriodSelector(dateLabel: String) {
    Surface(
        color = Color(0xFF007bff).copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007bff).copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("当前查看", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF007bff), letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${dateLabel}健康报告", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, Color(0xFFF1F5F9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF007bff), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ActivitySummarySection(steps: Int, calories: Int) {
    val progress = (steps.toFloat() / 8000f).coerceIn(0f, 1f)
    val displayPercent = (progress * 100).toInt()
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF8FAFC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp, 20.dp).background(Color(0xFFFB923C), RoundedCornerShape(4.dp)))
                    Text("活动总结", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), fontSize = 16.sp)
                }
                Text("今日步数", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF94A3B8))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        color = Color(0xFFF1F5F9),
                        strokeWidth = 8.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { progress },
                        color = Color(0xFF007bff),
                        strokeWidth = 8.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    Text("${displayPercent}%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("总步数", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$steps", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), letterSpacing = (-0.5).sp)
                            Text(" 步", fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    Column {
                        Text("热量消耗", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$calories", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                            Text(" kcal", fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepAnalysisSection(sleepMinutes: Int) {
    val hours = sleepMinutes / 60
    val minutes = sleepMinutes % 60
    
    val dHours = if (sleepMinutes > 0) hours else 7
    val dMins = if (sleepMinutes > 0) minutes else 12

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF8FAFC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp, 20.dp).background(Color(0xFF6366F1), RoundedCornerShape(4.dp)))
                    Text("睡眠分析", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), fontSize = 16.sp)
                }
                Surface(color = Color(0xFFEEF2FF), shape = RoundedCornerShape(12.dp)) {
                    Text("良好", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6366F1), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(modifier = Modifier.weight(1f), color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("昨夜时长", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$dHours", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Text("时 ", fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 2.dp))
                            Text("$dMins", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Text("分", fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                }
                Surface(modifier = Modifier.weight(1f), color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("质量评分", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("88", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Text(" / 100", fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartRateTrendSection(heartRate: Int) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF8FAFC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp, 20.dp).background(Color(0xFFEF4444), RoundedCornerShape(4.dp)))
                    Text("心率趋势", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), fontSize = 16.sp)
                }
                Text("静息平均: $heartRate bpm", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
            
            // Mini Chart Placeholder
            Row(
                modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val fractions = listOf(0.5f, 0.75f, 0.66f, 0.8f, 1.0f, 0.75f, 0.5f)
                val colors = listOf(Color(0xFFFEE2E2), Color(0xFFFECACA), Color(0xFFF87171), Color(0xFFFCA5A5), Color(0xFFEF4444), Color(0xFFFCA5A5), Color(0xFFF87171))
                
                fractions.forEachIndexed { index, fraction ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .fillMaxHeight(fraction)
                            .background(colors[index], RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun SpO2AndStressSection(bloodOxygen: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF8FAFC)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("血氧饱和度", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.padding(bottom = 8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$bloodOxygen", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007bff))
                    Text("%", fontSize = 14.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("状态：极佳", fontSize = 10.sp, color = Color(0xFF22C55E), modifier = Modifier.padding(top = 4.dp))
            }
        }
        
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF8FAFC)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("平均压力值", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.padding(bottom = 8.dp))
                Text("32", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                Text("状态：轻松", fontSize = 10.sp, color = Color(0xFF3B82F6), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun HealthInsightsSection() {
    Surface(
        color = Color(0xFF007bff).copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007bff).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Box(
                    modifier = Modifier.size(28.dp).background(Color(0xFF007bff), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI健康洞察", fontWeight = FontWeight.Bold, color = Color(0xFF007bff))
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("您的平均静息心率保持在理想范围内。建议您在本月增加两次高强度间歇训练（HIIT）以进一步提升心肺功能。", fontSize = 14.sp, color = Color(0xFF334155), lineHeight = 22.sp)
                Text("最近一周睡眠规律性有所下降，建议每晚23:00前入睡以改善恢复质量。", fontSize = 14.sp, color = Color(0xFF334155), lineHeight = 22.sp)
            }
        }
    }
}
