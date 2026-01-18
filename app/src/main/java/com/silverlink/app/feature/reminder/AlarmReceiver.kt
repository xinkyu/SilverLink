package com.silverlink.app.feature.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.silverlink.app.R

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("MED_NAME") ?: "药品"
        val medDosage = intent.getStringExtra("MED_DOSAGE") ?: ""
        val medId = intent.getIntExtra("MED_ID", 0)
        
        Log.d("AlarmReceiver", "Alarm triggered for $medName")

        val fullScreenIntent = Intent(context, ReminderAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DOSAGE", medDosage)
            putExtra("MED_TIME", intent.getStringExtra("MED_TIME") ?: "")
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            medId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createReminderChannel(context)

        // Try to start activity directly (works best if app is in foreground)
        try {
            context.startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to start activity directly: ${e.message}")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("该吃药了")
            .setContentText(if (medDosage.isNotBlank()) "$medName · $medDosage" else medName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(medId, notification)
    }

    private fun createReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "用药提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用药时间提醒"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private companion object {
        const val CHANNEL_ID = "medication_reminders"
    }
}
