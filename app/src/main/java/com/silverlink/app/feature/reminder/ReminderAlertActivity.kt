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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.ui.theme.SilverLinkTheme
import com.silverlink.app.ui.theme.WarmOrange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
     */
    private fun markMedicationAsTaken(medId: Int) {
        if (medId <= 0) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = SilverLinkApp.database.medicationDao()
                // 查询该药品并更新状态
                dao.getAllMedications().collect { medications ->
                    val medication = medications.find { it.id == medId }
                    if (medication != null && !medication.isTakenToday) {
                        val updated = medication.copy(isTakenToday = true)
                        dao.updateMedication(updated)
                    }
                    return@collect
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmOrange)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "吃药时间到了！",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = medName,
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        if (medDosage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "剂量: $medDosage",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onConfirmed,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = WarmOrange
            )
        ) {
            Text(
                text = "我吃过了",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.3f),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "稍后提醒（10分钟）",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
