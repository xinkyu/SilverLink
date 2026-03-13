package com.silverlink.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverlink.wear.WatchApp

@Composable
fun HeartHealthScreen(onBack: () -> Unit) {
    val prefs = remember { WatchApp.instance.watchPreferences }
    val currentHR = prefs.heartRate
    val minHR = prefs.minHeartRate
    val maxHR = prefs.maxHeartRate
    val heartStatus = when {
        currentHR == 0 -> "暂无数据"
        currentHR < 60 -> "心率偏低"
        currentHR > 100 -> "心率偏高"
        else -> "心率正常"
    }
    val statusColor = when {
        currentHR == 0 -> Color.Gray
        currentHR in 60..100 -> Color(0xFF4CAF50)
        else -> Color(0xFFEF5350)
    }

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
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "心脏健康",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Current heart rate
            Text(
                text = if (currentHR > 0) "$currentHR" else "--",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF49007)
            )
            Text(
                text = "当前心率 BPM",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (minHR > 0) "$minHR" else "--",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text("最低", fontSize = 10.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (maxHR > 0) "$maxHR" else "--",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF5350)
                    )
                    Text("最高", fontSize = 10.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = heartStatus,
                fontSize = 12.sp,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onBack) {
                Text("返回", color = Color(0xFFF49007), fontSize = 12.sp)
            }
        }
    }
}
