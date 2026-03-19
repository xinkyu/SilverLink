package com.silverlink.app.ui.history

import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.sdk.health.BodyMeasurementData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val dashboard by viewModel.healthDashboardData.collectAsState()
    val targetWeightKg by viewModel.weightTargetKg.collectAsState()
    val measurements = dashboard?.weightTimeline.orEmpty().sortedByDescending { it.timestamp }
    val latest = measurements.firstOrNull()
    val previous = measurements.getOrNull(1)
    val chartPoints = measurements.take(6).reversed()
    var selectedTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showTargetDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(chartPoints) {
        if (chartPoints.isNotEmpty() && chartPoints.none { it.timestamp == selectedTimestamp }) {
            selectedTimestamp = chartPoints.last().timestamp
        }
    }

    val selectedPoint = chartPoints.firstOrNull { it.timestamp == selectedTimestamp } ?: chartPoints.lastOrNull()

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF007BFF),
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
                    IconButton(onClick = {
                        val shareText = buildWeightShareText(measurements, targetWeightKg)
                        if (shareText.isBlank()) {
                            Toast.makeText(context, "暂无可分享的体重数据", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享体重数据"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { OverviewCards(latest = latest, previous = previous) }
            item {
                TrendSection(
                    chartPoints = chartPoints,
                    selectedPoint = selectedPoint,
                    onSelectPoint = { selectedTimestamp = it.timestamp }
                )
            }
            item {
                GoalSection(
                    latest = latest,
                    chartPoints = chartPoints,
                    targetWeightKg = targetWeightKg,
                    onSetTargetClick = { showTargetDialog = true }
                )
            }
            recentHistoryItems(
                measurements = measurements.take(5),
                onDeleteClick = { pendingDeleteTimestamp = it.timestamp }
            )
        }
    }

    if (showAddDialog) {
        AddWeightRecordDialog(
            initialWeight = latest?.weightKg,
            bmiFactor = latest?.let { if (it.weightKg > 0f && it.bmi > 0f) it.bmi / it.weightKg else 0f } ?: 0f,
            onDismiss = { showAddDialog = false },
            onConfirm = { weightKg, timestamp, bmi ->
                viewModel.addManualWeightRecord(weightKg, timestamp, bmi)
                selectedTimestamp = timestamp
                showAddDialog = false
                Toast.makeText(context, "已添加体重记录", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showTargetDialog) {
        SetWeightTargetDialog(
            initialWeight = targetWeightKg ?: latest?.weightKg,
            onDismiss = { showTargetDialog = false },
            onConfirm = { weightKg ->
                viewModel.setWeightTarget(weightKg)
                showTargetDialog = false
                Toast.makeText(context, "目标体重已更新", Toast.LENGTH_SHORT).show()
            }
        )
    }

    pendingDeleteTimestamp?.let { timestamp ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTimestamp = null },
            title = { Text("删除记录") },
            text = { Text("确认删除这条最近记录吗？删除后将不再显示。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteManualWeightRecord(timestamp)
                    pendingDeleteTimestamp = null
                    Toast.makeText(context, "记录已删除", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTimestamp = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun OverviewCards(latest: BodyMeasurementData?, previous: BodyMeasurementData?) {
    val weight = latest?.weightKg ?: 0f
    val bmi = latest?.bmi ?: 0f
    val delta = if (latest != null && previous != null) latest.weightKg - previous.weightKg else 0f

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F2FF)),
            border = BorderStroke(1.dp, Color(0xFFB3D9FF))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Scale, contentDescription = null, tint = Color(0xFF007BFF))
                    Text("当前体重", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (weight > 0f) String.format(Locale.US, "%.1f", weight) else "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), letterSpacing = (-1).sp)
                    Text("kg", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = if (delta <= 0f) Color(0xFFEF4444) else Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text(String.format(Locale.US, "%+.1fkg", delta), color = if (delta <= 0f) Color(0xFFEF4444) else Color(0xFF16A34A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B))
                    Text("当前 BMI", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                }
                Text(if (bmi > 0f) String.format(Locale.US, "%.1f", bmi) else "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), letterSpacing = (-1).sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(weightStatusColor(bmi), CircleShape))
                    Text(weightStatusLabel(bmi), color = Color(0xFF64748B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TrendSection(
    chartPoints: List<BodyMeasurementData>,
    selectedPoint: BodyMeasurementData?,
    onSelectPoint: (BodyMeasurementData) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MM/dd HH:mm", Locale.CHINA) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val weights = chartPoints.map { it.weightKg }
            val trend = if (weights.size >= 2) weights.last() - weights.first() else 0f
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("趋势", fontSize = 14.sp, color = Color(0xFF64748B))
                    Text(selectedPoint?.let { "${String.format(Locale.US, "%.1f", it.weightKg)} kg" } ?: "--", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(selectedPoint?.let { formatter.format(Date(it.timestamp)) } ?: "点击节点查看对应体重", fontSize = 12.sp, color = Color(0xFF64748B))
                }
                Surface(color = Color(0xFF007BFF).copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                    Text(String.format(Locale.US, "%+.1f kg", trend), color = Color(0xFF007BFF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize().pointerInput(chartPoints, selectedPoint) {
                        detectTapGestures { tapOffset ->
                            if (chartPoints.isEmpty()) return@detectTapGestures
                            val chartWidth = size.width - 32.dp.toPx()
                            val stepX = if (chartPoints.size <= 1) 0f else chartWidth / (chartPoints.size - 1)
                            val nearestIndex = chartPoints.indices.minByOrNull { index -> abs((16.dp.toPx() + stepX * index) - tapOffset.x) } ?: return@detectTapGestures
                            onSelectPoint(chartPoints[nearestIndex])
                        }
                    }
                ) {
                    val values = if (weights.isNotEmpty()) weights else listOf(0f, 0f)
                    val minValue = (values.minOrNull() ?: 65f) - 2f
                    val maxValue = max((values.maxOrNull() ?: 75f) + 2f, minValue + 1f)
                    val chartWidth = size.width - 32.dp.toPx()
                    val chartHeight = size.height - 32.dp.toPx()
                    val stepX = if (chartPoints.size <= 1) 0f else chartWidth / (chartPoints.size - 1)
                    val offsets = chartPoints.mapIndexed { index, point ->
                        val x = 16.dp.toPx() + stepX * index
                        val y = 16.dp.toPx() + chartHeight - ((point.weightKg - minValue) / (maxValue - minValue)) * chartHeight
                        Offset(x, y)
                    }
                    if (offsets.isNotEmpty()) {
                        val path = Path().apply { offsets.forEachIndexed { index, offset -> if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y) } }
                        val gradientPath = Path().apply {
                            addPath(path)
                            lineTo(offsets.last().x, size.height - 16.dp.toPx())
                            lineTo(offsets.first().x, size.height - 16.dp.toPx())
                            close()
                        }
                        drawPath(gradientPath, brush = Brush.verticalGradient(colors = listOf(Color(0xFF007BFF).copy(alpha = 0.28f), Color.Transparent), startY = 0f, endY = size.height))
                        drawPath(path, color = Color(0xFF007BFF), style = Stroke(width = 4f, cap = StrokeCap.Round))
                        offsets.forEachIndexed { index, offset ->
                            val selected = chartPoints[index].timestamp == selectedPoint?.timestamp
                            drawCircle(color = if (selected) Color(0xFF007BFF).copy(alpha = 0.2f) else Color.White, radius = if (selected) 13f else 10f, center = offset)
                            drawCircle(color = Color.White, radius = if (selected) 7f else 5f, center = offset)
                            drawCircle(color = Color(0xFF007BFF), radius = if (selected) 4f else 3f, center = offset)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dateFormatter = remember { SimpleDateFormat("MM/dd", Locale.CHINA) }
                    chartPoints.forEach { point ->
                        Text(dateFormatter.format(Date(point.timestamp)), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSection(
    latest: BodyMeasurementData?,
    chartPoints: List<BodyMeasurementData>,
    targetWeightKg: Float?,
    onSetTargetClick: () -> Unit
) {
    val initial = chartPoints.firstOrNull()?.weightKg ?: latest?.weightKg ?: 0f
    val current = latest?.weightKg ?: 0f
    val target = targetWeightKg
    val progress = if (target != null) calculateGoalProgress(initial, current, target) else 0f
    val remaining = if (target != null && current > 0f) abs(current - target) else null
    val reachedGoal = target != null && current > 0f && remaining == 0f

    Text("目标", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(bottom = 8.dp))
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(target?.let { "目标体重: ${String.format(Locale.US, "%.1f", it)} kg" } ?: "目标体重: 未设置", fontSize = 14.sp, color = Color(0xFF475569))
                    Text(if (target == null) "设置后可查看当前进度" else "按最近记录自动计算完成度", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
                Button(
                    onClick = onSetTargetClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF007BFF),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (target == null) "设定目标" else "调整目标")
                }
            }

            if (target != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (reachedGoal) "已达到目标" else String.format(Locale.US, "剩余 %.1fkg", remaining ?: 0f), color = if (reachedGoal) Color(0xFF16A34A) else Color(0xFF007BFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Surface(color = if (reachedGoal) Color(0xFFDCFCE7) else Color(0xFF007BFF).copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                        Text(if (target >= initial) "增重目标" else "减重目标", color = if (reachedGoal) Color(0xFF166534) else Color(0xFF007BFF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFF1F5F9), CircleShape)) {
                    Box(modifier = Modifier.fillMaxWidth(progress).height(12.dp).background(if (reachedGoal) Color(0xFF16A34A) else Color(0xFF007BFF), CircleShape))
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("初始记录", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                    Text(if (initial > 0f) String.format(Locale.US, "%.1f kg", initial) else "--", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("当前记录", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                    Text(if (current > 0f) String.format(Locale.US, "%.1f kg", current) else "--", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("目标", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
                    Text(target?.let { String.format(Locale.US, "%.1f kg", it) } ?: "--", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
            }
        }
    }
}

private fun LazyListScope.recentHistoryItems(
    measurements: List<BodyMeasurementData>,
    onDeleteClick: (BodyMeasurementData) -> Unit
) {
    item {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最近记录", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                Text("仅显示最近 5 条", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF007BFF))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val formatter = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
                measurements.forEach { measurement ->
                    HistoryItem(
                        time = formatter.format(Date(measurement.timestamp)),
                        label = measurement.bmi.takeIf { it > 0f }?.let { "BMI ${String.format(Locale.US, "%.1f", it)}" } ?: "健康同步",
                        reading = String.format(Locale.US, "%.1f kg", measurement.weightKg),
                        status = weightStatusLabel(measurement.bmi),
                        statusBg = weightStatusBackground(measurement.bmi),
                        statusText = weightStatusColor(measurement.bmi),
                        onDeleteClick = { onDeleteClick(measurement) }
                    )
                }
                if (measurements.isEmpty()) {
                    HistoryItem(
                        time = "暂无记录",
                        label = "尚未同步到体重数据",
                        reading = "--",
                        status = "待同步",
                        statusBg = Color(0xFFF1F5F9),
                        statusText = Color(0xFF64748B),
                        onDeleteClick = null
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    time: String,
    label: String,
    reading: String,
    status: String,
    statusBg: Color,
    statusText: Color,
    onDeleteClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF007BFF).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF007BFF), modifier = Modifier.size(20.dp))
            }
            Column {
                Text(time, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reading, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                if (onDeleteClick != null) {
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "删除记录", tint = Color(0xFF94A3B8))
                    }
                }
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(statusBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusText, letterSpacing = 1.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWeightRecordDialog(
    initialWeight: Float?,
    bmiFactor: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float, Long, Float) -> Unit
) {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var weightInput by rememberSaveable { mutableStateOf(initialWeight?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var selectedMillis by rememberSaveable { mutableStateOf(now.timeInMillis) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val source = Calendar.getInstance().apply { timeInMillis = selectedMillis }
                        val picked = Calendar.getInstance().apply { timeInMillis = millis }
                        source.set(Calendar.YEAR, picked.get(Calendar.YEAR))
                        source.set(Calendar.MONTH, picked.get(Calendar.MONTH))
                        source.set(Calendar.DAY_OF_MONTH, picked.get(Calendar.DAY_OF_MONTH))
                        selectedMillis = source.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加体重记录", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = sanitizeDecimalInput(it) },
                    label = { Text("体重 (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Surface(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }, shape = RoundedCornerShape(12.dp), color = Color(0xFFF8FAFC)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("日期", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text(dateFormatter.format(Date(selectedMillis)), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val picker = Calendar.getInstance().apply { timeInMillis = selectedMillis }
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val updated = Calendar.getInstance().apply { timeInMillis = selectedMillis }
                                updated.set(Calendar.HOUR_OF_DAY, hour)
                                updated.set(Calendar.MINUTE, minute)
                                updated.set(Calendar.SECOND, 0)
                                updated.set(Calendar.MILLISECOND, 0)
                                selectedMillis = updated.timeInMillis
                            },
                            picker.get(Calendar.HOUR_OF_DAY),
                            picker.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("时间", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text(timeFormatter.format(Date(selectedMillis)), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val weight = weightInput.toFloatOrNull()
                if (weight == null || weight <= 0f) {
                    Toast.makeText(context, "请输入有效的体重数值", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                val bmi = if (bmiFactor > 0f) weight * bmiFactor else 0f
                onConfirm(weight, selectedMillis, bmi)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SetWeightTargetDialog(
    initialWeight: Float?,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    val context = LocalContext.current
    var weightInput by rememberSaveable { mutableStateOf(initialWeight?.let { String.format(Locale.US, "%.1f", it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设定目标体重", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = weightInput,
                onValueChange = { weightInput = sanitizeDecimalInput(it) },
                label = { Text("目标体重 (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val weight = weightInput.toFloatOrNull()
                if (weight == null || weight <= 0f) {
                    Toast.makeText(context, "请输入有效的目标体重", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onConfirm(weight)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun buildWeightShareText(measurements: List<BodyMeasurementData>, targetWeightKg: Float?): String {
    val latest = measurements.firstOrNull() ?: return ""
    val previous = measurements.getOrNull(1)
    val delta = if (previous != null) latest.weightKg - previous.weightKg else 0f
    val recent = measurements.take(5).sortedBy { it.timestamp }
    val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.CHINA)
    return buildString {
        append("体重记录分享").append('\n')
        append("当前体重: ").append(String.format(Locale.US, "%.1f kg", latest.weightKg)).append('\n')
        if (latest.bmi > 0f) append("当前 BMI: ").append(String.format(Locale.US, "%.1f", latest.bmi)).append("（").append(weightStatusLabel(latest.bmi)).append("）").append('\n')
        targetWeightKg?.let { append("目标体重: ").append(String.format(Locale.US, "%.1f kg", it)).append('\n') }
        append("较上次变化: ").append(String.format(Locale.US, "%+.1f kg", delta)).append('\n')
        append("记录时间: ").append(formatter.format(Date(latest.timestamp))).append('\n')
        append('\n').append("最近记录:").append('\n')
        recent.forEach { item -> append(formatter.format(Date(item.timestamp))).append("  ").append(String.format(Locale.US, "%.1f kg", item.weightKg)).append('\n') }
    }.trim()
}

private fun sanitizeDecimalInput(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
}

private fun calculateGoalProgress(initial: Float, current: Float, target: Float): Float {
    if (initial <= 0f || current <= 0f || target <= 0f || initial == target) return 0f
    return if (target < initial) ((initial - current) / (initial - target)).coerceIn(0f, 1f) else ((current - initial) / (target - initial)).coerceIn(0f, 1f)
}

private fun weightStatusLabel(bmi: Float): String = when {
    bmi <= 0f -> "待同步"
    bmi < 18.5f -> "偏轻"
    bmi < 24f -> "标准"
    bmi < 28f -> "偏高"
    else -> "超重"
}

private fun weightStatusColor(bmi: Float): Color = when {
    bmi <= 0f -> Color(0xFF94A3B8)
    bmi < 18.5f -> Color(0xFFF59E0B)
    bmi < 24f -> Color(0xFF22C55E)
    bmi < 28f -> Color(0xFFFB923C)
    else -> Color(0xFFEF4444)
}

private fun weightStatusBackground(bmi: Float): Color = when {
    bmi <= 0f -> Color(0xFFF1F5F9)
    bmi < 18.5f -> Color(0xFFFFF7ED)
    bmi < 24f -> Color(0xFFECFDF5)
    bmi < 28f -> Color(0xFFFFF7ED)
    else -> Color(0xFFFEE2E2)
}
