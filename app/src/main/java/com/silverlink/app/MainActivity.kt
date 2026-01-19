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
import com.silverlink.app.feature.proactive.ProactiveInteractionService
import android.content.Intent


class MainActivity : ComponentActivity() {
    
    // 通知权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户选择后的回调，这里不需要特殊处理 */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 请求通知权限 (Android 13+)
        requestNotificationPermission()

        // 启动主动闲聊服务（前台服务）- 仅老人端需要
        val userPrefs = com.silverlink.app.data.local.UserPreferences.getInstance(this)
        startProactiveServiceIfElder(userPrefs)

        
        setContent {
            SilverLinkTheme {
                // 检查是否已完成激活
                val userConfig = userPrefs.userConfig.value
                var showOnboarding by remember { 
                    mutableStateOf(!userConfig.isActivated) 
                }
                
                if (showOnboarding) {
                    // 显示引导流程
                    OnboardingNavigation(
                        onOnboardingComplete = {
                            showOnboarding = false
                            // Onboarding完成后，检查并启动服务（如果是老人端）
                            startProactiveServiceIfElder(userPrefs)
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
    
    /**
     * 如果是老人端且已激活，启动主动关怀服务
     */
    private fun startProactiveServiceIfElder(userPrefs: com.silverlink.app.data.local.UserPreferences) {
        val config = userPrefs.userConfig.value
        if (config.role == com.silverlink.app.data.local.UserRole.ELDER && config.isActivated) {
            android.util.Log.d("MainActivity", "Starting ProactiveInteractionService for elder")
            androidx.core.content.ContextCompat.startForegroundService(
                this, 
                Intent(this, ProactiveInteractionService::class.java)
            )
        } else {
            android.util.Log.d("MainActivity", "Skipping ProactiveService: role=${config.role}, activated=${config.isActivated}")
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
