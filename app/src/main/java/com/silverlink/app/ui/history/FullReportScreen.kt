package com.silverlink.app.ui.history

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.feature.health.OppoHealthDashboardData
import com.silverlink.app.ui.components.MedicationSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReportBlue = Color(0xFF007BFF)
private val ReportText = Color(0xFF1E293B)
private val ReportMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullReportBottomSheet(
    onDismissRequest: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    val medicationSummary by viewModel.medicationSummary.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val healthError by viewModel.healthError.collectAsState()

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var isExporting by rememberSaveable { mutableStateOf(false) }

    val dateLabel = remember(selectedDate) {
        SimpleDateFormat("M月d日", Locale.CHINA).format(selectedDate)
    }
    val fullDateLabel = remember(selectedDate) {
        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(selectedDate)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.Transparent,
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(32.dp),
                shadowElevation = 28.dp,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp)
                            .size(width = 44.dp, height = 5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFD6DEE8))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismissRequest, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = ReportText)
                        }
                        Text(
                            text = "完整健康报告",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ReportText
                        )
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            ReportPeriodSelector(
                                dateLabel = dateLabel,
                                onPickDate = { showDatePicker = true }
                            )
                        }
                        if (!healthError.isNullOrBlank()) {
                            item { ErrorBanner(message = healthError!!) }
                        }
                        item {
                            ActivitySummarySection(
                                steps = healthDashboardData?.steps ?: 0,
                                calories = healthDashboardData?.calories ?: 0,
                                activeMinutes = healthDashboardData?.moveMinutes ?: 0
                            )
                        }
                        item {
                            SleepAnalysisSection(
                                sleepMinutes = healthDashboardData?.sleepMinutes ?: 0,
                                sleepScore = healthDashboardData?.sleepScore ?: 0
                            )
                        }
                        item {
                            HeartRateTrendSection(
                                heartRate = healthDashboardData?.latestHeartRate ?: 0
                            )
                        }
                        item {
                            SpO2AndStressSection(
                                bloodOxygen = healthDashboardData?.bloodOxygen ?: 0,
                                pressure = healthDashboardData?.latestPressure ?: 0
                            )
                        }
                        item {
                            MedicationSummarySection(summary = medicationSummary)
                        }
                        item {
                            HealthInsightsSection(
                                data = healthDashboardData,
                                medicationSummary = medicationSummary
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.96f),
                        shadowElevation = 18.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isExporting = true
                                        val file = withContext(Dispatchers.IO) {
                                            createHealthReportPdf(
                                                context = context,
                                                dateLabel = fullDateLabel,
                                                dashboardData = healthDashboardData,
                                                medicationSummary = medicationSummary
                                            )
                                        }
                                        isExporting = false
                                        Toast.makeText(
                                            context,
                                            "报告已保存：${file.absolutePath}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                enabled = !isExporting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ReportBlue)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = "下载报告 PDF",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isExporting = true
                                        val file = withContext(Dispatchers.IO) {
                                            createHealthReportPdf(
                                                context = context,
                                                dateLabel = fullDateLabel,
                                                dashboardData = healthDashboardData,
                                                medicationSummary = medicationSummary
                                            )
                                        }
                                        isExporting = false
                                        shareHealthReportPdf(
                                            context = context,
                                            file = file,
                                            shareText = buildShareText(fullDateLabel, healthDashboardData, medicationSummary)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(14.dp))
                            ) {
                                Icon(
                                    imageVector = ShareGlyph,
                                    contentDescription = "分享报告",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { viewModel.setSelectedDate(Date(it)) }
                        showDatePicker = false
                    }
                ) {
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
}

@Composable
private fun ReportPeriodSelector(
    dateLabel: String,
    onPickDate: () -> Unit
) {
    Surface(
        color = ReportBlue.copy(alpha = 0.05f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ReportBlue.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPickDate)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "当前查看",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ReportBlue,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${dateLabel}健康报告",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReportText
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, Color(0xFFE5EEF8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "选择日期",
                    tint = ReportBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = Color(0xFFFEF2F2),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFECACA))
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = Color(0xFFB91C1C),
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun ActivitySummarySection(
    steps: Int,
    calories: Int,
    activeMinutes: Int
) {
    val progress = (steps.toFloat() / 8000f).coerceIn(0f, 1f)
    val percent = (progress * 100).toInt()

    ReportCard(title = "活动总结", accent = Color(0xFFFB923C), icon = Icons.Default.LocalFireDepartment) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    trackColor = Color.Transparent,
                    color = Color(0xFFF1F5F9),
                    strokeWidth = 8.dp,
                    modifier = Modifier.fillMaxSize()
                )
                CircularProgressIndicator(
                    progress = { progress },
                    trackColor = Color.Transparent,
                    color = ReportBlue,
                    strokeWidth = 8.dp,
                    modifier = Modifier.fillMaxSize()
                )
                Text("$percent%", fontWeight = FontWeight.Bold, color = ReportText)
            }
            Column {
                MetricLine(label = "总步数", value = if (steps > 0) "$steps 步" else "--")
                Spacer(modifier = Modifier.height(10.dp))
                MetricLine(label = "消耗热量", value = if (calories > 0) "$calories kcal" else "--")
                Spacer(modifier = Modifier.height(10.dp))
                MetricLine(label = "活跃时长", value = if (activeMinutes > 0) "$activeMinutes 分钟" else "--")
            }
        }
    }
}

