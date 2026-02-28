package com.silverlink.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WatchScreen {
    HOME, HEALTH, SOS, MEDICATION, SLEEP, HEART, SETTINGS
}

@Composable
fun WatchNavigation() {
    var currentScreen by remember { mutableStateOf(WatchScreen.HOME) }

    when (currentScreen) {
        WatchScreen.HOME -> WatchHomeScreen(onNavigate = { currentScreen = it })
        WatchScreen.HEALTH -> HealthDashboard(onBack = { currentScreen = WatchScreen.HOME })
        WatchScreen.SOS -> SOSScreen(onBack = { currentScreen = WatchScreen.HOME })
        WatchScreen.MEDICATION -> MedicationScreen(onBack = { currentScreen = WatchScreen.HOME })
        WatchScreen.SLEEP -> SleepReportScreen(onBack = { currentScreen = WatchScreen.HOME })
        WatchScreen.HEART -> HeartHealthScreen(onBack = { currentScreen = WatchScreen.HOME })
        WatchScreen.SETTINGS -> WatchSettingsScreen(onBack = { currentScreen = WatchScreen.HOME })
    }
}

@Composable
fun WatchHomeScreen(onNavigate: (WatchScreen) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // Heart rate display
            Text(
                text = "72",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF49007)
            )
            Text(
                text = "BPM",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick action buttons (2x2 grid)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Filled.Favorite,
                    label = "健康",
                    color = Color(0xFF4CAF50),
                    onClick = { onNavigate(WatchScreen.HEALTH) }
                )
                QuickActionButton(
                    icon = Icons.Filled.Warning,
                    label = "SOS",
                    color = Color(0xFFEF5350),
                    onClick = { onNavigate(WatchScreen.SOS) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Filled.Medication,
                    label = "用药",
                    color = Color(0xFF2196F3),
                    onClick = { onNavigate(WatchScreen.MEDICATION) }
                )
                QuickActionButton(
                    icon = Icons.Filled.Bedtime,
                    label = "睡眠",
                    color = Color(0xFF9C27B0),
                    onClick = { onNavigate(WatchScreen.SLEEP) }
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}
