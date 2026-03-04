package com.silverlink.wear.ui

import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverlink.wear.service.SOSHelper
import com.silverlink.wear.ui.theme.WatchTheme

class FallAlertActivity : ComponentActivity() {

    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WatchTheme {
                FallAlertScreen(
                    onCancel = {
                        countdownTimer?.cancel()
                        finish()
                    },
                    onTimerCreated = { timer -> countdownTimer = timer },
                    onConfirmFall = {
                        SOSHelper.triggerSOS(
                            this@FallAlertActivity,
                            SOSHelper.SOSTriggerSource.FALL_DETECTION
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}

@Composable
private fun FallAlertScreen(
    onCancel: () -> Unit,
    onTimerCreated: (CountDownTimer) -> Unit,
    onConfirmFall: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(30) }

    LaunchedEffect(Unit) {
        val timer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdown = (millisUntilFinished / 1000).toInt()
            }
            override fun onFinish() {
                onConfirmFall()
            }
        }
        onTimerCreated(timer)
        timer.start()
    }

    val pulseAnim = rememberInfiniteTransition(label = "alert_pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "检测到跌倒！",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF5350),
                modifier = Modifier.scale(scale)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Countdown circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF5350).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$countdown",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF5350)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "秒后自动呼救",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Text("我没事", fontSize = 13.sp)
            }
        }
    }
}
