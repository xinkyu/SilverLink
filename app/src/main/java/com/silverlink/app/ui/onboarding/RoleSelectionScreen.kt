package com.silverlink.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 身份选择页
 * 屏幕上下均分两个巨大卡片：我是长辈 / 我是家人
 */
@Composable
fun RoleSelectionScreen(
    onElderSelected: () -> Unit,
    onFamilySelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmApricot)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 标题
        Text(
            text = "欢迎使用银龄守护",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "请选择您的身份",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF8D6E63),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 上卡片 - 我是长辈
        RoleCard(
            modifier = Modifier.weight(1f),
            emoji = "👴",
            title = "我是长辈",
            subtitle = "开始使用我的智能伴侣",
            gradientColors = listOf(
                Color(0xFFFFE0B2),
                Color(0xFFFFCC80)
            ),
            onClick = onElderSelected
        )
        
        // 下卡片 - 我是家人
        RoleCard(
            modifier = Modifier.weight(1f),
            emoji = "👩‍💼",
            title = "我是家人",
            subtitle = "为长辈配置智能伴侣",
            gradientColors = listOf(
                Color(0xFFB2EBF2),
                Color(0xFF80DEEA)
            ),
            onClick = onFamilySelected
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 角色选择卡片
 */
@Composable
fun RoleCard(
    modifier: Modifier = Modifier,
    emoji: String,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Emoji 图标
                Text(
                    text = emoji,
                    fontSize = 80.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 标题
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF37474F)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 副标题
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF546E7A)
                )
            }
        }
    }
}
