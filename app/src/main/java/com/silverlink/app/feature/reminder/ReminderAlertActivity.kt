package com.silverlink.app.feature.reminder

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.silverlink.app.ui.theme.SilverLinkTheme
import com.silverlink.app.ui.theme.WarmOrange

class ReminderAlertActivity : ComponentActivity() {
    
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val medName = intent.getStringExtra("MED_NAME") ?: "该吃药了"
        val medDosage = intent.getStringExtra("MED_DOSAGE") ?: ""

        playRingtone()

        setContent {
            SilverLinkTheme {
                ReminderAlertScreen(
                    medName = medName,
                    medDosage = medDosage,
                    onConfirmed = {
                        stopRingtone()
                        finish()
                        // In a real app, we would mark it as taken in DB here
                    }
                )
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
}

@Composable
fun ReminderAlertScreen(
    medName: String,
    medDosage: String,
    onConfirmed: () -> Unit
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

        Spacer(modifier = Modifier.height(64.dp))
        
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
    }
}
