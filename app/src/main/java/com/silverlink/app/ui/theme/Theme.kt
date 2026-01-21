package com.silverlink.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = WarmPrimary,
    onPrimary = WarmOnPrimary,
    primaryContainer = WarmContainer,
    onPrimaryContainer = WarmOnPrimary,
    secondary = CalmSecondary,
    onSecondary = CalmOnSecondary,
    secondaryContainer = CalmContainer,
    onSecondaryContainer = CalmOnSecondary,
    tertiary = Pink80,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

private val LightColorScheme = lightColorScheme(
    primary = WarmPrimary,
    onPrimary = WarmOnPrimary,
    primaryContainer = WarmContainer,
    onPrimaryContainer = WarmOnPrimary,
    secondary = CalmSecondary,
    onSecondary = CalmOnSecondary,
    secondaryContainer = CalmContainer,
    onSecondaryContainer = CalmOnSecondary,
    tertiary = Pink40,
    background = AppBackground,
    surface = SurfaceWhite,
    onBackground = TextBlack,
    onSurface = TextBlack,
)

@Composable
fun SilverLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic color to enforce our high contrast theme
    fontScale: Float = 1.0f, // 用户自定义字体缩放倍率
    statusBarColor: Color? = null, // 自定义状态栏颜色，null 时使用背景色
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    val finalStatusBarColor = statusBarColor ?: colorScheme.background
    val finalNavBarColor = statusBarColor ?: colorScheme.background // 导航栏与状态栏使用相同颜色
    val isLightStatusBar = statusBarColor == null && !darkTheme // 背景色时根据主题决定图标颜色
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = finalStatusBarColor.toArgb()
            window.navigationBarColor = finalNavBarColor.toArgb() // 设置导航栏颜色
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes, // Apply our new Shapes
        content = {
            val currentDensity = LocalDensity.current
            // 结合系统字体缩放和应用内设置 (fontScale * systemFontScale)
            // 或者直接使用应用设置覆盖。这里我们选择相乘，以保留系统无障碍设置的影响
            val customDensity = Density(
                density = currentDensity.density,
                fontScale = currentDensity.fontScale * fontScale
            )
            
            CompositionLocalProvider(
                LocalDensity provides customDensity
            ) {
                content()
            }
        }
    )
}
