package com.silverlink.app.feature.falldetection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.silverlink.app.R
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.EmergencyEventData
import com.silverlink.app.data.remote.QueryEmergencyRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 紧急事件轮询服务（家人端）
 * 后台定期查询云端是否有新的紧急事件
 */
class EmergencyPollingService : Service() {

    private var pollingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val TAG = "EmergencyPollingService"
        private const val CHANNEL_ID = "emergency_polling_channel"
        private const val NOTIFICATION_ID = 2001
        private const val ALERT_CHANNEL_ID = "emergency_alert_channel"
        private const val ALERT_NOTIFICATION_ID = 2002
        private const val POLLING_INTERVAL_MS = 30000L // 30秒轮询一次
        
        var isRunning = false
            private set
        
        // 记录已处理的事件ID，避免重复通知
        private val processedEventIds = mutableSetOf<String>()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "EmergencyPollingService onCreate")
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "EmergencyPollingService onStartCommand")
        
        // 只有家人端需要轮询
        val userPrefs = UserPreferences.getInstance(this)
        if (userPrefs.userConfig.value.role != UserRole.FAMILY) {
            Log.d(TAG, "Not family role, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        startPolling()
        
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "EmergencyPollingService onDestroy")
        pollingJob?.cancel()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    queryEmergencyEvents()
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling emergency events", e)
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private suspend fun queryEmergencyEvents() {
        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            
            val request = QueryEmergencyRequest(
                familyDeviceId = deviceId,
                onlyUnresolved = true
            )
            
            val response = CloudBaseService.api.queryEmergencyEvents(request)
            
            if (response.success && response.data != null) {
                val events = response.data
                for (event in events) {
                    if (event.id !in processedEventIds) {
                        // 新事件，发送通知
                        showEmergencyNotification(event)
                        processedEventIds.add(event.id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query emergency events", e)
        }
    }

    private fun showEmergencyNotification(event: EmergencyEventData) {
        val intent = Intent(this, FamilyEmergencyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("event_id", event.id)
            putExtra("elder_name", event.elderName)
            putExtra("latitude", event.latitude)
            putExtra("longitude", event.longitude)
            putExtra("timestamp", event.timestamp)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            event.id.hashCode(), 
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elderName = if (event.elderName.isNotEmpty()) event.elderName else "您的家人"
        
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ 紧急警报")
            .setContentText("$elderName 可能发生了跌倒！请立即查看")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(ALERT_NOTIFICATION_ID + event.id.hashCode(), notification)
        
        Log.d(TAG, "Emergency notification shown for event: ${event.id}")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // 后台轮询通知渠道
            val pollingChannel = NotificationChannel(
                CHANNEL_ID,
                "家人守护服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "持续监听老人安全状态"
            }
            manager.createNotificationChannel(pollingChannel)
            
            // 紧急警报通知渠道
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "紧急警报",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "老人跌倒紧急通知"
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("家人守护已开启")
            .setContentText("正在监听老人安全状态")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}
