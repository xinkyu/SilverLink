package com.silverlink.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.ui.components.ChartType
import com.silverlink.app.ui.components.ChartTypeToggle
import com.silverlink.app.ui.components.CognitiveReportCard
import com.silverlink.app.ui.components.CognitiveReportUiData
import com.silverlink.app.ui.components.HealthTopBar
import com.silverlink.app.ui.components.HeroStatusDisplay
import com.silverlink.app.ui.components.MedicationStatusDisplay
import com.silverlink.app.ui.components.MedicationSummaryCard
import com.silverlink.app.ui.components.MoodAnalysisCard
import com.silverlink.app.ui.components.MoodDetailCard
import com.silverlink.app.ui.components.MoodDistributionDonutChart
import com.silverlink.app.ui.components.MoodTimelineChart
import com.silverlink.app.ui.components.TimeRange
import com.silverlink.app.ui.components.TimeRangeSelector
import com.silverlink.app.ui.theme.WarmPrimary

/**
 * 历史记录主屏幕（老人端）
 * 全新设计：Tab时间范围 + 时间轴图表 + 用药状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel()
) {
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
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 认知评估状态
    val cognitiveReport by viewModel.cognitiveReport.collectAsState()
    val isCognitiveLoading by viewModel.isCognitiveLoading.collectAsState()
    
    val isDarkTheme = isSystemInDarkTheme()
    val gradientBrush = Brush.verticalGradient(
        colors = if (isDarkTheme) {
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.background
            )
        } else {
            listOf(
                Color(0xFFFFF8F0),
                Color(0xFFFFF0E6),
                Color(0xFFFFE8DA)
            )
        }
    )
    
    Scaffold(
        topBar = {
            HealthTopBar(
                title = "健康记录",
                onRefresh = { viewModel.refresh() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(innerPadding)
        ) {
            if (isLoading) {
                // 加载中
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // A+B: 时间范围选择器（Tab + 日期选择）
                    TimeRangeSelector(
                        selectedRange = selectedRange,
                        selectedDate = selectedDate,
                        onRangeSelected = { viewModel.setTimeRange(it) },
                        onDateSelected = { viewModel.setSelectedDate(it) }
                    )
                    
                    // C: 核心状态展示
                    HeroStatusDisplay(
                        currentMood = currentMood,
                        latestTime = latestTime
                    )
                    
                    // 图表类型切换
                    ChartTypeToggle(
                        selectedType = chartType,
                        onTypeSelected = { viewModel.setChartType(it) }
                    )
                    
                    // D: 根据图表类型显示不同内容
                    AnimatedVisibility(
                        visible = chartType == ChartType.MOOD,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            if (selectedRange == TimeRange.DAY) {
                                // 情绪时间轴图表
                                MoodTimelineChart(
                                    moodPoints = moodPoints,
                                    onPointClick = { viewModel.selectMoodPoint(it) }
                                )
                                
                                // 选中情绪点的详情卡片
                                selectedMoodPoint?.let { point ->
                                    Spacer(modifier = Modifier.height(16.dp))
                                    MoodDetailCard(
                                        moodPoint = point,
                                        onDismiss = { viewModel.selectMoodPoint(null) }
                                    )
                                }
                            } else {
                                // 周/月/年：情绪分布环形图 + AI分析
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
                    
                    AnimatedVisibility(
                        visible = chartType == ChartType.MEDICATION,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        if (selectedRange == TimeRange.DAY) {
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
                    }
                    
                    // 无数据提示
                    if (chartType == ChartType.MOOD && moodPoints.isEmpty()) {
                        EmptyStateHint(type = "情绪")
                    }
                    
                    if (chartType == ChartType.MEDICATION) {
                        val showEmpty = if (selectedRange == TimeRange.DAY) {
                            medicationStatuses.isEmpty()
                        } else {
                            medicationSummary == null || (medicationSummary?.totalCount ?: 0) == 0
                        }
                        if (showEmpty) {
                            EmptyStateHint(type = "用药")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
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
