package com.silverlink.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole
import com.silverlink.app.ui.chat.ChatScreen
import com.silverlink.app.ui.family.FamilyMonitoringScreen
import com.silverlink.app.ui.family.FamilyLocationScreen
import com.silverlink.app.ui.family.FamilyLocationViewModel
import com.silverlink.app.ui.history.HistoryScreen
import com.silverlink.app.ui.memory.ElderPhotoGridScreen
import com.silverlink.app.ui.memory.MemoryGalleryScreen
import com.silverlink.app.ui.memory.MemoryQuizScreen
import com.silverlink.app.ui.memory.MemoryLibraryScreen
import com.silverlink.app.ui.reminder.ReminderScreen
import com.silverlink.app.ui.theme.WarmPrimary

/**
 * 主屏幕
 * 根据用户角色显示不同的标签页：
 * - 老人端：聊天 | 提醒 | 记忆相册 | 健康概览 | 安全守护
 * - 家人端：健康记录 | 记忆库
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
 * 老人端主屏幕 - 4个标签（新增记忆相册）
 */
@Composable
private fun ElderMainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferences.getInstance(context) }
    val userConfig by userPrefs.userConfig.collectAsState()
    val assistantName = userConfig.assistantName.ifBlank { "小银" }
    var selectedTab by remember { mutableStateOf(0) }
    var showMemoryQuiz by remember { mutableStateOf(false) }
    var showPhotoDetail by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableStateOf(0) }
    
    // 语音命令导航状态
    var showMedicationAdd by remember { mutableStateOf(false) }
    var showEmergencyContacts by remember { mutableStateOf(false) }
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
                        Text("${assistantName}陪伴", style = MaterialTheme.typography.labelMedium) 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WarmPrimary,
                        selectedTextColor = WarmPrimary,
                        indicatorColor = Color.Transparent
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
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { 
                        Icon(
                            Icons.Default.PhotoLibrary, 
                            contentDescription = "相册",
                            modifier = Modifier.size(26.dp)
                        ) 
                    },
                    label = { 
                        Text("记忆相册", style = MaterialTheme.typography.labelMedium) 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WarmPrimary,
                        selectedTextColor = WarmPrimary,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { 
                        Icon(
                            Icons.Default.Favorite, 
                            contentDescription = "概览",
                            modifier = Modifier.size(26.dp)
                        ) 
                    },
                    label = { 
                        Text("健康概览", style = MaterialTheme.typography.labelMedium) 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WarmPrimary,
                        selectedTextColor = WarmPrimary,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { 
                        Icon(
                            Icons.Default.Security, 
                            contentDescription = "安全",
                            modifier = Modifier.size(26.dp)
                        ) 
                    },
                    label = { 
                        Text("安全守护", style = MaterialTheme.typography.labelMedium) 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WarmPrimary,
                        selectedTextColor = WarmPrimary,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> ChatScreen(
                    onNavigateToGallery = {
                        selectedTab = 2
                        showMemoryQuiz = false
                        showPhotoDetail = false
                    },
                    onNavigateToMedicationAdd = {
                        selectedTab = 1
                        showMedicationAdd = true
                    },
                    onNavigateToMedicationFind = {
                        // 已在ChatScreen中直接处理
                    },
                    onNavigateToMemoryQuiz = {
                        selectedTab = 2
                        showMemoryQuiz = true
                    },
                    onNavigateToMoodAnalysis = { _ ->
                        selectedTab = 3
                    },
                    onNavigateToSafetySettings = {
                        selectedTab = 4
                        showEmergencyContacts = false
                    },
                    onNavigateToContacts = {
                        selectedTab = 4
                        showEmergencyContacts = true
                    }
                )
                1 -> ReminderScreen()
                2 -> {
                    when {
                        showMemoryQuiz -> {
                            MemoryQuizScreen(
                                onBack = { showMemoryQuiz = false }
                            )
                        }
                        showPhotoDetail -> {
                            MemoryGalleryScreen(
                                onBack = { showPhotoDetail = false },
                                onQuizClick = { showMemoryQuiz = true },
                                initialPhotoIndex = selectedPhotoIndex
                            )
                        }
                        else -> {
                            ElderPhotoGridScreen(
                                onPhotoClick = { index ->
                                    selectedPhotoIndex = index
                                    showPhotoDetail = true
                                },
                                onQuizClick = { showMemoryQuiz = true }
                            )
                        }
                    }
                }
                3 -> FamilyMonitoringScreen()
                4 -> {
                    // 安全守护 Tab
                    if (showEmergencyContacts) {
                        com.silverlink.app.feature.falldetection.EmergencyContactScreen(
                            onBack = { showEmergencyContacts = false }
                        )
                    } else {
                        com.silverlink.app.feature.falldetection.FallDetectionScreen(
                            onNavigateToContacts = { showEmergencyContacts = true }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 家人端主屏幕 - 3个标签（健康记录 + 位置守护 + 记忆库）
 */
@Composable
private fun FamilyMainScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(0) }
    val familyLocationViewModel: FamilyLocationViewModel = viewModel()

    LaunchedEffect(Unit) {
        familyLocationViewModel.startMonitoring()
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
                            Icons.Default.DateRange, 
                            contentDescription = "记录",
                            modifier = Modifier.size(26.dp)
                        ) 
                    },
                    label = { 
                        Text("健康记录", style = MaterialTheme.typography.labelMedium) 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3F51B5),
                        selectedTextColor = Color(0xFF3F51B5),
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "位置守护",
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text("位置守护", style = MaterialTheme.typography.labelMedium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3F51B5),
                        selectedTextColor = Color(0xFF3F51B5),
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { 
                        Icon(
                            Icons.Default.PhotoLibrary, 
                            contentDescription = "记忆库",
                            modifier = Modifier.size(26.dp)
                        ) 
                    },
                    label = { 
                        Text("记忆库", style = MaterialTheme.typography.labelMedium) 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3F51B5),
                        selectedTextColor = Color(0xFF3F51B5),
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> FamilyHealthRecordContent()
                1 -> FamilyLocationScreen(viewModel = familyLocationViewModel)
                2 -> MemoryLibraryScreen(onBack = { selectedTab = 0 })
            }
        }
    }
}

