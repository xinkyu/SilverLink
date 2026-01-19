package com.silverlink.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.silverlink.app.ui.theme.SilverLinkTheme
import com.silverlink.app.ui.MainScreen
import com.silverlink.app.ui.onboarding.OnboardingNavigation

class MainActivity : ComponentActivity() {
    
    // 通知权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户选择后的回调，这里不需要特殊处理 */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 请求通知权限 (Android 13+)
        requestNotificationPermission()
        
        // 启动家人端轮询服务（如果是家人角色）
        startFamilyPollingServiceIfNeeded()
        
        setContent {
            SilverLinkTheme {
                var showOnboarding by remember { mutableStateOf(true) }
                
                if (showOnboarding) {
                    // 显示引导流程
                    OnboardingNavigation(
                        onOnboardingComplete = {
                            showOnboarding = false
                            // 引导完成后再次检查是否需要启动服务
                            startFamilyPollingServiceIfNeeded()
                        }
                    )
                } else {
                    // 显示主应用
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MainScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
    
    private fun startFamilyPollingServiceIfNeeded() {
        val userPrefs = com.silverlink.app.data.local.UserPreferences.getInstance(this)
        if (userPrefs.userConfig.value.role == com.silverlink.app.data.local.UserRole.FAMILY &&
            userPrefs.userConfig.value.isActivated) {
            val intent = android.content.Intent(this, 
                com.silverlink.app.feature.falldetection.EmergencyPollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
