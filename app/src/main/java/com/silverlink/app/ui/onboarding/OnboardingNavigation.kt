package com.silverlink.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole

/**
 * 导航目的地
 */
sealed class OnboardingDestination {
    object Splash : OnboardingDestination()
    object RoleSelection : OnboardingDestination()
    object FamilySetup : OnboardingDestination()
    object ElderActivation : OnboardingDestination()
    object MainApp : OnboardingDestination()
}

/**
 * 引导流程导航器
 * 管理启动流程中的所有页面导航
 */
@Composable
fun OnboardingNavigation(
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val userConfig by userPreferences.userConfig.collectAsState()
    
    var currentDestination by remember { 
        mutableStateOf<OnboardingDestination>(OnboardingDestination.Splash) 
    }
    
    // 检查是否已经激活，如果是则直接进入主应用
    LaunchedEffect(userConfig) {
        if (userConfig.isActivated && currentDestination == OnboardingDestination.Splash) {
            // 已激活用户，短暂显示启动页后直接进入主应用
        }
    }
    
    when (currentDestination) {
        OnboardingDestination.Splash -> {
            SplashScreen(
                onSplashFinished = {
                    // 检查用户是否已经完成激活
                    if (userConfig.isActivated) {
                        onOnboardingComplete()
                    } else {
                        currentDestination = OnboardingDestination.RoleSelection
                    }
                }
            )
        }
        
        OnboardingDestination.RoleSelection -> {
            RoleSelectionScreen(
                onElderSelected = {
                    currentDestination = OnboardingDestination.ElderActivation
                },
                onFamilySelected = {
                    currentDestination = OnboardingDestination.FamilySetup
                }
            )
        }
        
        OnboardingDestination.FamilySetup -> {
            FamilySetupScreen(
                onBack = {
                    currentDestination = OnboardingDestination.RoleSelection
                },
                onSetupComplete = {
                    onOnboardingComplete()
                }
            )
        }
        
        OnboardingDestination.ElderActivation -> {
            ElderActivationScreen(
                onBack = {
                    currentDestination = OnboardingDestination.RoleSelection
                },
                onActivationComplete = { _ ->
                    onOnboardingComplete()
                }
            )
        }
        
        OnboardingDestination.MainApp -> {
            onOnboardingComplete()
        }
    }
}
