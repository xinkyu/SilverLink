package com.silverlink.app.ui.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.ui.components.CognitiveAnalysisCard
import com.silverlink.app.ui.components.CognitiveReportCard
import com.silverlink.app.ui.components.CognitiveReportUiData
import com.silverlink.app.ui.family.FamilyMonitoringViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CognitiveAssessmentScreen(
    onNavigateBack: () -> Unit,
    viewModel: FamilyMonitoringViewModel = viewModel()
) {
    val context = LocalContext.current
    val cognitiveReport by viewModel.cognitiveReport.collectAsState()
    val isCognitiveLoading by viewModel.isCognitiveLoading.collectAsState()
    val cognitiveAnalysis by viewModel.cognitiveAnalysis.collectAsState()
    val isCognitiveAnalyzing by viewModel.isCognitiveAnalyzing.collectAsState()
    val elderName by viewModel.elderName.collectAsState()

    val subjectName = elderName.ifBlank { "长辈" }
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

    Scaffold(
        containerColor = Color(0xFFF5F7F8),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "认知评估",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color(0xFF475569))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareText = buildCognitiveShareText(subjectName, reportUiData, cognitiveAnalysis)
                            if (shareText.isBlank()) {
                                Toast.makeText(context, "暂无可分享的认知评估内容", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享认知评估"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color(0xFF475569))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White.copy(alpha = 0.92f)),
                modifier = Modifier.background(Color.White)
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
                CognitiveSummaryHeroCard(
                    subjectName = subjectName,
                    report = reportUiData,
                    isLoading = isCognitiveLoading
                )
            }
            item {
                CognitiveReportCard(
                    report = reportUiData,
                    isLoading = isCognitiveLoading
                )
            }
            item {
                CognitiveAnalysisCard(
                    analysis = cognitiveAnalysis,
                    isLoading = isCognitiveAnalyzing,
                    title = "AI 建议",
                    loadingText = "正在生成给家人的认知照护建议…",
                    emptyText = "暂无可生成的认知建议"
                )
            }
            item {
                CognitiveNoteCard(subjectName = subjectName)
            }
        }
    }
}

@Composable
private fun CognitiveSummaryHeroCard(
    subjectName: String,
    report: CognitiveReportUiData?,
    isLoading: Boolean
) {
    val rateText = report?.let { "${(it.correctRate * 100).toInt()}%" } ?: "--"
    val trendText = report?.let { trendLabel(it.trend) } ?: "等待评估"
    val averageSeconds = report?.averageResponseTimeMs?.div(1000) ?: 0L

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5)
                )
            }
            Text(
                text = "${subjectName}的近期认知状态",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = when {
                    isLoading -> "正在同步认知评估结果，请稍候。"
                    report == null || report.totalQuestions == 0 -> "目前还没有可展示的认知评估记录。完成记忆问答后，这里会生成报告和建议。"
                    else -> "${subjectName}在 ${report.startDate} 至 ${report.endDate} 共完成 ${report.totalQuestions} 题，当前正确率 $rateText，趋势为$trendText。"
                },
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Color(0xFF475569)
            )
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF4F46E5),
                    trackColor = Color(0xFFC7D2FE)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CognitiveHeroStat(
                        label = "正确率",
                        value = rateText
                    )
                    CognitiveHeroStat(
                        label = "平均用时",
                        value = if (report == null || report.totalQuestions == 0) "--" else "${averageSeconds}秒"
                    )
                    CognitiveHeroStat(
                        label = "趋势",
                        value = trendText
                    )
                }
            }
        }
    }
}

@Composable
private fun CognitiveHeroStat(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun CognitiveNoteCard(subjectName: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "查看说明",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "这里展示的是${subjectName}最近一次统计区间内的认知答题表现。AI 建议默认写给家人，便于结合日常陪伴、照片回忆和规律训练做后续观察。",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Color(0xFF475569)
            )
        }
    }
}

private fun buildCognitiveShareText(
    subjectName: String,
    report: CognitiveReportUiData?,
    analysis: String?
): String {
    if (report == null || report.totalQuestions == 0) return ""
    val trend = trendLabel(report.trend)
    return buildString {
        appendLine("${subjectName}认知评估")
        appendLine("时间范围：${report.startDate} 至 ${report.endDate}")
        appendLine("正确题数：${report.correctAnswers}/${report.totalQuestions}")
        appendLine("正确率：${(report.correctRate * 100).toInt()}%")
        appendLine("平均用时：${report.averageResponseTimeMs / 1000} 秒")
        appendLine("趋势：$trend")
        if (!analysis.isNullOrBlank()) {
            appendLine()
            appendLine("AI建议：")
            appendLine(analysis)
        }
    }.trim()
}

private fun trendLabel(trend: String): String {
    return when (trend) {
        "improving" -> "进步中"
        "declining" -> "需关注"
        else -> "保持稳定"
    }
}
