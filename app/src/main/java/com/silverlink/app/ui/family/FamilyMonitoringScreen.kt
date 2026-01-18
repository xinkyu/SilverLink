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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
    val addMedicationState by viewModel.addMedicationState.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    // 监听添加成功后关闭对话框
    LaunchedEffect(addMedicationState) {
        if (addMedicationState is LoadingState.Success) {
            showAddDialog = false
            viewModel.resetAddMedicationState()
        }
    }
    
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF0F4FF),
            Color(0xFFE8F0FF),
            Color(0xFFE0EAFF)
        )
    )
    
    val familyPrimary = Color(0xFF3F51B5)
    
    Scaffold(
        topBar = {
            HealthTopBar(
                title = "长辈健康",
                onRefresh = { viewModel.refresh() },
                primaryColor = familyPrimary
            )
        },
        floatingActionButton = {
            if (isPaired) {
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
                                "正在获取长辈健康数据...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
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
                                titlePrefix = "长辈"
                            )
                            
                            // 图表类型切换
                            ChartTypeToggle(
                                selectedType = chartType,
                                onTypeSelected = { viewModel.setChartType(it) },
                                primaryColor = familyPrimary
                            )
                            
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
                            
                            Spacer(modifier = Modifier.height(80.dp)) // FAB空间
                        }
                    }
                }
            }
        }
    }
    
    // 添加药品对话框
    if (showAddDialog) {
        MedicationFormDialog(
            title = "为长辈添加药品",
            subtitle = "添加的药品将同步到长辈设备",
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
                color = Color(0xFF5D4037)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "请先在「设置」中与长辈设备配对\n配对后即可查看长辈的健康记录",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
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
                color = Color(0xFF5D4037)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
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
                color = Color.Gray
            )
        }
    }
}
