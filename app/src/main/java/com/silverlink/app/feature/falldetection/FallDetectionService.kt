package com.silverlink.app.feature.falldetection

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.silverlink.app.MainActivity
import com.silverlink.app.R
import kotlin.math.sqrt

/**
 * 跌倒检测前台服务
 * 使用加速度传感器实时监测用户运动状态，检测可能的跌倒事件
 */
class FallDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    // 跌倒检测参数 - 根据灵敏度动态设置
    private var fallThreshold = 0.5f      // 自由落体阈值 (g值)
    private var impactThreshold = 2.0f    // 撞击阈值 (g值)
    private var stillnessThreshold = 0.8f // 静止阈值
    
    // 检测状态
    private var lastAcceleration = 0f
    private var freeFallDetected = false
    private var freeFallTime = 0L
    private var impactDetected = false
    private var impactTime = 0L
    
    // 防止重复触发
    private var lastFallAlertTime = 0L
    private val minAlertInterval = 30000L // 最小警报间隔30秒
    
    companion object {
        private const val TAG = "FallDetectionService"
        private const val CHANNEL_ID = "fall_detection_channel"
        private const val NOTIFICATION_ID = 1001
        
        // 服务状态
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FallDetectionService onCreate")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        if (accelerometer == null) {
            Log.e(TAG, "Accelerometer not available on this device")
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FallDetectionService onStartCommand")
        
        // 加载灵敏度设置
        loadSensitivitySettings()
        
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // 注册传感器监听
        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME // 约50ms采样间隔
            )
        }
        
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FallDetectionService onDestroy")
        sensorManager.unregisterListener(this)
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            if (sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                processAccelerometerData(sensorEvent.values)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要处理精度变化
    }

    /**
     * 处理加速度数据，检测跌倒
     */
    private fun processAccelerometerData(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        
        // 计算合加速度 (以重力加速度g为单位)
        val acceleration = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        
        val currentTime = System.currentTimeMillis()
        
        // 检测自由落体 (加速度接近0)
        if (acceleration < fallThreshold && !freeFallDetected) {
            freeFallDetected = true
            freeFallTime = currentTime
            Log.d(TAG, "Free fall detected, acceleration: $acceleration")
        }
        
        // 检测撞击 (自由落体后加速度突然增大)
        if (freeFallDetected && !impactDetected) {
            val timeSinceFreeFall = currentTime - freeFallTime
            
            // 自由落体后1000ms内检测撞击 - 增加时间窗口以提高检测率
            if (timeSinceFreeFall < 1000 && acceleration > impactThreshold) {
                impactDetected = true
                impactTime = currentTime
                Log.d(TAG, "Impact detected, acceleration: $acceleration")
            } else if (timeSinceFreeFall > 1000) {
                // 超时重置
                freeFallDetected = false
            }
        }
        
        // 检测撞击后的静止状态
        if (impactDetected) {
            val timeSinceImpact = currentTime - impactTime
            
            // 撞击后2秒内检测静止状态
            if (timeSinceImpact > 500 && timeSinceImpact < 2000) {
                val accelerationChange = kotlin.math.abs(acceleration - lastAcceleration)
                
                if (accelerationChange < stillnessThreshold) {
                    // 检测到跌倒模式：自由落体 -> 撞击 -> 静止
                    if (currentTime - lastFallAlertTime > minAlertInterval) {
                        Log.d(TAG, "Fall pattern detected! Triggering alert.")
                        triggerFallAlert()
                        lastFallAlertTime = currentTime
                    }
                }
            } else if (timeSinceImpact > 2000) {
                // 超时重置
                resetDetectionState()
            }
        }
        
        lastAcceleration = acceleration
    }
    
    /**
     * 加载灵敏度设置
     * 根据用户设置的灵敏度级别调整检测阈值
     */
    private fun loadSensitivitySettings() {
        val prefs = getSharedPreferences("silverlink_user_prefs", Context.MODE_PRIVATE)
        val sensitivity = prefs.getInt("fall_detection_sensitivity", 1) // 默认为中
        
        when (sensitivity) {
            0 -> { // 高灵敏度 - 更容易触发
                fallThreshold = 0.6f
                impactThreshold = 1.5f
                stillnessThreshold = 1.0f
                Log.d(TAG, "Sensitivity set to HIGH")
            }
            1 -> { // 中灵敏度 - 默认
                fallThreshold = 0.5f
                impactThreshold = 2.0f
                stillnessThreshold = 0.8f
                Log.d(TAG, "Sensitivity set to MEDIUM")
            }
            2 -> { // 低灵敏度 - 减少误报
                fallThreshold = 0.4f
                impactThreshold = 2.5f
                stillnessThreshold = 0.5f
                Log.d(TAG, "Sensitivity set to LOW")
            }
        }
    }
    
    /**
     * 重置检测状态
     */
    private fun resetDetectionState() {
        freeFallDetected = false
        impactDetected = false
        freeFallTime = 0L
        impactTime = 0L
    }
    
    /**
     * 触发跌倒警报
     */
    private fun triggerFallAlert() {
        Log.d(TAG, "Triggering fall alert")
        
        // 振动提醒
        vibrate()
        
        // 重置检测状态
        resetDetectionState()
        
        // 启动跌倒警报界面
        val intent = Intent(this, FallAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }
    
    /**
     * 振动提醒
     */
    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "跌倒检测服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "持续监测您的运动状态，保障您的安全"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安全守护已开启")
            .setContentText("正在持续监测您的安全状态")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
