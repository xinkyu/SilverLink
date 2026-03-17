package com.silverlink.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.ui.theme.WarmPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(),
    onNavigateToMedicationHistory: () -> Unit = {},
    onNavigateToMoodAnalysis: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val medicationSummary by viewModel.medicationSummary.collectAsState()
    val currentMood by viewModel.currentMood.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showHealthPrivacyDialog by viewModel.showHealthPrivacyDialog.collectAsState()
    val isHealthLoading by viewModel.isHealthLoading.collectAsState()
    val healthDashboardData by viewModel.healthDashboardData.collectAsState()
    val healthError by viewModel.healthError.collectAsState()
    val healthAuthorized by viewModel.healthAuthorized.collectAsState()
    val cognitiveReport by viewModel.cognitiveReport.collectAsState()

    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    var showBoardEditor by rememberSaveable { mutableStateOf(false) }
    var selectedMetricDetail by remember { mutableStateOf<MetricDetailDialogState?>(null) }
    val defaultVisibleMetricIds = remember {
        listOf("mood", "medication", "cognitive", "heartRate", "activity", "stress", "bloodOxygen", "sleep", "bloodPressure", "weight")
    }
    var visibleMetricIds by rememberSaveable { mutableStateOf(defaultVisibleMetricIds) }

    val steps = healthDashboardData?.steps ?: 0
    val calories = healthDashboardData?.calories ?: 0
    val activeMinutes = healthDashboardData?.moveMinutes ?: 0
    val sleepMinutes = healthDashboardData?.sleepMinutes ?: 0
    val heartRate = healthDashboardData?.latestHeartRate ?: 0
    val bloodOxygen = healthDashboardData?.bloodOxygen ?: 0

    val adherenceText = medicationSummary?.let { "${it.takenCount}/${it.totalCount}" } ?: "未按时"
    val cognitionText = cognitiveReport?.let { "${(it.correctRate * 100).toInt()}%" } ?: "--"
    val adherenceRate = medicationSummary?.let {
        if (it.totalCount > 0) it.takenCount.toFloat() / it.totalCount.toFloat() else null
    }
    val stepsRate = if (steps > 0) (steps / 8000f).coerceIn(0f, 1f) else null
    val sleepRate = if (sleepMinutes > 0) (sleepMinutes / 480f).coerceIn(0f, 1f) else null
    val oxygenRate = if (bloodOxygen > 0) ((bloodOxygen - 90f) / 10f).coerceIn(0f, 1f) else null
    val overallInputs = listOfNotNull(stepsRate, sleepRate, oxygenRate, adherenceRate)
    val overallScore = if (overallInputs.isNotEmpty()) (overallInputs.average() * 100).toInt() else 0
    val overallLabel = when {
        overallInputs.isEmpty() -> "等待同步更多健康数据"
        overallScore >= 85 -> "整体状态良好"
        overallScore >= 70 -> "状态基本稳定"
        overallScore >= 50 -> "建议继续关注作息和活动"
        else -> "建议优先查看详情并补齐记录"
    }
    val heartRateText = if (heartRate > 0) heartRate.toString() else "--"
    val bloodOxygenText = if (bloodOxygen > 0) "$bloodOxygen%" else "--"
    val sleepText = if (sleepMinutes > 0) "${sleepMinutes / 60}h ${sleepMinutes % 60}m" else "未同步"
    val stepsText = if (steps > 0) steps.toString() else "--"
    val stressText = if (!currentMood.isNullOrBlank()) moodStressLabel(currentMood) else "未接入"
    val medicationDisplayText = medicationSummary?.let {
        if (it.totalCount > 0 && it.takenCount == it.totalCount) "已按时服用" else adherenceText
    } ?: "--"
    val metricCards = listOf(
        DashboardMetricCardState(
            id = "mood",
            title = "情绪分析",
            value = currentMood ?: "暂无",
            unit = "",
            iconBgColor = Color(0xFFFEF9C3),
            iconColor = Color(0xFFCA8A04),
            icon = Icons.Default.Face,
            onClick = onNavigateToMoodAnalysis
        ),
        DashboardMetricCardState(
            id = "medication",
            title = "用药记录",
            value = medicationDisplayText,
            unit = "",
            iconBgColor = Color(0xFFDBEAFE),
            iconColor = Color(0xFF2563EB),
            icon = Icons.Default.Check,
            onClick = onNavigateToMedicationHistory
        ),
        DashboardMetricCardState(
            id = "cognitive",
            title = "认知评估",
            value = if (cognitionText == "--") "未评估" else cognitionText,
            unit = "",
            iconBgColor = Color(0xFFF3E8FF),
            iconColor = Color(0xFF9333EA),
            icon = Icons.Default.Star,
            onClick = {
                selectedMetricDetail = MetricDetailDialogState(
                    title = "认知评估",
                    value = if (cognitionText == "--") "暂无最近评估" else cognitionText,
                    description = cognitiveReport?.let {
                        "最近评估区间：${it.startDate} 至 ${it.endDate}\n正确题数：${it.correctAnswers}/${it.totalQuestions}\n平均响应时长：${it.averageResponseTimeMs / 1000} 秒\n趋势：${trendLabel(it.trend)}"
                    } ?: "最近 7 天还没有同步到认知评估结果。"
                )
            }
        ),
        DashboardMetricCardState(
            id = "heartRate",
            title = "心率",
            value = heartRateText,
            unit = if (heartRate > 0) " bpm" else "",
            iconBgColor = Color(0xFFFEE2E2),
            iconColor = Color(0xFFDC2626),
            icon = Icons.Default.Favorite,
            onClick = {
                selectedMetricDetail = MetricDetailDialogState(
                    title = "心率",
                    value = if (heartRate > 0) "$heartRate bpm" else "暂无同步数据",
                    description = if (heartRate > 0) "当前展示的是今日最新一次同步到健康服务的心率值。" else "当前还没有从 OPPO 健康同步到心率数据。"
                )
            }
        ),
        DashboardMetricCardState(
            id = "activity",
            title = "活动详情",
            value = stepsText,
            unit = if (steps > 0) " 步" else "",
            iconBgColor = Color(0xFFD1FAE5),
            iconColor = Color(0xFF059669),
            icon = Icons.Default.PlayArrow,
            onClick = {
                selectedMetricDetail = MetricDetailDialogState(
                    title = "活动详情",
                    value = if (steps > 0) "$steps 步" else "暂无同步数据",
                    description = "热量消耗：${if (calories > 0) "$calories kcal" else "--"}\n活动时长：${if (activeMinutes > 0) "$activeMinutes 分钟" else "--"}\n该数据来自今天同步到健康服务的活动摘要。"
                )
            }
        ),
        DashboardMetricCardState(
            id = "stress",
            title = "压力指数",
            value = stressText,
            unit = "",
            iconBgColor = Color(0xFFFFEDD5),
            iconColor = Color(0xFFEA580C),
            icon = Icons.Default.Warning,
            onClick = {
                selectedMetricDetail = MetricDetailDialogState(
                    title = "压力指数",
                    value = stressText,
                    description = if (!currentMood.isNullOrBlank()) {
                        "当前版本尚未接入独立压力传感器数据，页面依据最近情绪状态给出参考提示，不再展示固定假值。"
                    } else {
                        "当前版本尚未接入独立压力传感器数据。接入前这里会明确显示未接入，不再使用占位数字。"
                    }
                )
            }
        ),
        DashboardMetricCardState(
            id = "bloodOxygen",
            title = "血氧",
            value = bloodOxygenText,
            unit = "",
            iconBgColor = Color(0xFFCFFAFE),
            iconColor = Color(0xFF0891B2),
            icon = Icons.Default.Add,
            onClick = {
                selectedMetricDetail = MetricDetailDialogState(
                    title = "血氧",
                    value = if (bloodOxygen > 0) "$bloodOxygen%" else "暂无同步数据",
                    description = if (bloodOxygen > 0) "当前展示的是今日最新一次同步到健康服务的血氧值。" else "当前还没有从 OPPO 健康同步到血氧数据。"
                )
            }
        ),
        DashboardMetricCardState(
            id = "sleep",
            title = "睡眠",
            value = sleepText,
            unit = "",
            iconBgColor = Color(0xFFE0E7FF),
            iconColor = Color(0xFF4F46E5),
            icon = Icons.Default.Info,
            onClick = {
                selectedMetricDetail = MetricDetailDialogState(
                    title = "睡眠",
                    value = sleepText,
                    description = if (sleepMinutes > 0) "当前展示的是今天同步到健康服务的累计睡眠时长。" else "当前还没有从 OPPO 健康同步到睡眠数据。"
                )
            }
        ),
        DashboardMetricCardState(
            id = "bloodPressure",
            title = "血压",
            value = "未接入",
            unit = "",
            iconBgColor = Color(0xFFFFE4E6),
            iconColor = Color(0xFFE11D48),
            icon = Icons.Default.Favorite,
            onClick = {
                selectedMetricDetail = MetricDetailDialogState(
                    title = "血压",
                    value = "当前未接入",
                    description = "现阶段接入的健康服务仅同步步数、热量、活动时长、心率、血氧和睡眠。血压能力接通前，这里会明确提示未接入。"
                )
            }
        ),
        DashboardMetricCardState(
            id = "weight",
            title = "体重",
            value = "未接入",
            unit = "",
            iconBgColor = Color(0xFFF1F5F9),
            iconColor = Color(0xFF475569),
            icon = Icons.Default.Info,
            onClick = {
                selectedMetricDetail = MetricDetailDialogState(
                    title = "体重",
                    value = "当前未接入",
                    description = "当前健康服务桥接层还没有同步体重数据，所以页面不再显示固定占位值。"
                )
            }
        )
    )
    val visibleMetricCards = metricCards.filter { visibleMetricIds.contains(it.id) }

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = {
                    Text("健康记录", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color(0xFF334155))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        if (showHealthPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissHealthPrivacy() },
                title = { Text("健康服务隐私说明") },
                text = {
                    Text("将接入OPPO健康服务SDK（广东欢太科技有限公司），用于读取步数、心率、睡眠等数据。仅在您同意后初始化并请求授权。")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.acceptHealthPrivacy() }) { Text("同意") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissHealthPrivacy() }) { Text("暂不") }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WarmPrimary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!healthAuthorized) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("开启健康数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("授权获取心率、睡眠等穿戴设备数据", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { activity?.let { viewModel.requestHealthAuthorization(it) } }, enabled = !isHealthLoading) {
                                    Text("去授权")
                                }
                                if (!healthError.isNullOrBlank()) {
                                    Text(healthError!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    // Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text("今日健康摘要", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                                    Text("您的整体健康状况良好", fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 4.dp))
                                }
                                // Activity circular progress 
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                                    CircularProgressIndicator(
                                        progress = { 1f },
                                        color = Color(0xFFF1F5F9),
                                        strokeWidth = 6.dp,
                                        modifier = Modifier.fillMaxSize(),
                                        trackColor = Color.Transparent,
                                    )
                                    CircularProgressIndicator(
                                        progress = { (overallScore / 100f).coerceIn(0f, 1f) },
                                        color = Color(0xFF007bff),
                                        strokeWidth = 6.dp,
                                        modifier = Modifier.fillMaxSize(),
                                        trackColor = Color.Transparent,
                                    )
                                    Text("${overallScore}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF007bff).copy(alpha = 0.05f))
                                        .padding(12.dp)
                                ) {
                                    Text("已消耗热量", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007bff), letterSpacing = 1.sp)
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(if (calories > 0) "${calories}" else "--", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text(" kcal", fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp, start = 4.dp))
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF007bff).copy(alpha = 0.05f))
                                        .padding(12.dp)
                                ) {
                                    Text("活跃时长", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007bff), letterSpacing = 1.sp)
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(if (activeMinutes > 0) "${activeMinutes}" else "--", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text(" min", fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp, start = 4.dp))
                                    }
                                }
                            }

                            Button(
                                onClick = { showReportDialog = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007bff))
                            ) {
                                Text("查看完整报告", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                        }
                    }

                    // Data Grid Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBoardEditor = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("数据概览", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                        Text("编辑看板", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF007bff))
                    }

                    TextButton(onClick = { showBoardEditor = true }, modifier = Modifier.align(Alignment.End)) {
                        Text("自定义看板")
                    }
                    if (false) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            GridItemCard(
                                title = "情绪分析",
                                value = currentMood ?: "平静",
                                unit = "",
                                iconBgColor = Color(0xFFFEF9C3), // yellow-100
                                iconColor = Color(0xFFCA8A04), // yellow-600
                                icon = Icons.Default.Face,
                                onClick = onNavigateToMoodAnalysis,
                                modifier = Modifier.weight(1f)
                            )
                            GridItemCard(
                                title = "用药记录",
                                value = if(medicationSummary?.takenCount == medicationSummary?.totalCount) "已按时服用" else adherenceText,
                                unit = "",
                                iconBgColor = Color(0xFFDBEAFE), // blue-100
                                iconColor = Color(0xFF2563EB), // blue-600
                                icon = Icons.Default.Check,
                                onClick = onNavigateToMedicationHistory,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            GridItemCard(
                                title = "认知评估",
                                value = if(cognitionText == "--") "未评估" else cognitionText,
                                unit = "",
                                iconBgColor = Color(0xFFF3E8FF), // purple-100
                                iconColor = Color(0xFF9333EA), // purple-600
                                icon = Icons.Default.Star,
                                modifier = Modifier.weight(1f)
                            )
                            GridItemCard(
                                title = "心率",
                                value = "$heartRate",
                                unit = " bpm",
                                iconBgColor = Color(0xFFFEE2E2), // red-100
                                iconColor = Color(0xFFDC2626), // red-600
                                icon = Icons.Default.Favorite,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            GridItemCard(
                                title = "活动详情",
                                value = "$steps",
                                unit = " 步",
                                iconBgColor = Color(0xFFD1FAE5), // emerald-100
                                iconColor = Color(0xFF059669), // emerald-600
                                icon = Icons.Default.PlayArrow,
                                modifier = Modifier.weight(1f)
                            )
                            GridItemCard(
                                title = "压力指数",
                                value = "34",
                                unit = " 中等",
                                iconBgColor = Color(0xFFFFEDD5), // orange-100
                                iconColor = Color(0xFFEA580C), // orange-600
                                icon = Icons.Default.Warning,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            GridItemCard(
                                title = "血氧",
                                value = "$bloodOxygen%",
                                unit = "",
                                iconBgColor = Color(0xFFCFFAFE), // cyan-100
                                iconColor = Color(0xFF0891B2), // cyan-600
                                icon = Icons.Default.Add,
                                modifier = Modifier.weight(1f)
                            )
                            GridItemCard(
                                title = "睡眠",
                                value = "${sleepMinutes / 60}h ${sleepMinutes % 60}m",
                                unit = "",
                                iconBgColor = Color(0xFFE0E7FF), // indigo-100
                                iconColor = Color(0xFF4F46E5), // indigo-600
                                icon = Icons.Default.Info,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            GridItemCard(
                                title = "血压",
                                value = "--/--",
                                unit = " mmHg",
                                iconBgColor = Color(0xFFFFE4E6), // rose-100
                                iconColor = Color(0xFFE11D48), // rose-600
                                icon = Icons.Default.Favorite,
                                modifier = Modifier.weight(1f)
                            )
                            GridItemCard(
                                title = "体重",
                                value = "--.--",
                                unit = " kg",
                                iconBgColor = Color(0xFFF1F5F9), // slate-100
                                iconColor = Color(0xFF475569), // slate-600
                                icon = Icons.Default.Info,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        }
                    }
                    MetricGrid(metricCards = visibleMetricCards)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (showReportDialog) {
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = { Text("完整健康报告") },
                text = {
                    Text(
                        "综合得分：$overallScore%\n" +
                            "今日步数：${if (steps > 0) "$steps 步" else "--"}\n" +
                            "活动时长：${if (activeMinutes > 0) "$activeMinutes 分钟" else "--"}\n" +
                            "热量消耗：${if (calories > 0) "$calories kcal" else "--"}\n" +
                            "睡眠时长：$sleepText\n" +
                            "心率：${if (heartRate > 0) "$heartRate bpm" else "--"}\n" +
                            "血氧：$bloodOxygenText\n" +
                            "用药依从：$medicationDisplayText\n" +
                            "情绪状态：${currentMood ?: "暂无"}\n\n" +
                            overallLabel
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showReportDialog = false }) {
                        Text("知道了")
                    }
                }
            )
        }

        selectedMetricDetail?.let { detail ->
            AlertDialog(
                onDismissRequest = { selectedMetricDetail = null },
                title = { Text(detail.title) },
                text = {
                    Text("${detail.value}\n\n${detail.description}")
                },
                confirmButton = {
                    TextButton(onClick = { selectedMetricDetail = null }) {
                        Text("关闭")
                    }
                }
            )
        }

        if (showBoardEditor) {
            AlertDialog(
                onDismissRequest = { showBoardEditor = false },
                title = { Text("自定义看板") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        defaultVisibleMetricIds.forEach { id ->
                            val card = metricCards.first { it.id == id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        visibleMetricIds = if (visibleMetricIds.contains(id)) {
                                            visibleMetricIds.filterNot { it == id }
                                        } else {
                                            visibleMetricIds + id
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = visibleMetricIds.contains(id),
                                    onCheckedChange = { checked ->
                                        visibleMetricIds = if (checked) {
                                            (visibleMetricIds + id).distinct()
                                        } else {
                                            visibleMetricIds.filterNot { it == id }
                                        }
                                    }
                                )
                                Text(card.title)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBoardEditor = false }) {
                        Text("完成")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        visibleMetricIds = defaultVisibleMetricIds
                        showBoardEditor = false
                    }) {
                        Text("重置")
                    }
                }
            )
        }
    }
}

