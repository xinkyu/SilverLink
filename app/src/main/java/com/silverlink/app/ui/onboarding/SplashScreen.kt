package com.silverlink.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// 橙色系背景
val WarmApricot = Color(0xFFF59A1B)

/**
 * 启动页
 * 暖杏色背景，中心显示两只手相握的Logo和应用名称
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.9f) }
    
    LaunchedEffect(Unit) {
        // 淡入动画
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
        // 停留一段时间
        delay(1500)
        // 进入下一页
        onSplashFinished()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmApricot),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha.value)
        ) {
            // Logo - 使用新图标
            Image(
                painter = painterResource(id = com.silverlink.app.R.drawable.ic_app_logo),
                contentDescription = "银龄守护Logo",
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale.value)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 应用名称
            Text(
                text = "SilverLink",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "银龄守护",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.9f),
                letterSpacing = 4.sp
            )
        }
    }
}

/**
 * 两只手相握的Logo图标
 * 使用Canvas绘制简笔画风格
 */
@Composable
fun HandsLogoIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF8D6E63)
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = width * 0.035f
        
        val paint = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        
        // 左手（从左下到中间）
        val leftHandPath = androidx.compose.ui.graphics.Path().apply {
            // 手掌轮廓
            moveTo(width * 0.15f, height * 0.7f)
            quadraticBezierTo(
                width * 0.2f, height * 0.5f,
                width * 0.35f, height * 0.45f
            )
            // 手指
            lineTo(width * 0.5f, height * 0.42f)
        }
        
        // 右手（从右下到中间）
        val rightHandPath = androidx.compose.ui.graphics.Path().apply {
            // 手掌轮廓
            moveTo(width * 0.85f, height * 0.7f)
            quadraticBezierTo(
                width * 0.8f, height * 0.5f,
                width * 0.65f, height * 0.45f
            )
            // 手指
            lineTo(width * 0.5f, height * 0.42f)
        }
        
        // 握手的连接部分（心形暗示）
        val heartPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.35f, height * 0.35f)
            quadraticBezierTo(
                width * 0.35f, height * 0.2f,
                width * 0.5f, height * 0.25f
            )
            quadraticBezierTo(
                width * 0.65f, height * 0.2f,
                width * 0.65f, height * 0.35f
            )
            quadraticBezierTo(
                width * 0.65f, height * 0.45f,
                width * 0.5f, height * 0.55f
            )
            quadraticBezierTo(
                width * 0.35f, height * 0.45f,
                width * 0.35f, height * 0.35f
            )
        }
        
        // 绘制
        drawPath(leftHandPath, tint, style = paint)
        drawPath(rightHandPath, tint, style = paint)
        drawPath(heartPath, tint, style = paint)
        
        // 填充心形（半透明）
        drawPath(heartPath, tint.copy(alpha = 0.3f))
    }
}
