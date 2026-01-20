package com.silverlink.app.feature.falldetection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.silverlink.app.R
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserPreferences.FallDetectionSensitivity
import kotlinx.coroutines.*
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * 跌倒检测前台服务
 * 使用加速度计检测跌倒事件（自由落体 → 撞击 → 静止）
 */
class FallDetectionService : Service(), SensorEventListener {
    
    companion object {
        private const val TAG = "FallDetectionService"
        private const val NOTIFICATION_CHANNEL_ID = "fall_detection_channel"
        private const val NOTIFICATION_ID = 2001
        
        // 检测状态
        private const val STATE_NORMAL = 0
        private const val STATE_FREE_FALL = 1
        private const val STATE_IMPACT = 2
        
        // 灵敏度配置 - 高灵敏度 (适合行动能力差的老人)
        private const val FREE_FALL_THRESHOLD_HIGH = 3.5f    // 收紧：需更明显的失重
        private const val IMPACT_THRESHOLD_HIGH = 25.0f      // 提高：需约 2.5G 撞击
        private const val STILLNESS_THRESHOLD_HIGH = 2.0f    
        
        // 灵敏度配置 - 中等灵敏度（推荐，优化防误触）
        private const val FREE_FALL_THRESHOLD_MEDIUM = 1.5f  // 下调：需接近完全失重
        private const val IMPACT_THRESHOLD_MEDIUM = 38.0f    // 提高：需约 3.8G 撞击 (很重的摔倒)
        private const val STILLNESS_THRESHOLD_MEDIUM = 1.5f
        
        // 灵敏度配置 - 低灵敏度 (仅严重摔倒)
        private const val FREE_FALL_THRESHOLD_LOW = 1.0f     // 极严：需完美自由落体
        private const val IMPACT_THRESHOLD_LOW = 45.0f       // 需约 4.5G 撞击
        private const val STILLNESS_THRESHOLD_LOW = 1.0f
        
        // 时间窗口
        private const val FREE_FALL_DURATION_MS = 250L      // 再次延长到250ms，彻底过滤手持晃动
        private const val IMPACT_WINDOW_MS = 1000L          // 1秒内撞击
        private const val STILLNESS_DURATION_MS = 3000L     // 静止3秒确认
        private const val DETECTION_COOLDOWN_MS = 5000L     
        
        // 备用检测：突然的剧烈冲击（防误触：大幅提高）
        // 原来 42 容易误触，现在 65 (约 6.6G)，只有极猛烈的摔倒才触发
        private const val SUDDEN_IMPACT_THRESHOLD = 65.0f
        
        
        const val ACTION_STOP_ALARM = "com.silverlink.app.action.STOP_ALARM"

        fun stopAlarm(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, FallDetectionService::class.java))
        }
    }
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var userPreferences: UserPreferences
    private var wakeLock: PowerManager.WakeLock? = null
    // 铃声播放器
    private var mediaPlayer: android.media.MediaPlayer? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 检测状态
    private var currentState = STATE_NORMAL
    private var freeFallStartTime = 0L
    private var impactTime = 0L
    private var stillnessStartTime = 0L
    private var lastDetectionTime = 0L
    
    // 当前灵敏度阈值
    private var freeFallThreshold = FREE_FALL_THRESHOLD_MEDIUM
    private var impactThreshold = IMPACT_THRESHOLD_MEDIUM
    private var stillnessThreshold = STILLNESS_THRESHOLD_MEDIUM
    
    // 加速度历史记录（用于检测静止）
    private val accelHistory = mutableListOf<Float>()
    private val historySize = 10
    
    // ========== ML 分类器和缓冲区 ==========
    private lateinit var mlClassifier: FallDetectionMLClassifier
    private val accelBuffer = AccelerometerBuffer()
    
    // ML 检测状态
    private var mlDetectionState = MLDetectionState.MONITORING
    private var mlConfirmationStartTime = 0L
    private val ML_CONFIRMATION_DURATION_MS = 1000L  // 缩短到1秒，加快响应
    
    private enum class MLDetectionState {
        MONITORING,      // 正常监控
        CONFIRMING,      // 确认中（等待静止）
        COOLDOWN         // 冷却期
    }
    
    override fun onCreate() {
        super.onCreate()
        try {
            Log.d(TAG, "Service Created")
            
            // 1. 创建通知渠道并启动前台服务
            createNotificationChannel()
            val notification = buildNotification()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 (API 29) 及以上必须指定服务类型（如果Manifest中声明了）
                // 尤其是在 Android 14 (API 34) 中是强制的
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            // 2. 获取 WakeLock 保持CPU唤醒（防止后台/熄屏时传感器停止）
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SilverLink:FallDetection"
                )
                // 设置24小时超时，防止永久持有
                wakeLock?.acquire(24 * 60 * 60 * 1000L) 
                Log.d(TAG, "WakeLock acquired for background detection")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire WakeLock", e)
            }
            
            // 3. 初始化用户配置
            userPreferences = UserPreferences.getInstance(applicationContext)
            updateSensitivityThresholds()
            
            // 4. 初始化传感器
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            if (accelerometer == null) {
                Log.e(TAG, "Accelerometer not available!")
                stopSelf()
                return
            }
            
            // 使用 SENSOR_DELAY_GAME 获得适中采样率（约50Hz）
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Accelerometer registered for fall detection with GAME delay")
            
            // 初始化 ML 分类器
            mlClassifier = FallDetectionMLClassifier(applicationContext)
            Log.d(TAG, "ML Classifier initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in FallDetectionService.onCreate", e)
            android.widget.Toast.makeText(this, "服务启动失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }
    
    private val ALERT_CHANNEL_ID = "FallAlertChannel"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 1. 低优先级通道，用于前台服务保活
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "跌倒检测服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SilverLink 跌倒检测后台服务"
            }
            manager.createNotificationChannel(serviceChannel)
            
            // 2. ★ 高优先级通道，用于跌倒警报（必须High才能弹出/唤醒）
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "跌倒警报",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "检测到跌倒时触发的紧急警报"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alertChannel)
        }
    }
    
    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, FallAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("安全守护已开启")
            .setContentText("正在监测跌倒...")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Must be high for full screen intent
            .setCategory(NotificationCompat.CATEGORY_ALARM)
        
        // Android 10+ background start restriction workaround
        // We use fullScreenIntent to allow activity launch from background
        builder.setFullScreenIntent(pendingIntent, true)
        
        return builder.build()
    }
    
    /**
     * 根据用户设置更新灵敏度阈值
     */
    private fun updateSensitivityThresholds() {
        when (userPreferences.getFallDetectionSensitivity()) {
            FallDetectionSensitivity.HIGH -> {
                freeFallThreshold = FREE_FALL_THRESHOLD_HIGH
                impactThreshold = IMPACT_THRESHOLD_HIGH
                stillnessThreshold = STILLNESS_THRESHOLD_HIGH
            }
            FallDetectionSensitivity.MEDIUM -> {
                freeFallThreshold = FREE_FALL_THRESHOLD_MEDIUM
                impactThreshold = IMPACT_THRESHOLD_MEDIUM
                stillnessThreshold = STILLNESS_THRESHOLD_MEDIUM
            }
            FallDetectionSensitivity.LOW -> {
                freeFallThreshold = FREE_FALL_THRESHOLD_LOW
                impactThreshold = IMPACT_THRESHOLD_LOW
                stillnessThreshold = STILLNESS_THRESHOLD_LOW
            }
        }
        Log.d(TAG, "Sensitivity updated: freeFall=$freeFallThreshold, impact=$impactThreshold, stillness=$stillnessThreshold")
    }
    
    // 用于调试日志的计数器
    private var logCounter = 0
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        
        val currentTime = System.currentTimeMillis()
        
        // 添加样本到 ML 缓冲区
        accelBuffer.addSample(x, y, z, magnitude, currentTime)
        
        // 每100次采样打印一次日志（大约2秒一次）
        logCounter++
        if (logCounter >= 100) {
            Log.d(TAG, "Sensor: mag=${String.format("%.2f", magnitude)} mlState=$mlDetectionState")
            logCounter = 0
        }
        
        // 冷却期内不检测
        if (mlDetectionState == MLDetectionState.COOLDOWN) {
            if (currentTime - lastDetectionTime > DETECTION_COOLDOWN_MS) {
                mlDetectionState = MLDetectionState.MONITORING
                accelBuffer.clear()
            }
            return
        }
        
        // 更新加速度历史
        accelHistory.add(magnitude)
        if (accelHistory.size > historySize) {
            accelHistory.removeAt(0)
        }
        
        // ★ 备用检测：剧烈撞击直接触发（绕过 ML）
        // 如果加速度超过 40 m/s²（约4G），直接进入确认
        if (magnitude > 40.0f && mlDetectionState == MLDetectionState.MONITORING) {
            Log.i(TAG, "STRONG IMPACT detected! mag=$magnitude -> entering confirmation")
            mlDetectionState = MLDetectionState.CONFIRMING
            mlConfirmationStartTime = currentTime
            return
        }
        
        // ========== ML 双重验证检测流程 ==========
        when (mlDetectionState) {
            MLDetectionState.MONITORING -> {
                // 调试：打印缓冲区状态
                if (logCounter == 0) {
                    Log.d(TAG, "Buffer: isFull=${accelBuffer.isFull()}")
                }
                
                // 只有缓冲区足够时才进行检测
                if (!accelBuffer.isFull()) return
                
                // 获取特征
                val features = accelBuffer.getFeatures()
                if (features == null) {
                    Log.d(TAG, "Features is null")
                    return
                }
                
                // 调试：打印特征
                Log.d(TAG, "Features: max=${String.format("%.1f", features.magMax)}, " +
                        "range=${String.format("%.1f", features.magRange)}, " +
                        "freeFall=${features.freeFallCount}")
                
                // 快速预筛选
                if (!mlClassifier.quickPrescreen(features)) {
                    Log.d(TAG, "Prescreen failed: max=${features.magMax}, range=${features.magRange}")
                    return
                }
                
                // ML 分类
                val result = mlClassifier.classify(features)
                
                Log.d(TAG, "ML Result: prob=${String.format("%.2f", result.fallProbability)}, " +
                        "conf=${String.format("%.2f", result.confidence)}, patterns=${result.detectedPatterns}")
                
                // 超过阈值，进入确认阶段
                if (result.fallProbability >= FallDetectionMLClassifier.FALL_PROBABILITY_THRESHOLD) {
                    Log.i(TAG, "ML detected possible fall (prob=${result.fallProbability}), entering confirmation...")
                    mlDetectionState = MLDetectionState.CONFIRMING
                    mlConfirmationStartTime = currentTime
                }
            }
            
            MLDetectionState.CONFIRMING -> {
                // 确认阶段：检查是否保持相对静止
                val avgMagnitude = accelBuffer.getRecentAverage(10)
                val deviation = abs(avgMagnitude - 9.8f)
                
                // 如果检测到剧烈活动，取消警报（说明人在动）
                // 放宽条件：只有非常剧烈的活动才取消
                if (magnitude > 25.0f) {
                    Log.d(TAG, "Strong activity detected during confirmation, resetting...")
                    mlDetectionState = MLDetectionState.MONITORING
                    accelBuffer.clear()
                    return
                }
                
                // 如果加速度在合理范围内（不需要完全静止）
                // 放宽静止条件
                if (deviation < stillnessThreshold + 1.0f) {
                    if (currentTime - mlConfirmationStartTime > ML_CONFIRMATION_DURATION_MS) {
                        // 确认跌倒！
                        Log.i(TAG, "FALL CONFIRMED! Triggering alert...")
                        onFallDetected()
                        mlDetectionState = MLDetectionState.COOLDOWN
                        lastDetectionTime = currentTime
                        return
                    }
                }
                // 不再重置计时器，让时间自然流逝
                
                // 超时未确认，重置（延长超时时间）
                if (currentTime - mlConfirmationStartTime > ML_CONFIRMATION_DURATION_MS * 3) {
                    Log.d(TAG, "Confirmation timeout, resetting...")
                    mlDetectionState = MLDetectionState.MONITORING
                    accelBuffer.clear()
                }
            }
            
            MLDetectionState.COOLDOWN -> {
                // 已在前面处理
            }
        }
    }

    
    /**
     * 处理加速度数据，检测跌倒模式
     */
    private fun processAccelerometerData(magnitude: Float, currentTime: Long) {
        when (currentState) {
            STATE_NORMAL -> {
                // 检测自由落体（加速度接近0）
                if (magnitude < freeFallThreshold) {
                    if (freeFallStartTime == 0L) {
                        freeFallStartTime = currentTime
                    } else if (currentTime - freeFallStartTime > FREE_FALL_DURATION_MS) {
                        Log.d(TAG, "Free fall detected! magnitude=$magnitude")
                        currentState = STATE_FREE_FALL
                        impactTime = 0L
                    }
                } else {
                    freeFallStartTime = 0L
                }
            }
            
            STATE_FREE_FALL -> {
                // 检测撞击（加速度突然增大）
                if (magnitude > impactThreshold) {
                    Log.d(TAG, "Impact detected! magnitude=$magnitude")
                    currentState = STATE_IMPACT
                    impactTime = currentTime
                    stillnessStartTime = 0L
                } else if (currentTime - freeFallStartTime > IMPACT_WINDOW_MS) {
                    // 超时未检测到撞击，重置
                    resetState()
                }
            }
            
            STATE_IMPACT -> {
                // 检测静止（加速度稳定在重力附近）
                val avgMagnitude = if (accelHistory.isNotEmpty()) {
                    accelHistory.average().toFloat()
                } else {
                    magnitude
                }
                
                // 静止检测：加速度接近重力（9.8 m/s²）且变化小
                val deviation = kotlin.math.abs(avgMagnitude - 9.8f)
                if (deviation < stillnessThreshold) {
                    if (stillnessStartTime == 0L) {
                        stillnessStartTime = currentTime
                    } else if (currentTime - stillnessStartTime > STILLNESS_DURATION_MS) {
                        // 检测到跌倒！
                        Log.i(TAG, "FALL DETECTED! Triggering alert...")
                        onFallDetected()
                        resetState()
                    }
                } else {
                    stillnessStartTime = 0L
                }
                
                // 超时未检测到静止，重置
                if (currentTime - impactTime > STILLNESS_DURATION_MS * 2) {
                    resetState()
                }
            }
        }
    }
    
    private fun resetState() {
        currentState = STATE_NORMAL
        freeFallStartTime = 0L
        impactTime = 0L
        stillnessStartTime = 0L
    }
    
    /**
     * 检测到跌倒，触发警报
     */
    /**
     * 检测到跌倒，触发警报
     */
    private fun onFallDetected() {
        lastDetectionTime = System.currentTimeMillis()
        Log.i(TAG, "FALL DETECTED! Initiating full screen alert...")
        
        // 1. 立即强制点亮屏幕 (尝试多次以确保生效)
        wakeScreen()
        
        // 2. 播放紧急警报音 (即使屏幕没亮，声音也能提醒)
        playAlarmSound()
        
        // 3. 震动提示
        vibrateDevice()
        
        // 4. 准备启动 Activity 的 Intent
        val intent = Intent(this, FallAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra("from_service", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 5. 发送高优先级全屏通知
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("检测到跌倒！")
            .setContentText("正在启动紧急呼救...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
            
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
        
        // 6. 尝试直接启动
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed", e)
        }
    }

    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val screenWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "SilverLink:FallDetectedWakeup"
            )
            screenWakeLock.acquire(10000) // 延长到10秒
            Log.d(TAG, "Screen WakeLock acquired in Service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen in service", e)
        }
    }

    private fun playAlarmSound() {
        try {
            val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            mediaPlayer = android.media.MediaPlayer.create(applicationContext, notification).apply {
                isLooping = true
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound", e)
        }
    }
    
    private fun vibrateDevice() {
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
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
        }
        
        // 更新灵敏度设置
        updateSensitivityThresholds()
        return START_STICKY
    }
    
    private fun stopAlarm() {
        try {
            // 停止声音
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                Log.d(TAG, "Alarm stopped")
            }
            
            // 取消全屏通知 (ID: 2002)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID + 1)
            Log.d(TAG, "Alert notification cancelled")
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        
        stopAlarm()
        
        // 释放 WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        
        Log.d(TAG, "Service Destroyed")
    }
    

    
    override fun onBind(intent: Intent?): IBinder? = null
}
