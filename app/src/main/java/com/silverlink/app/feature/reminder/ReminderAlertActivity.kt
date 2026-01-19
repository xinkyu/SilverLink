package com.silverlink.app.feature.reminder

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.ui.theme.SilverLinkTheme
import com.silverlink.app.ui.theme.WarmOrange
import com.silverlink.app.ui.theme.GradientStart
import com.silverlink.app.ui.theme.GradientEnd
import com.silverlink.app.ui.theme.CardSurface
import com.silverlink.app.ui.theme.TextPrimary
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class ReminderAlertActivity : ComponentActivity() {
    
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val medId = intent.getIntExtra("MED_ID", 0)
        val medName = intent.getStringExtra("MED_NAME") ?: "该吃药了"
        val medDosage = intent.getStringExtra("MED_DOSAGE") ?: ""

        playRingtone()

        // 唤醒屏幕并显示在锁屏之上
        turnScreenOnAndKeyguard()

        // 取消通知（因为已经全屏显示了）
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medId)

        setContent {
            SilverLinkTheme {
                ReminderAlertScreen(
                    medName = medName,
                    medDosage = medDosage,
                    onConfirmed = {
                        stopRingtone()
                        // 标记药品为已服用
                        markMedicationAsTaken(medId)
                        finish()
                    },
                    onSnooze = {
                        stopRingtone()
                        // 设置10分钟后再次提醒
                        scheduleSnooze(medId, medName, medDosage)
                        finish()
                    }
                )
            }
        }
    }

    /**
     * 设置稍后提醒（10分钟后）
     */
    private fun scheduleSnooze(medId: Int, medName: String, medDosage: String) {
        val alarmScheduler = AlarmScheduler(this)
        alarmScheduler.scheduleSnooze(medId, medName, medDosage)
    }

    /**
     * 将药品标记为今日已服用
     * 同时上传到云端并保存本地历史记录
     */
    private fun markMedicationAsTaken(medId: Int) {
        if (medId <= 0) return
        
        val scheduledTime = intent.getStringExtra("MED_TIME") 
            ?: java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val medicationDao = SilverLinkApp.database.medicationDao()
                val historyDao = SilverLinkApp.database.historyDao()
                val syncRepo = com.silverlink.app.data.repository.SyncRepository.getInstance(this@ReminderAlertActivity)
                
                // 查询该药品并更新状态（一次性）
                val medications = medicationDao.getAllMedications().first()
                val medication = medications.find { it.id == medId }
                if (medication != null) {
                    // 防重复写入同一时间点
                    val existingCount = historyDao.getMedicationLogCount(currentDate, medId, scheduledTime)
                    if (existingCount == 0) {
                        // 保存本地历史记录
                        val logEntity = com.silverlink.app.data.local.entity.MedicationLogEntity(
                            medicationId = medId,
                            medicationName = medication.name,
                            dosage = medication.dosage,
                            scheduledTime = scheduledTime,
                            status = "taken",
                            date = currentDate
                        )
                        historyDao.insertMedicationLog(logEntity)

                        // 上传到云端（不阻塞）
                        syncRepo.syncMedicationTaken(
                            medicationId = medId,
                            medicationName = medication.name,
                            dosage = medication.dosage,
                            scheduledTime = scheduledTime,
                            status = "taken"
                        )
                    }

                    // 更新药品状态（当日已服用）
                    if (!medication.isTakenToday) {
                        val updated = medication.copy(isTakenToday = true)
                        medicationDao.updateMedication(updated)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playRingtone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
    }

    private fun turnScreenOnAndKeyguard() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
fun ReminderAlertScreen(
    medName: String,
    medDosage: String,
    onConfirmed: () -> Unit,
    onSnooze: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            // Main Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Animated Icon
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .scale(scale)
                                .background(
                                    color = GradientStart.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                        )
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = GradientEnd
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "吃药时间到了",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = medName,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        if (medDosage.isNotEmpty()) {
                            Text(
                                text = "剂量: $medDosage",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onConfirmed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardSurface,
                        contentColor = GradientEnd
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "我吃过了",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                TextButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "稍后提醒 (10分钟)",
                        style = MaterialTheme.typography.titleMedium,
                        color = CardSurface.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
