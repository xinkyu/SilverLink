package com.silverlink.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO */ },
                containerColor = Color(0xFF007bff),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加记录")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("体重详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "更多")
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Current Weight
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F2FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB3D9FF))
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Scale, contentDescription = null, tint = Color(0xFF007bff))
                                Text("当前体重", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                            }
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("72.5", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), letterSpacing = (-1).sp)
                                Text("kg", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Text("-0.5kg", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Current BMI
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B))
                                Text("当前BMI", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                            }
                            Text("23.4", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), letterSpacing = (-1).sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                                Text("标准", color = Color(0xFF64748B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Progress Chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("趋势", fontSize = 14.sp, color = Color(0xFF64748B))
                                Text("-4.2 kg", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                            Surface(color = Color(0xFF007bff).copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                                Text("近三个月", color = Color(0xFF007bff), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                val values = listOf(76.7f, 75.8f, 74.5f, 73.2f, 73.0f, 72.5f)
                                val minValue = 70f
                                val maxValue = 80f
                                val stepX = size.width / (values.size - 1)
                                
                                val path = Path()
                                values.forEachIndexed { index, value ->
                                    val x = stepX * index
                                    val y = size.height - ((value - minValue) / (maxValue - minValue)) * size.height
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                
                                val gradientPath = Path().apply {
                                    addPath(path)
                                    lineTo(size.width, size.height)
                                    lineTo(0f, size.height)
                                    close()
                                }
                                
                                drawPath(
                                    gradientPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF007bff).copy(alpha = 0.3f), Color.Transparent),
                                        startY = 0f,
                                        endY = size.height
                                    )
                                )
                                drawPath(path, color = Color(0xFF007bff), style = Stroke(width = 4f, cap = StrokeCap.Round))
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("1月", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                Text("2月", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                Text("3月", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF007bff))
                            }
                        }
                    }
                }
            }

            // Goal Tracking
            item {
                Text("目标", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("目标体重: 68.0 kg", fontSize = 14.sp, color = Color(0xFF475569))
                            Surface(color = Color(0xFF007bff).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                Text("剩余 4.5kg", color = Color(0xFF007bff), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        
                        // Progress Bar
                        Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFF1F5F9), CircleShape)) {
                            Box(modifier = Modifier.fillMaxWidth(0.65f).height(12.dp).background(Color(0xFF007bff), CircleShape))
                        }
                        
                        Divider(color = Color(0xFFF1F5F9))
                        
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("初始记录", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                                Text("76.7 kg", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("预计达成", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                                Text("2026/05/15", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                        }
                    }
                }
            }

            // History
            item {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("最近记录", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    TextButton(onClick = {}) { Text("全部", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007bff)) }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RecordItem("今天", "08:30", "72.5 kg", "-0.5kg", Color(0xFFEF4444), 1f)
                    RecordItem("昨天", "08:15", "73.0 kg", "±0.0kg", Color(0xFF94A3B8), 0.8f)
                    RecordItem("前天", "08:00", "73.0 kg", "-0.2kg", Color(0xFFEF4444), 0.7f)
                }
            }
        }
    }
}

@Composable
private fun RecordItem(date: String, time: String, weight: String, change: String, changeColor: Color, alpha: Float) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC).copy(alpha = alpha),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(date, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A).copy(alpha = alpha))
                    Text(time, fontSize = 12.sp, color = Color(0xFF64748B).copy(alpha = alpha))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(weight, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A).copy(alpha = alpha))
                Text(change, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = changeColor)
            }
        }
    }
}