@Composable
private fun SleepAnalysisSection(
    sleepMinutes: Int,
    sleepScore: Int
) {
    val hours = sleepMinutes / 60
    val minutes = sleepMinutes % 60

    ReportCard(title = "睡眠分析", accent = Color(0xFF6366F1), icon = Icons.Default.NightsStay) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryPill(
                modifier = Modifier.weight(1f),
                label = "昨夜时长",
                value = if (sleepMinutes > 0) "${hours}小时${minutes}分" else "--"
            )
            SummaryPill(
                modifier = Modifier.weight(1f),
                label = "质量评分",
                value = if (sleepScore > 0) "$sleepScore / 100" else "--"
            )
        }
    }
}

@Composable
private fun HeartRateTrendSection(heartRate: Int) {
    ReportCard(title = "心率趋势", accent = Color(0xFFEF4444), icon = Icons.Default.Favorite) {
        Text(
            text = if (heartRate > 0) "静息平均: $heartRate bpm" else "暂无当日心率数据",
            color = ReportMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            listOf(0.42f, 0.58f, 0.51f, 0.76f, 1f, 0.7f, 0.48f).forEachIndexed { index, value ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(value)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(
                            when (index) {
                                4 -> Color(0xFFEF4444)
                                3, 5 -> Color(0xFFFCA5A5)
                                else -> Color(0xFFFEE2E2)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun SpO2AndStressSection(
    bloodOxygen: Int,
    pressure: Int
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactMetricCard(
            modifier = Modifier.weight(1f),
            title = "血氧饱和度",
            value = if (bloodOxygen > 0) "$bloodOxygen%" else "--",
            tone = ReportBlue,
            icon = Icons.Default.WaterDrop
        )
        CompactMetricCard(
            modifier = Modifier.weight(1f),
            title = "压力指数",
            value = if (pressure > 0) pressure.toString() else "--",
            tone = Color(0xFF334155),
            icon = Icons.Default.Speed
        )
    }
}

@Composable
private fun MedicationSummarySection(summary: MedicationSummary?) {
    val total = summary?.totalCount ?: 0
    val taken = summary?.takenCount ?: 0
    val rate = if (total > 0) (taken * 100 / total) else 0

    ReportCard(title = "服药情况", accent = Color(0xFF10B981), icon = Icons.Default.LocalFireDepartment) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                MetricLine(label = "完成情况", value = if (total > 0) "$taken / $total" else "--")
                Spacer(modifier = Modifier.height(8.dp))
                MetricLine(label = "完成率", value = if (total > 0) "$rate%" else "--")
            }
            Surface(
                color = Color(0xFFECFDF5),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (total > 0 && taken == total) "已按时完成" else "仍需关注",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color(0xFF047857),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun HealthInsightsSection(
    data: OppoHealthDashboardData?,
    medicationSummary: MedicationSummary?
) {
    val insight1 = when {
        (data?.latestHeartRate ?: 0) > 0 ->
            "当前静息心率为 ${data?.latestHeartRate} bpm，建议继续保持规律作息与适度活动。"
        else -> "当前日期还没有同步到心率数据，可以切换日期或刷新后再查看。"
    }
    val insight2 = when {
        (medicationSummary?.totalCount ?: 0) > 0 &&
            medicationSummary?.takenCount == medicationSummary?.totalCount ->
            "当日服药任务已全部完成，继续保持现在的执行节奏。"
        (data?.sleepMinutes ?: 0) > 0 ->
            "今日睡眠共 ${(data?.sleepMinutes ?: 0) / 60} 小时，建议和步数一起持续观察恢复状态。"
        else -> "如果需要对外发送报告，可以直接使用底部的 PDF 下载和分享功能。"
    }

    Surface(
        color = ReportBlue.copy(alpha = 0.08f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ReportBlue.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(ReportBlue, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text("健康洞察", color = ReportBlue, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(insight1, color = Color(0xFF334155), lineHeight = 22.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(insight2, color = Color(0xFF334155), lineHeight = 22.sp)
        }
    }
}

@Composable
private fun ReportCard(
    title: String,
    accent: Color,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp, 22.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent)
                    )
                    Text(title, fontWeight = FontWeight.Bold, color = ReportText, fontSize = 16.sp)
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(accent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun CompactMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    tone: Color,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(tone.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontSize = 13.sp, color = ReportMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tone)
        }
    }
}

@Composable
private fun SummaryPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, fontSize = 12.sp, color = ReportMuted)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Bold, color = ReportText)
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = ReportMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ReportText)
    }
}

private fun buildShareText(
    dateLabel: String,
    dashboardData: OppoHealthDashboardData?,
    medicationSummary: MedicationSummary?
): String {
    val lines = mutableListOf<String>()
    lines += "$dateLabel 健康报告"
    lines += "步数：${dashboardData?.steps ?: 0}"
    lines += "睡眠：${dashboardData?.sleepMinutes ?: 0} 分钟"
    lines += "心率：${dashboardData?.latestHeartRate ?: 0} bpm"
    lines += "血氧：${dashboardData?.bloodOxygen ?: 0}%"
    if (medicationSummary != null) {
        lines += "服药完成：${medicationSummary.takenCount}/${medicationSummary.totalCount}"
    }
    return lines.joinToString(separator = "\n")
}

private fun createHealthReportPdf(
    context: Context,
    dateLabel: String,
    dashboardData: OppoHealthDashboardData?,
    medicationSummary: MedicationSummary?
): File {
    val reportsDir = File(context.getExternalFilesDir(null), "reports").apply { mkdirs() }
    val file = File(
        reportsDir,
        "health_report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
    )

    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0F172A")
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E293B")
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#334155")
        textSize = 12f
    }
    val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#64748B")
        textSize = 11f
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E2E8F0")
        strokeWidth = 1f
    }

    var y = 72f
    canvas.drawText("完整健康报告", 40f, y, titlePaint)
    y += 26f
    canvas.drawText(dateLabel, 40f, y, mutedPaint)
    y += 30f
    canvas.drawLine(40f, y, 555f, y, linePaint)
    y += 28f

    fun section(title: String, value: String) {
        canvas.drawText(title, 40f, y, headingPaint)
        y += 22f
        canvas.drawText(value, 40f, y, bodyPaint)
        y += 24f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 28f
    }

    section(
        title = "活动总结",
        value = "步数 ${dashboardData?.steps ?: 0} 步，热量 ${dashboardData?.calories ?: 0} kcal，活跃时长 ${dashboardData?.moveMinutes ?: 0} 分钟"
    )
    section(
        title = "睡眠分析",
        value = "睡眠 ${dashboardData?.sleepMinutes ?: 0} 分钟，睡眠评分 ${dashboardData?.sleepScore ?: 0}"
    )
    section(
        title = "生命体征",
        value = "心率 ${dashboardData?.latestHeartRate ?: 0} bpm，血氧 ${dashboardData?.bloodOxygen ?: 0}%，压力 ${dashboardData?.latestPressure ?: 0}"
    )
    section(
        title = "服药情况",
        value = medicationSummary?.let { "已完成 ${it.takenCount}/${it.totalCount}" } ?: "暂无服药数据"
    )
    section(
        title = "提示",
        value = "本报告由银龄守护应用生成，可用于下载存档或分享给家人查看。"
    )

    document.finishPage(page)
    FileOutputStream(file).use { output -> document.writeTo(output) }
    document.close()
    return file
}

