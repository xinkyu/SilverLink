package com.silverlink.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WatchSettingsScreen(onBack: () -> Unit) {
    var fallDetectionEnabled by remember { mutableStateOf(true) }
    var highSensitivity by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Fall detection toggle
            SettingsRow(
                label = "跌倒检测",
                checked = fallDetectionEnabled,
                onCheckedChange = { fallDetectionEnabled = it }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // High sensitivity toggle
            SettingsRow(
                label = "高灵敏度",
                checked = highSensitivity,
                onCheckedChange = { highSensitivity = it }
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onBack) {
                Text("返回", color = Color(0xFFF49007), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFF49007),
                checkedTrackColor = Color(0xFFF49007).copy(alpha = 0.3f)
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}
