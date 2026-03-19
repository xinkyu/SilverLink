package com.silverlink.app.ui.family

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.ui.components.ChartType
import com.silverlink.app.ui.components.ChartTypeToggle
import com.silverlink.app.ui.components.HealthTopBar
import com.silverlink.app.ui.components.HeroStatusDisplay
import com.silverlink.app.ui.components.MedicationFormDialog
import com.silverlink.app.ui.components.MedicationStatusDisplay
import com.silverlink.app.ui.components.MedicationSummaryCard
import com.silverlink.app.ui.components.MoodAnalysisCard
import com.silverlink.app.ui.components.MoodDetailCard
import com.silverlink.app.ui.components.MoodDistributionDonutChart
import com.silverlink.app.ui.components.MoodTimelineChart
import com.silverlink.app.ui.components.TimeRangeSelector
import com.silverlink.app.ui.components.CognitiveReportCard
import com.silverlink.app.ui.components.CognitiveReportUiData
import com.silverlink.app.ui.components.CognitiveAnalysisCard
import com.silverlink.app.ui.components.LocationCard
import com.silverlink.app.data.remote.AlertData

/**
 * 家人端监控主屏幕（统一UI设计）
 * 显示已配对长辈的服药和情绪记录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMonitoringScreen(
    viewModel: FamilyMonitoringViewModel = viewModel()
) {
    val loadingState by viewModel.loadingState.collectAsState()
    val isPaired by viewModel.isPaired.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    val moodPoints by viewModel.moodPoints.collectAsState()
    val medicationStatuses by viewModel.medicationStatuses.collectAsState()
    val medicationSummary by viewModel.medicationSummary.collectAsState()
    val currentMood by viewModel.currentMood.collectAsState()
    val latestTime by viewModel.latestTime.collectAsState()
    val selectedMoodPoint by viewModel.selectedMoodPoint.collectAsState()
    val moodAnalysis by viewModel.moodAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val overviewAnalysis by viewModel.overviewAnalysis.collectAsState()
    val isOverviewAnalyzing by viewModel.isOverviewAnalyzing.collectAsState()
    val addMedicationState by viewModel.addMedicationState.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    // 警报状态
    val alerts by viewModel.alerts.collectAsState()
    
    // 认知报告状态
    val cognitiveReport by viewModel.cognitiveReport.collectAsState()
    val isCognitiveLoading by viewModel.isCognitiveLoading.collectAsState()
    val cognitiveAnalysis by viewModel.cognitiveAnalysis.collectAsState()
    val isCognitiveAnalyzing by viewModel.isCognitiveAnalyzing.collectAsState()
    
    // 位置状态
    val elderLocation by viewModel.elderLocation.collectAsState()
    val isLocationLoading by viewModel.isLocationLoading.collectAsState()
    
    // 启动位置轮询
    LaunchedEffect(isPaired) {
        if (isPaired && viewModel.showLocationSection) {
            viewModel.startLocationPolling()
        }
    }
    
    // 监听添加成功后关闭对话框
    LaunchedEffect(addMedicationState) {
        if (addMedicationState is LoadingState.Success) {
            showAddDialog = false
            viewModel.resetAddMedicationState()
        }
    }
    
    val isDarkTheme = isSystemInDarkTheme()
    val gradientBrush = Brush.verticalGradient(
        colors = if (isDarkTheme) {
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.background
            )
        } else {
            listOf(Color.White, Color.White)
        }
    )
    
    val familyPrimary = Color(0xFF3F51B5)
    
    Scaffold(
        topBar = {
            HealthTopBar(
                title = viewModel.pageTitle,
                onRefresh = { viewModel.refresh() },
                primaryColor = familyPrimary
            )
        },
        floatingActionButton = {
            if (viewModel.isFamilyRole && isPaired) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = familyPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加药品")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(innerPadding)
        ) {
            when (loadingState) {
                is LoadingState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = familyPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (viewModel.isFamilyRole) "正在获取长辈健康数据..." else "正在获取健康概览...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                is LoadingState.Error -> {
                    if (!isPaired) {
                        NotPairedView()
                    } else {
                        ErrorView(
                            message = (loadingState as LoadingState.Error).message,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }
                
                else -> {
                    if (!isPaired) {
                        NotPairedView()
                    } else {
                        // 主要内容
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 警报横幅（如果有未读警报）
                            if (viewModel.showAlertsSection) {
                                alerts.forEach { alert ->
                                    AlertBanner(
                                        alert = alert,
                                        onDismiss = { viewModel.dismissAlert(alert.id) }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                            
                            // 时间范围选择器
                            TimeRangeSelector(
                                selectedRange = selectedRange,
                                selectedDate = selectedDate,
                                onRangeSelected = { viewModel.setTimeRange(it) },
                                onDateSelected = { viewModel.setSelectedDate(it) },
                                primaryColor = familyPrimary
                            )
                            
                            // 核心状态展示
                            HeroStatusDisplay(
                                currentMood = currentMood,
                                latestTime = latestTime,
                                titlePrefix = viewModel.heroTitlePrefix
                            )
                            
                            if (viewModel.showLocationSection) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                LocationCard(
                                    location = elderLocation,
                                    isLoading = isLocationLoading,
                                    onRefresh = { viewModel.refreshLocation() },
                                    onViewMap = if (elderLocation != null) {
                                        {
                                            val lat = elderLocation?.latitude ?: 0.0
                                            val lng = elderLocation?.longitude ?: 0.0

                                            android.widget.Toast.makeText(context, "正在打开地图...", android.widget.Toast.LENGTH_SHORT).show()

                                            val geoUri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(长辈位置)")
                                            val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
                                            mapIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

                                            try {
                                                context.startActivity(mapIntent)
                                            } catch (e: Exception) {
                                                val webUri = android.net.Uri.parse("https://uri.amap.com/marker?position=$lng,$lat&name=长辈位置")
                                                val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
                                                webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                try {
                                                    context.startActivity(webIntent)
                                                } catch (e2: Exception) {
                                                    android.widget.Toast.makeText(context, "没有可用的地图或浏览器应用", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else null
                                )
                            }
                            
                            // 图表类型切换
                            ChartTypeToggle(
                                selectedType = chartType,
                                onTypeSelected = { viewModel.setChartType(it) },
                                primaryColor = familyPrimary
                            )

                            AnimatedVisibility(
                                visible = chartType == ChartType.HEALTH,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                MoodAnalysisCard(
                                    analysis = overviewAnalysis,
                                    isLoading = isOverviewAnalyzing,
                                    title = "AI 概览说明",
                                    loadingText = "正在整理概览并生成建议…",
                                    emptyText = "暂无可分析的概览数据"
                                )
                            }
                            
                            // 情绪图表
                            AnimatedVisibility(
                                visible = chartType == ChartType.MOOD,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    if (selectedRange == com.silverlink.app.ui.components.TimeRange.DAY) {
                                        MoodTimelineChart(
                                            moodPoints = moodPoints,
                                            onPointClick = { viewModel.selectMoodPoint(it) }
                                        )
                                        
                                        selectedMoodPoint?.let { point ->
                                            Spacer(modifier = Modifier.height(16.dp))
                                            MoodDetailCard(
                                                moodPoint = point,
                                                onDismiss = { viewModel.selectMoodPoint(null) }
                                            )
                                        }
                                    } else {
                                        MoodDistributionDonutChart(
                                            moodPoints = moodPoints
                                        )
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        MoodAnalysisCard(
                                            analysis = moodAnalysis,
                                            isLoading = isAnalyzing
                                        )
                                    }
                                }
                            }
                            
                            // 用药状态
                            AnimatedVisibility(
                                visible = chartType == ChartType.MEDICATION,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                if (selectedRange == com.silverlink.app.ui.components.TimeRange.DAY) {
                                    MedicationStatusDisplay(
                                        medicationStatuses = medicationStatuses
                                    )
                                } else {
                                    MedicationSummaryCard(
                                        summary = medicationSummary
                                    )
                                }
                            }
                            
                            // 认知评估
                            AnimatedVisibility(
                                visible = chartType == ChartType.COGNITIVE,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    val reportUiData = cognitiveReport?.let { report ->
                                        CognitiveReportUiData(
                                            totalQuestions = report.totalQuestions,
                                            correctAnswers = report.correctAnswers,
                                            correctRate = report.correctRate,
                                            averageResponseTimeMs = report.averageResponseTimeMs,
                                            trend = report.trend,
                                            startDate = report.startDate,
                                            endDate = report.endDate
                                        )
                                    }
                                    CognitiveReportCard(
                                        report = reportUiData,
                                        isLoading = isCognitiveLoading
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    CognitiveAnalysisCard(
                                        analysis = cognitiveAnalysis,
                                        isLoading = isCognitiveAnalyzing
                                    )
                                }
                            }
                            
                            // 无数据提示
                            if (chartType == ChartType.MOOD && moodPoints.isEmpty()) {
                                EmptyStateHint(type = "情绪")
                            }
                            
                            if (chartType == ChartType.MEDICATION) {
                                val showEmpty = if (selectedRange == com.silverlink.app.ui.components.TimeRange.DAY) {
                                    medicationStatuses.isEmpty()
                                } else {
                                    medicationSummary == null || (medicationSummary?.totalCount ?: 0) == 0
                                }
                                if (showEmpty) {
                                    EmptyStateHint(type = "用药")
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(if (viewModel.isFamilyRole) 80.dp else 24.dp))
                        }
                    }
                }
            }
        }
    }
    
    // 添加药品对话框
    if (showAddDialog) {
        MedicationFormDialog(
            title = viewModel.medicationDialogTitle,
            subtitle = viewModel.medicationDialogSubtitle,
            isLoading = addMedicationState is LoadingState.Loading,
            errorMessage = (addMedicationState as? LoadingState.Error)?.message,
            confirmButtonText = "添加",
            primaryColor = familyPrimary,
            onDismiss = { 
                showAddDialog = false
                viewModel.resetAddMedicationState()
            },
            onConfirm = { name, dosage, times ->
                viewModel.addMedication(name, dosage, times.joinToString(","))
            }
        )
    }
}

/**
 * 未配对状态视图
 */
@Composable
private fun NotPairedView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "👨‍👩‍👧‍👦",
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "尚未配对长辈",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "请先在「设置」中与长辈设备配对\n配对后即可查看长辈的健康记录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 错误视图
 */
@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "😥",
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                onClick = onRetry,
                color = Color(0xFF3F51B5),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "重试",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * 无数据提示
 */
@Composable
private fun EmptyStateHint(type: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (type == "情绪") "📝" else "💊",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "暂无${type}记录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 警报横幅
 */
@Composable
private fun AlertBanner(
    alert: AlertData,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "警报",
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "⚠️ 长辈状态提醒",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFD32F2F)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5D4037)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.Gray
                )
            }
        }
    }
}
