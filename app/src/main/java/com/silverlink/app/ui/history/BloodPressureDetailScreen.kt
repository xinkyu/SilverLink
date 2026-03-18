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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodPressureDetailScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = { Text("血压详情", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color(0xFF64748B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {},
                containerColor = Color(0xFF007bff),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加记录")
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
                HeroStats()
            }
            item {
                StatusCard()
            }
            item {
                ChartSection()
            }
            item {
                HistoryList()
            }
        }
    }
}

@Composable
private fun HeroStats() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Systolic
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF007bff).copy(alpha = 0.1f))
                .border(1.dp, Color(0xFF007bff).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF007bff), modifier = Modifier.size(16.dp))
                Text("收缩压", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007bff), letterSpacing = 1.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("118", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("mmHg", fontSize = 14.sp, color = Color(0xFF64748B))
            }
        }

        // Diastolic
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                Text("舒张压", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B), letterSpacing = 1.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("76", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("mmHg", fontSize = 14.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun StatusCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFECFDF5))
            .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
            }
            Column {
                Text("状态: 正常", color = Color(0xFF064E3B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("您的血压读数在健康范围内。", color = Color(0xFF047857), fontSize = 14.sp)
            }
        }
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text("查看健康洞察", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChartSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("月度趋势", fontSize = 14.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    Text("平均 118/78", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Text("2.4%", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("相较上月", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chart Canvas
            val values = listOf(125f, 120f, 115f, 118f)
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val minValue = 100f
                val maxValue = 140f
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                val linePath = Path()
                
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    if (index == 0) {
                        linePath.moveTo(x, y)
                    } else {
                        // Create smooth curve if desired, here just lines for simplicity matching Stitch SVG
                        linePath.lineTo(x, y)
                    }
                }
                
                // Draw fill below the line
                val bgPath = Path().apply {
                    addPath(linePath)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    path = bgPath,
                    color = Color(0xFF007bff).copy(alpha = 0.1f)
                )

                // Draw line
                drawPath(linePath, color = Color(0xFF007bff), style = Stroke(width = 6f, cap = StrokeCap.Round))
                
                // Draw dots
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val ratio = (value - minValue) / (maxValue - minValue)
                    val y = size.height - ratio * size.height
                    drawCircle(Color.White, radius = 12f, center = Offset(x, y))
                    drawCircle(Color(0xFF007bff), radius = 8f, center = Offset(x, y))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("第1周", "第2周", "第3周", "第4周").forEach {
                    Text(it, fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryList() {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("最近记录", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
            Text("查看全部", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF007bff), modifier = Modifier.clickable {  })
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HistoryItem("10月24日, 08:30 AM", "晨间测量", "120/82", "正常", Color(0xFF10B981), Color(0xFFD1FAE5), Color(0xFF064E3B))
            HistoryItem("10月23日, 09:15 PM", "晚间测量", "135/88", "偏高", Color(0xFFF59E0B), Color(0xFFFEF3C7), Color(0xFF92400E))
            HistoryItem("10月22日, 08:05 AM", "晨间测量", "116/75", "正常", Color(0xFF10B981), Color(0xFFD1FAE5), Color(0xFF064E3B))
        }
    }
}

@Composable
private fun HistoryItem(time: String, label: String, reading: String, status: String, statusColor: Color, statusBg: Color, statusText: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF007bff).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF007bff), modifier = Modifier.size(20.dp))
            }
            Column {
                Text(time, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(reading, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
            Box(modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(statusBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusText, letterSpacing = 1.sp)
            }
        }
    }
}