private fun shareHealthReportPdf(
    context: Context,
    file: File,
    shareText: String
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, shareText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享健康报告"))
}

private val ShareGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShareGlyph",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 50f,
        viewportHeight = 50f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(40f, 0f)
            curveTo(34.53125f, 0f, 30.066406f, 4.421875f, 30f, 9.875f)
            lineTo(15.90625f, 16.9375f)
            curveTo(14.25f, 15.71875f, 12.207031f, 15f, 10f, 15f)
            curveTo(4.488281f, 15f, 0f, 19.488281f, 0f, 25f)
            curveTo(0f, 30.511719f, 4.488281f, 35f, 10f, 35f)
            curveTo(12.207031f, 35f, 14.25f, 34.28125f, 15.90625f, 33.0625f)
            lineTo(30f, 40.125f)
            curveTo(30.066406f, 45.578125f, 34.53125f, 50f, 40f, 50f)
            curveTo(45.511719f, 50f, 50f, 45.511719f, 50f, 40f)
            curveTo(50f, 34.488281f, 45.511719f, 30f, 40f, 30f)
            curveTo(37.875f, 30f, 35.902344f, 30.675781f, 34.28125f, 31.8125f)
            lineTo(20.625f, 25f)
            lineTo(34.28125f, 18.1875f)
            curveTo(35.902344f, 19.324219f, 37.875f, 20f, 40f, 20f)
            curveTo(45.511719f, 20f, 50f, 15.511719f, 50f, 10f)
            curveTo(50f, 4.488281f, 45.511719f, 0f, 40f, 0f)
            close()
            moveTo(40f, 2f)
            curveTo(44.429688f, 2f, 48f, 5.570313f, 48f, 10f)
            curveTo(48f, 14.429688f, 44.429688f, 18f, 40f, 18f)
            curveTo(38.363281f, 18f, 36.859375f, 17.492188f, 35.59375f, 16.65625f)
            curveTo(35.46875f, 16.238281f, 35.089844f, 15.949219f, 34.65625f, 15.9375f)
            curveTo(34.652344f, 15.933594f, 34.628906f, 15.941406f, 34.625f, 15.9375f)
            curveTo(33.230469f, 14.675781f, 32.292969f, 12.910156f, 32.0625f, 10.9375f)
            curveTo(32.273438f, 10.585938f, 32.25f, 10.140625f, 32f, 9.8125f)
            curveTo(32.101563f, 5.472656f, 35.632813f, 2f, 40f, 2f)
            close()
            moveTo(30.21875f, 12f)
            curveTo(30.589844f, 13.808594f, 31.449219f, 15.4375f, 32.65625f, 16.75f)
            lineTo(19.8125f, 23.1875f)
            curveTo(19.472656f, 21.359375f, 18.65625f, 19.710938f, 17.46875f, 18.375f)
            close()
            moveTo(10f, 17f)
            curveTo(11.851563f, 17f, 13.554688f, 17.609375f, 14.90625f, 18.65625f)
            curveTo(14.917969f, 18.664063f, 14.925781f, 18.679688f, 14.9375f, 18.6875f)
            curveTo(14.945313f, 18.707031f, 14.957031f, 18.730469f, 14.96875f, 18.75f)
            curveTo(15.054688f, 18.855469f, 15.160156f, 18.9375f, 15.28125f, 19f)
            curveTo(15.285156f, 19.003906f, 15.308594f, 18.996094f, 15.3125f, 19f)
            curveTo(16.808594f, 20.328125f, 17.796875f, 22.222656f, 17.96875f, 24.34375f)
            curveTo(17.855469f, 24.617188f, 17.867188f, 24.925781f, 18f, 25.1875f)
            curveTo(17.980469f, 25.269531f, 17.96875f, 25.351563f, 17.96875f, 25.4375f)
            curveTo(17.847656f, 27.65625f, 16.839844f, 29.628906f, 15.28125f, 31f)
            curveTo(15.1875f, 31.058594f, 15.101563f, 31.132813f, 15.03125f, 31.21875f)
            curveTo(13.65625f, 32.332031f, 11.914063f, 33f, 10f, 33f)
            curveTo(5.570313f, 33f, 2f, 29.429688f, 2f, 25f)
            curveTo(2f, 20.570313f, 5.570313f, 17f, 10f, 17f)
            close()
            moveTo(19.8125f, 26.8125f)
            lineTo(32.65625f, 33.25f)
            curveTo(31.449219f, 34.5625f, 30.589844f, 36.191406f, 30.21875f, 38f)
            lineTo(17.46875f, 31.625f)
            curveTo(18.65625f, 30.289063f, 19.472656f, 28.640625f, 19.8125f, 26.8125f)
            close()
            moveTo(40f, 32f)
            curveTo(44.429688f, 32f, 48f, 35.570313f, 48f, 40f)
            curveTo(48f, 44.429688f, 44.429688f, 48f, 40f, 48f)
            curveTo(35.570313f, 48f, 32f, 44.429688f, 32f, 40f)
            curveTo(32f, 37.59375f, 33.046875f, 35.433594f, 34.71875f, 33.96875f)
            curveTo(34.742188f, 33.949219f, 34.761719f, 33.929688f, 34.78125f, 33.90625f)
            curveTo(34.785156f, 33.902344f, 34.808594f, 33.910156f, 34.8125f, 33.90625f)
            curveTo(34.972656f, 33.839844f, 35.113281f, 33.730469f, 35.21875f, 33.59375f)
            curveTo(36.554688f, 32.597656f, 38.199219f, 32f, 40f, 32f)
            close()
        }
    }.build()
}
