package com.silverlink.app.ui.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.sdk.health.BloodPressureData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodPressureDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val dashboardData by viewModel.healthDashboardData.collectAsState()
    val canEdit = viewModel.canEditHealthMetrics
    val readings = dashboardData?.bloodPressureTimeline.orEmpty().sortedByDescending { it.timestamp }
    val latest = readings.firstOrNull()
    val chartReadings = readings.take(4).reversed()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }

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
                    IconButton(
                        onClick = {
                            val summary = buildBloodPressureShareText(readings)
                            if (summary.isBlank()) {
                                Toast.makeText(context, "暂无可分享的血压数据", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, summary)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享血压详情"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color(0xFF64748B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF007BFF),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增血压记录")
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
                HeroStats(latest = latest)
            }
            item {
                StatusCard(
                    latest = latest,
                    onInsightClick = {
                        Toast.makeText(
                            context,
                            latest?.let { "当前状态：${bloodPressureStatus(it).description}" } ?: "暂无血压洞察可查看",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
            item {
                ChartSection(readings = chartReadings)
            }
            recentHistoryItems(
                readings = readings.take(5),
                onDeleteClick = { if (canEdit) pendingDeleteTimestamp = it.timestamp }
            )
        }
    }

    if (showAddDialog && canEdit) {
        AddBloodPressureRecordDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { systolic, diastolic ->
                viewModel.addManualBloodPressureRecord(
                    systolic = systolic,
                    diastolic = diastolic,
                    timestamp = System.currentTimeMillis()
                )
                showAddDialog = false
                Toast.makeText(context, "已添加血压记录", Toast.LENGTH_SHORT).show()
            }
        )
    }

    pendingDeleteTimestamp?.takeIf { canEdit }?.let { timestamp ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTimestamp = null },
            title = { Text("删除记录") },
            text = { Text("确认删除这条最近记录吗？删除后将不再显示。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteManualBloodPressureRecord(timestamp)
                        pendingDeleteTimestamp = null
                        Toast.makeText(context, "记录已删除", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("删除", color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTimestamp = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HeroStats(latest: BloodPressureData?) {
    val systolic = latest?.systolic ?: 0
    val diastolic = latest?.diastolic ?: 0

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF007BFF).copy(alpha = 0.1f))
                .border(1.dp, Color(0xFF007BFF).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF007BFF), modifier = Modifier.size(16.dp))
                Text("收缩压", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007BFF), letterSpacing = 1.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (systolic > 0) systolic.toString() else "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("mmHg", fontSize = 14.sp, color = Color(0xFF64748B))
            }
        }

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
                Text(if (diastolic > 0) diastolic.toString() else "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("mmHg", fontSize = 14.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun StatusCard(
    latest: BloodPressureData?,
    onInsightClick: () -> Unit
) {
    val status = bloodPressureStatus(latest)
    val bgColor = if (status.isNormal) Color(0xFFECFDF5) else Color(0xFFFFF7ED)
    val borderColor = if (status.isNormal) Color(0xFFD1FAE5) else Color(0xFFFED7AA)
    val accentColor = if (status.isNormal) Color(0xFF10B981) else Color(0xFFF59E0B)
    val titleColor = if (status.isNormal) Color(0xFF064E3B) else Color(0xFF9A3412)
    val bodyColor = if (status.isNormal) Color(0xFF047857) else Color(0xFFC2410C)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
            }
            Column {
                Text("状态 ${status.label}", color = titleColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(status.description, color = bodyColor, fontSize = 14.sp)
            }
        }
        Button(
            onClick = onInsightClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.White
            )
        ) {
            Text("查看健康洞察", fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun ChartSection(readings: List<BloodPressureData>) {
    var selectedIndex by remember(readings) {
        mutableStateOf(readings.lastIndex.coerceAtLeast(0))
    }
    val selectedReading = readings.getOrNull(selectedIndex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            val averageSystolic = readings.map { it.systolic }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
            val averageDiastolic = readings.map { it.diastolic }.average().takeIf { !it.isNaN() }?.toInt() ?: 0

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("近期趋势", fontSize = 14.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    Text(
                        if (averageSystolic > 0 && averageDiastolic > 0) "$averageSystolic/$averageDiastolic" else "--",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Text("${readings.size}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("最近记录数", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))

            selectedReading?.let { reading ->
                val formatter = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
                Text(
                    text = "${formatter.format(Date(reading.timestamp))} ${reading.systolic}/${reading.diastolic} mmHg",
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            }

            val values = if (readings.isNotEmpty()) readings.map { it.systolic.toFloat() } else listOf(0f, 0f, 0f, 0f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val validValues = values.filter { it > 0f }
                    val minValue = (validValues.minOrNull() ?: 100f) - 10f
                    val maxValue = (validValues.maxOrNull() ?: 140f) + 10f
                    val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                    val linePath = Path()

                    values.forEachIndexed { index, value ->
                        val x = stepX * index
                        val ratio = if (maxValue > minValue) (value - minValue) / (maxValue - minValue) else 0f
                        val y = size.height - ratio * size.height
                        if (index == 0) {
                            linePath.moveTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                        }
                    }

                    val bgPath = Path().apply {
                        addPath(linePath)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path = bgPath, color = Color(0xFF007BFF).copy(alpha = 0.1f))
                    drawPath(linePath, color = Color(0xFF007BFF), style = Stroke(width = 6f, cap = StrokeCap.Round))

                    values.forEachIndexed { index, value ->
                        val x = stepX * index
                        val ratio = if (maxValue > minValue) (value - minValue) / (maxValue - minValue) else 0f
                        val y = size.height - ratio * size.height
                        val isSelected = index == selectedIndex
                        drawCircle(Color.White, radius = if (isSelected) 14f else 12f, center = Offset(x, y))
                        drawCircle(
                            color = if (isSelected) Color(0xFF0EA5E9) else Color(0xFF007BFF),
                            radius = if (isSelected) 9f else 8f,
                            center = Offset(x, y)
                        )
                    }
                }

                if (readings.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        readings.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { selectedIndex = index }
                            )
                        }
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val formatter = SimpleDateFormat("MM/dd", Locale.CHINA)
                (if (readings.isNotEmpty()) readings else List(4) { null }).forEach { point ->
                    Text(point?.let { formatter.format(Date(it.timestamp)) } ?: "--", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun LazyListScope.recentHistoryItems(
    readings: List<BloodPressureData>,
    onDeleteClick: (BloodPressureData) -> Unit
) {
    item {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最近记录", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                Text("仅显示最近 5 条", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF007BFF))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                readings.forEach { reading ->
                    val formatter = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
                    val status = bloodPressureStatus(reading)
                    HistoryItem(
                        time = formatter.format(Date(reading.timestamp)),
                        label = "健康同步",
                        reading = "${reading.systolic}/${reading.diastolic}",
                        status = status.label,
                        statusBg = status.background,
                        statusText = status.textColor,
                        onDeleteClick = { onDeleteClick(reading) }
                    )
                }
                if (readings.isEmpty()) {
                    HistoryItem(
                        time = "暂无记录",
                        label = "尚未同步到血压数据",
                        reading = "--/--",
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF007BFF).copy(alpha = 0.1f)),
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
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(28.dp)
                    ) {
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

@Composable
private fun AddBloodPressureRecordDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    var systolicText by rememberSaveable { mutableStateOf("") }
    var diastolicText by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增血压记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = systolicText,
                    onValueChange = { systolicText = it.filter(Char::isDigit).take(3) },
                    label = { Text("收缩压") },
                    suffix = { Text("mmHg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = diastolicText,
                    onValueChange = { diastolicText = it.filter(Char::isDigit).take(3) },
                    label = { Text("舒张压") },
                    suffix = { Text("mmHg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val systolic = systolicText.toIntOrNull()
                    val diastolic = diastolicText.toIntOrNull()
                    if (systolic == null || diastolic == null || systolic <= 0 || diastolic <= 0) {
                        Toast.makeText(context, "请输入有效的血压数值", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (systolic <= diastolic) {
                        Toast.makeText(context, "收缩压应大于舒张压", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    onConfirm(systolic, diastolic)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private data class BloodPressureStatus(
    val label: String,
    val description: String,
    val background: Color,
    val textColor: Color,
    val isNormal: Boolean
)

private fun bloodPressureStatus(reading: BloodPressureData?): BloodPressureStatus {
    val systolic = reading?.systolic ?: 0
    val diastolic = reading?.diastolic ?: 0
    return when {
        systolic <= 0 || diastolic <= 0 -> BloodPressureStatus(
            label = "待同步",
            description = "还没有读取到最新的血压数据。",
            background = Color(0xFFF1F5F9),
            textColor = Color(0xFF475569),
            isNormal = false
        )
        systolic < 120 && diastolic < 80 -> BloodPressureStatus(
            label = "正常",
            description = "您的血压读数处于健康范围。",
            background = Color(0xFFD1FAE5),
            textColor = Color(0xFF064E3B),
            isNormal = true
        )
        systolic < 140 && diastolic < 90 -> BloodPressureStatus(
            label = "偏高",
            description = "血压略高，建议继续观察近期趋势。",
            background = Color(0xFFFEF3C7),
            textColor = Color(0xFF92400E),
            isNormal = false
        )
        else -> BloodPressureStatus(
            label = "过高",
            description = "血压高于建议范围，建议结合医生建议持续关注。",
            background = Color(0xFFFEE2E2),
            textColor = Color(0xFF991B1B),
            isNormal = false
        )
    }
}

private fun buildBloodPressureShareText(readings: List<BloodPressureData>): String {
    val latest = readings.firstOrNull() ?: return ""
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    val recent = readings.take(5)
    val avgSystolic = recent.map { it.systolic }.average().takeIf { !it.isNaN() }?.toInt() ?: latest.systolic
    val avgDiastolic = recent.map { it.diastolic }.average().takeIf { !it.isNaN() }?.toInt() ?: latest.diastolic
    return buildString {
        appendLine("血压详情")
        appendLine("最新记录：${latest.systolic}/${latest.diastolic} mmHg")
        appendLine("记录时间：${formatter.format(Date(latest.timestamp))}")
        appendLine("状态：${bloodPressureStatus(latest).label}")
        appendLine("最近${recent.size}条平均：$avgSystolic/$avgDiastolic mmHg")
    }.trim()
}
