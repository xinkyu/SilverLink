package com.silverlink.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silverlink.app.feature.health.OppoHealthDashboardData
import kotlin.math.max

@Composable
fun HealthConsentCard(onAccept: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("OPPO健康服务SDK使用说明", fontWeight = FontWeight.Bold)
            Text(
                "用于读取步数、心率、睡眠等健康数据。仅在您同意后初始化并发起授权。第三方公司：广东欢太科技有限公司。"
            )
            Button(onClick = onAccept) {
                Text("同意并继续")
            }
        }
    }
}

@Composable
fun HealthDashboardSection(
    data: OppoHealthDashboardData?,
    isLoading: Boolean,
    healthAuthorized: Boolean,
    error: String?,
    onAuthorize: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OPPO健康数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!healthAuthorized) {
                    Button(onClick = onAuthorize, enabled = !isLoading) {
                        Text("绑定OPPO健康")
                    }
                } else {
                    Button(onClick = onRefresh, enabled = !isLoading) {
                        Text("刷新")
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            if (!error.isNullOrBlank()) {
                Text(error, color = Color(0xFFD32F2F))
            }

            if (data != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepsRingCard(data.steps, data.stepGoal)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricRow("卡路里", "${data.calories} kcal")
                        MetricRow("距离", "${data.distanceMeters} m")
                        MetricRow("活动时长", "${data.moveMinutes} min")
                        MetricRow("睡眠", "${data.sleepMinutes} min")
                        MetricRow("血氧", "${data.bloodOxygen}%")
                        MetricRow("当前心率", "${data.latestHeartRate} bpm")
                    }
                }

                HeartRateLineChart(data)
            }
        }
    }
}

@Composable
private fun StepsRingCard(steps: Int, goal: Int) {
    val safeGoal = if (goal <= 0) 1 else goal
    val progress = (steps.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(120.dp)
            .background(Color(0xFFF5F7FF), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(88.dp),
            strokeWidth = 8.dp,
            color = Color(0xFF3F51B5),
            trackColor = Color(0xFFE3E7FF),
            strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("步数", style = MaterialTheme.typography.labelMedium)
            Text(steps.toString(), fontWeight = FontWeight.Bold)
            Text("/ $goal", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetricRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HeartRateLineChart(data: OppoHealthDashboardData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFBFF))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("近24小时心率趋势", style = MaterialTheme.typography.titleSmall)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val points = data.heartRateTimeline
                if (points.isEmpty()) return@Canvas

                val minTs = points.first().timestamp
                val maxTs = points.last().timestamp
                val minValue = points.minOf { it.value }
                val maxValue = max(points.maxOf { it.value }, minValue + 1)

                val path = Path()
                points.forEachIndexed { index, p ->
                    val xRatio = if (maxTs == minTs) 0f else (p.timestamp - minTs).toFloat() / (maxTs - minTs).toFloat()
                    val yRatio = (p.value - minValue).toFloat() / (maxValue - minValue).toFloat()
                    val x = xRatio * size.width
                    val y = size.height - yRatio * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(path = path, color = Color(0xFF1E88E5), style = Stroke(width = 4f))
                drawLine(Color(0x33000000), Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
        }
    }
}
