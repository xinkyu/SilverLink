package com.silverlink.app.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silverlink.app.feature.chat.realtime.ConversationState

@Composable
fun RealtimeCallScreen(
    assistantName: String,
    conversationState: ConversationState,
    partialTranscript: String,
    onEndCall: () -> Unit,
    onStartCall: () -> Unit
) {
    LaunchedEffect(Unit) {
        onStartCall()
    }

    val stateText = when (conversationState) {
        ConversationState.Idle -> "准备中..."
        ConversationState.Listening -> "正在听..."
        ConversationState.Processing -> "思考中..."
        ConversationState.Speaking -> "正在说..."
        ConversationState.Interrupted -> "被打断..."
        is ConversationState.Error -> "发生错误"
    }

    val statusColor = when (conversationState) {
        ConversationState.Listening -> MaterialTheme.colorScheme.primary
        ConversationState.Speaking -> MaterialTheme.colorScheme.tertiary
        ConversationState.Error("error") -> MaterialTheme.colorScheme.error // Simple check, actual extraction below
        else -> MaterialTheme.colorScheme.secondary
    }

    // Pulse animation for active states
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (conversationState == ConversationState.Listening || conversationState == ConversationState.Speaking) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Avatar / Status Indicator
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = assistantName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stateText,
            style = MaterialTheme.typography.titleLarge,
            color = statusColor
        )
        
        if (conversationState is ConversationState.Error) {
             Text(
                text = conversationState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Live Transcript
        if (partialTranscript.isNotBlank() || conversationState == ConversationState.Listening) {
            Text(
                text = partialTranscript.ifBlank { "..." },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(60.dp) // Fixed height to prevent jumping
            )
        } else {
             Spacer(modifier = Modifier.height(60.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // End Call Button
        IconButton(
            onClick = onEndCall,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "挂断",
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