@Composable
private fun FamilyHealthRecordContent() {
    var moodAnalysisPeriod by remember { mutableStateOf<String?>(null) }
    var showMedicationHistory by remember { mutableStateOf(false) }
    var showCognitiveAssessment by remember { mutableStateOf(false) }
    var showHeartRateDetail by remember { mutableStateOf(false) }
    var showActivityDetail by remember { mutableStateOf(false) }
    var showSleepDetail by remember { mutableStateOf(false) }
    var showBloodPressureDetail by remember { mutableStateOf(false) }
    var showBloodOxygenDetail by remember { mutableStateOf(false) }
    var showStressDetail by remember { mutableStateOf(false) }
    var showWeightDetail by remember { mutableStateOf(false) }

    when {
        moodAnalysisPeriod != null -> {
            com.silverlink.app.ui.history.MoodAnalysisScreen(
                initialPeriod = moodAnalysisPeriod,
                onNavigateBack = { moodAnalysisPeriod = null }
            )
        }
        showMedicationHistory -> {
            com.silverlink.app.ui.history.MedicationHistoryScreen(
                onNavigateBack = { showMedicationHistory = false }
            )
        }
        showCognitiveAssessment -> {
            com.silverlink.app.ui.history.CognitiveAssessmentScreen(
                onNavigateBack = { showCognitiveAssessment = false }
            )
        }
        showHeartRateDetail -> {
            com.silverlink.app.ui.history.HeartRateDetailScreen(
                onNavigateBack = { showHeartRateDetail = false }
            )
        }
        showActivityDetail -> {
            com.silverlink.app.ui.history.ActivityDetailScreen(
                onNavigateBack = { showActivityDetail = false }
            )
        }
        showSleepDetail -> {
            com.silverlink.app.ui.history.SleepDetailScreen(
                onNavigateBack = { showSleepDetail = false }
            )
        }
        showBloodPressureDetail -> {
            com.silverlink.app.ui.history.BloodPressureDetailScreen(
                onNavigateBack = { showBloodPressureDetail = false }
            )
        }
        showBloodOxygenDetail -> {
            com.silverlink.app.ui.history.BloodOxygenDetailScreen(
                onNavigateBack = { showBloodOxygenDetail = false }
            )
        }
        showStressDetail -> {
            com.silverlink.app.ui.history.StressDetailScreen(
                onNavigateBack = { showStressDetail = false }
            )
        }
        showWeightDetail -> {
            com.silverlink.app.ui.history.WeightDetailScreen(
                onNavigateBack = { showWeightDetail = false }
            )
        }
        else -> {
            HistoryScreen(
                onNavigateToMedicationHistory = { showMedicationHistory = true },
                onNavigateToMoodAnalysis = { moodAnalysisPeriod = "day" },
                onNavigateToCognitiveAssessment = { showCognitiveAssessment = true },
                onNavigateToHeartRateDetail = { showHeartRateDetail = true },
                onNavigateToActivityDetail = { showActivityDetail = true },
                onNavigateToSleepDetail = { showSleepDetail = true },
                onNavigateToBloodPressureDetail = { showBloodPressureDetail = true },
                onNavigateToBloodOxygenDetail = { showBloodOxygenDetail = true },
                onNavigateToStressDetail = { showStressDetail = true },
                onNavigateToWeightDetail = { showWeightDetail = true }
            )
        }
    }
}