@Composable
private fun MetricGrid(metricCards: List<DashboardMetricCardState>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        metricCards.chunked(2).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowCards.forEach { card ->
                    GridItemCard(
                        title = card.title,
                        value = card.value,
                        unit = card.unit,
                        iconBgColor = card.iconBgColor,
                        iconColor = card.iconColor,
                        icon = card.icon,
                        modifier = Modifier.weight(1f),
                        onClick = card.onClick
                    )
                }
                if (rowCards.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class DashboardMetricCardState(
    val id: String,
    val title: String,
    val value: String,
    val unit: String,
    val iconBgColor: Color,
    val iconColor: Color,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private data class MetricDetailDialogState(
    val title: String,
    val value: String,
    val description: String
)

private fun moodStressLabel(mood: String?): String {
    return when (mood?.uppercase()) {
        "HAPPY", "NEUTRAL" -> "较低"
        "ANXIOUS" -> "偏高"
        "SAD" -> "中等"
        "ANGRY" -> "较高"
        else -> "未接入"
    }
}

private fun trendLabel(trend: String): String {
    return when (trend) {
        "improving" -> "改善中"
        "declining" -> "需关注"
        else -> "保持稳定"
    }
}

@Composable
private fun GridItemCard(
    title: String,
    value: String,
    unit: String,
    iconBgColor: Color,
    iconColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1923))
                if (unit.isNotEmpty()) {
                    Text(unit, fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 1.dp))
                }
            }
        }
    }
}
