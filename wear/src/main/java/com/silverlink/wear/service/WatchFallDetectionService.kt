package com.silverlink.wear.service

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
import com.silverlink.shared.detection.AccelerometerBuffer
import com.silverlink.shared.detection.FallClassifier
import com.silverlink.shared.detection.FallDetectionDLClassifier
import com.silverlink.wear.ui.FallAlertActivity
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

class WatchFallDetectionService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "WatchFallDetection"
        private const val NOTIFICATION_CHANNEL_ID = "watch_fall_detection"
        private const val NOTIFICATION_ID = 3001
        private const val DETECTION_COOLDOWN_MS = 5000L
        private const val ML_CONFIRMATION_DURATION_MS = 1000L
        private const val STRONG_IMPACT_THRESHOLD = 40.0f

        fun start(context: Context) {
            val intent = Intent(context, WatchFallDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchFallDetectionService::class.java))
        }
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val accelBuffer = AccelerometerBuffer()
    
    // 深度学习分类器
    private var dlClassifier: FallDetectionDLClassifier? = null
    private var useDLClassifier = false
    
    // 降级后备：规则分类器
    private val fallbackClassifier = FallClassifier(
        logger = FallClassifier.Logger { tag, msg -> Log.d(tag, msg) }
    )

    private enum class DetectionState { MONITORING, CONFIRMING, COOLDOWN }

    private var detectionState = DetectionState.MONITORING
    private var confirmationStartTime = 0L
    private var lastDetectionTime = 0L
    private var logCounter = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // WakeLock
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SilverLinkWear:FallDetection"
            )
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }

        // Sensor init
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.e(TAG, "Accelerometer not available")
            stopSelf()
            return
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        
        // 初始化 DL 分类器
        try {
            val modelBytes = assets.open("fall_detection_model.onnx").use { it.readBytes() }
            dlClassifier = FallDetectionDLClassifier(
                modelBytes = modelBytes,
                logger = FallDetectionDLClassifier.Logger { tag, msg -> Log.d(tag, msg) }
            )
            useDLClassifier = true
            Log.i(TAG, "✅ Watch DL Classifier loaded (ONNX)")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Watch DL model load failed, using rule-based: ${e.message}")
            useDLClassifier = false
        }
        
        Log.d(TAG, "Watch fall detection service started")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "跌倒检测",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "手表跌倒检测服务"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("安全守护已开启")
            .setContentText("正在监测...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val currentTime = System.currentTimeMillis()

        accelBuffer.addSample(x, y, z, magnitude, currentTime)

        logCounter++
        if (logCounter >= 100) {
            Log.d(TAG, "Sensor: mag=${String.format("%.2f", magnitude)} state=$detectionState")
            logCounter = 0
        }

        if (detectionState == DetectionState.COOLDOWN) {
            if (currentTime - lastDetectionTime > DETECTION_COOLDOWN_MS) {
                detectionState = DetectionState.MONITORING
                accelBuffer.clear()
            }
            return
        }

        // Strong impact bypass
        if (magnitude > STRONG_IMPACT_THRESHOLD && detectionState == DetectionState.MONITORING) {
            Log.i(TAG, "Strong impact: mag=$magnitude")
            detectionState = DetectionState.CONFIRMING
            confirmationStartTime = currentTime
            return
        }

        when (detectionState) {
            DetectionState.MONITORING -> {
                if (!accelBuffer.isFull()) return
                val features = accelBuffer.getFeatures() ?: return
                
                if (useDLClassifier && dlClassifier != null) {
                    // DL 路径
                    if (!dlClassifier!!.quickPrescreen(features)) return
                    val rawSequence = accelBuffer.getRawSequence()
                    val result = dlClassifier!!.classify(rawSequence)
                    if (result.fallProbability >= FallDetectionDLClassifier.FALL_PROBABILITY_THRESHOLD) {
                        Log.i(TAG, "DL: possible fall (prob=${result.fallProbability})")
                        detectionState = DetectionState.CONFIRMING
                        confirmationStartTime = currentTime
                    }
                } else {
                    // 降级路径
                    if (!fallbackClassifier.quickPrescreen(features)) return
                    val result = fallbackClassifier.classify(features)
                    if (result.fallProbability >= FallClassifier.FALL_PROBABILITY_THRESHOLD) {
                        Log.i(TAG, "Fallback: possible fall (prob=${result.fallProbability})")
                        detectionState = DetectionState.CONFIRMING
                        confirmationStartTime = currentTime
                    }
                }
            }
            DetectionState.CONFIRMING -> {
                if (magnitude > 25.0f) {
                    detectionState = DetectionState.MONITORING
                    accelBuffer.clear()
                    return
                }

                val avgMag = accelBuffer.getRecentAverage(10)
                val deviation = abs(avgMag - 9.8f)
                if (deviation < 3.0f && currentTime - confirmationStartTime > ML_CONFIRMATION_DURATION_MS) {
                    Log.i(TAG, "FALL CONFIRMED on watch!")
                    onFallDetected()
                    detectionState = DetectionState.COOLDOWN
                    lastDetectionTime = currentTime
                    return
                }

                if (currentTime - confirmationStartTime > ML_CONFIRMATION_DURATION_MS * 3) {
                    detectionState = DetectionState.MONITORING
                    accelBuffer.clear()
                }
            }
            DetectionState.COOLDOWN -> { /* handled above */ }
        }
    }

    private fun onFallDetected() {
        // Vibrate
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate failed", e)
        }

        // Launch alert activity
        val intent = Intent(this, FallAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start FallAlertActivity", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        dlClassifier?.close()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.d(TAG, "Watch fall detection service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
