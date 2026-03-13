package com.silverlink.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverlink.wear.WatchApp

@Composable
fun SleepReportScreen(onBack: () -> Unit) {
    val prefs = remember { WatchApp.instance.watchPreferences }
    val deepMinutes = prefs.deepSleepMinutes
    val lightMinutes = prefs.lightSleepMinutes
    val remMinutes = prefs.remSleepMinutes
    val totalMinutes = deepMinutes + lightMinutes + remMinutes
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    val sleepScore = prefs.sleepScore

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "昨晚睡眠",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (totalMinutes > 0) {
                // Sleep duration
                Text(
                    text = "${hours}h ${mins}m",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (sleepScore > 0) {
                    Text(
                        text = "睡眠评分 $sleepScore",
                        fontSize = 12.sp,
                        color = Color(0xFF9C27B0)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sleep stages
                SleepStageRow("深睡", deepMinutes, Color(0xFF1A237E))
                SleepStageRow("浅睡", lightMinutes, Color(0xFF42A5F5))
                SleepStageRow("REM", remMinutes, Color(0xFF9C27B0))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "暂无睡眠数据",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onBack) {
                Text("返回", color = Color(0xFFF49007), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SleepStageRow(label: String, minutes: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${minutes / 60}h ${minutes % 60}m",
            fontSize = 11.sp,
            color = Color.White
        )
    }
}
