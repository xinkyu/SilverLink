package com.silverlink.app.feature.falldetection

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverlink.app.ui.theme.SilverLinkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "FallAlertActivity"
private const val COUNTDOWN_SECONDS = 15

/**
 * 跌倒警报全屏界面
 * 15秒倒计时，可取消或立即呼叫
 */
class FallAlertActivity : ComponentActivity() {
    
    private var ringtone: Ringtone? = null
    private var countDownTimer: CountDownTimer? = null
    private val emergencyNotifier by lazy { EmergencyNotifier(this) }
    private var alertType: EmergencyNotifier.AlertType = EmergencyNotifier.AlertType.FALL
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alertType = when (intent.getStringExtra("alert_type")) {
            "inactivity" -> EmergencyNotifier.AlertType.INACTIVITY
            else -> EmergencyNotifier.AlertType.FALL
        }
        
        // 唤醒屏幕并显示在锁屏之上
        turnScreenOnAndKeyguard()
        
        // 播放警报铃声
        playAlertSound()
        
        setContent {
            SilverLinkTheme {
                var remainingSeconds by remember { mutableIntStateOf(COUNTDOWN_SECONDS) }
                var isSending by remember { mutableStateOf(false) }
                
                // 启动倒计时
                LaunchedEffect(Unit) {
                    countDownTimer = object : CountDownTimer(
                        COUNTDOWN_SECONDS * 1000L,
                        1000L
                    ) {
                        override fun onTick(millisUntilFinished: Long) {
                            remainingSeconds = (millisUntilFinished / 1000).toInt()
                        }
                        
                        override fun onFinish() {
                            remainingSeconds = 0
                            // 倒计时结束，发送紧急通知
                            sendEmergencyNotification()
                        }
                    }.start()
                }
                
                FallAlertScreen(
                    title = if (alertType == EmergencyNotifier.AlertType.INACTIVITY) "久坐无响应！" else "检测到跌倒！",
                    remainingSeconds = remainingSeconds,
                    isSending = isSending,
                    onImFine = {
                        // 用户表示没事，取消警报
                        cancelAlert()
                    },
                    onEmergencyCall = {
                        // 立即发送紧急通知
                        isSending = true
                        sendEmergencyNotification()
                    }
                )
            }
        }
    }
    
    private fun sendEmergencyNotification() {
        stopAlertSound()
        // 停止服务端的报警音
        if (alertType == EmergencyNotifier.AlertType.FALL) {
            FallDetectionService.stopAlarm(this)
        }
        
        countDownTimer?.cancel()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sentCount = emergencyNotifier.sendEmergencyNotification(alertType)
                Log.i(TAG, "Emergency notification sent to $sentCount contacts")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send emergency notification", e)
            }
            finish()
        }
    }
    
    private fun cancelAlert() {
        Log.d(TAG, "Alert cancelled by user")
        stopAlertSound()
        // 停止服务端的报警音
        if (alertType == EmergencyNotifier.AlertType.FALL) {
            FallDetectionService.stopAlarm(this)
        }
        
        countDownTimer?.cancel()
        finish()
    }
    
    private fun playAlertSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alert sound", e)
        }
    }
    
    private fun stopAlertSound() {
        ringtone?.stop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAlertSound()
        // 确保服务端的报警音也停止
        if (alertType == EmergencyNotifier.AlertType.FALL) {
            FallDetectionService.stopAlarm(this)
        }
        
        countDownTimer?.cancel()
    }
    
    private fun turnScreenOnAndKeyguard() {
        // 1. 使用 PowerManager 强制点亮屏幕 (最强力的方式)
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.FULL_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "SilverLink:FallAlertWakeLock"
            )
            wakeLock.acquire(3000) // 点亮3秒后释放，后续由 WindowFlags 保持
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock for screen on", e)
        }

        // 2. 标准 API 设置
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // 3. 兼容旧版 WindowFlags (作为保险)
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
fun FallAlertScreen(
    title: String,
    remainingSeconds: Int,
    isSending: Boolean,
    onImFine: () -> Unit,
    onEmergencyCall: () -> Unit
) {
    val dangerRed = Color(0xFFE53935)
    val dangerRedLight = Color(0xFFFF6F60)
    val safeGreen = Color(0xFF43A047)
    
    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(dangerRed, dangerRedLight)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            // 警告图标（脉冲动画）
            Box(
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(scale)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                )
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.White
                )
            }
            
            // 主标题
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            // 倒计时卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(60.dp),
                            color = dangerRed,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "正在发送紧急通知...",
                            style = MaterialTheme.typography.titleMedium,
                            color = dangerRed
                        )
                    } else {
                        // 倒计时数字
                        Text(
                            text = "$remainingSeconds",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 72.sp
                            ),
                            color = dangerRed
                        )
                        
                        Text(
                            text = "秒后将自动通知紧急联系人",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        
                        // 倒计时进度条
                        LinearProgressIndicator(
                            progress = { remainingSeconds / COUNTDOWN_SECONDS.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = dangerRed,
                            trackColor = Color.LightGray,
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 按钮区域
            if (!isSending) {
                // "我没事"按钮 - 大而醒目
                Button(
                    onClick = onImFine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = safeGreen
                    ),
                    shape = RoundedCornerShape(36.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = "我没事",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // "紧急呼叫"按钮
                OutlinedButton(
                    onClick = onEmergencyCall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "立即呼叫紧急联系人",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
