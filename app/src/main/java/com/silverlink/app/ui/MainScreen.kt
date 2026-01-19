package com.silverlink.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole
import com.silverlink.app.ui.chat.ChatScreen
import com.silverlink.app.ui.family.FamilyMonitoringScreen
import com.silverlink.app.ui.history.HistoryScreen
import com.silverlink.app.ui.reminder.ReminderScreen
import com.silverlink.app.ui.theme.WarmPrimary

/**
 * 主屏幕
 * 根据用户角色显示不同的标签页：
 * - 老人端：聊天 | 提醒 | 健康记录
 * - 家人端：长辈监控
 */
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferences.getInstance(context) }
    val userConfig by userPrefs.userConfig.collectAsState()

    when (userConfig.role) {
        UserRole.FAMILY -> FamilyMainScreen(modifier)
        else -> ElderMainScreen(modifier)
    }
}

/**
 * 老人端主屏幕 - 3个标签
 */
@Composable
private fun ElderMainScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(0) }

    // 简单的内部导航状态，用于从设置跳转到联系人页面
    // 0: Main Tabs, 1: Emergency Contacts
    var currentSubScreen by remember { mutableStateOf(0) }

    // 处理返回键逻辑 (简单的实现)
    if (currentSubScreen == 1) {
        androidx.activity.compose.BackHandler {
            currentSubScreen = 0
        }
    }

    if (currentSubScreen == 1) {
        com.silverlink.app.ui.falldetection.EmergencyContactScreen(
            onBack = { currentSubScreen = 0 }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = "聊天",
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text("小银陪伴", style = MaterialTheme.typography.labelMedium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WarmPrimary,
                        selectedTextColor = WarmPrimary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "吃药",
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text("吃药提醒", style = MaterialTheme.typography.labelMedium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WarmPrimary,
                        selectedTextColor = WarmPrimary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "记录",
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text("健康记录", style = MaterialTheme.typography.labelMedium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WarmPrimary,
                        selectedTextColor = WarmPrimary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        // 使用 Shield 图标或者类似的
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "安全",
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text("安全守护", style = MaterialTheme.typography.labelMedium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.silverlink.app.ui.theme.AlertRed,
                        selectedTextColor = com.silverlink.app.ui.theme.AlertRed,
                        indicatorColor = com.silverlink.app.ui.theme.AlertRed.copy(alpha = 0.1f),
                        // 非选中状态
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> ChatScreen()
                1 -> ReminderScreen()
                2 -> HistoryScreen()
                3 -> com.silverlink.app.ui.falldetection.FallDetectionScreen(
                    onNavigateToContacts = { currentSubScreen = 1 }
                )
            }
        }
    }
}

/**
 * 家人端主屏幕 - 单标签（监控）
 */
@Composable
private fun FamilyMainScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "监控",
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text("长辈健康", style = MaterialTheme.typography.labelMedium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3F51B5),
                        selectedTextColor = Color(0xFF3F51B5),
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> FamilyMonitoringScreen()
            }
        }
    }
}
