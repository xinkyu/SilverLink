package com.silverlink.app.feature.proactive

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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.silverlink.app.R
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.Dialect
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.dao.HistoryDao
import com.silverlink.app.data.local.dao.CognitiveLogDao
import com.silverlink.app.data.local.dao.MemoryPhotoDao
import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest
import com.silverlink.app.feature.chat.AudioPlayerHelper
import com.silverlink.app.feature.chat.TextToSpeechService
import com.silverlink.app.feature.falldetection.FallAlertActivity
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import android.provider.Settings
import java.util.Calendar
import java.util.TimeZone

/**
 * 主动关怀服务
 * 检测老人久坐不动，主动唤醒并播放个性化问候语
 * 
 * 特性：
 * - 前台服务，确保后台持续运行
 * - 使用 App 统一的阿里云 CosyVoice 语音通道
 * - AI 生成个性化唤醒词
 */
class ProactiveInteractionService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "ProactiveService"
        private const val NOTIFICATION_CHANNEL_ID = "proactive_service_channel"
        private const val NOTIFICATION_ID = 1001

        private const val ALERT_CHANNEL_ID = "proactive_inactivity_alert_channel"
        private const val ALERT_NOTIFICATION_ID = 1101
        
        private const val CHECK_INTERVAL_MS = 60 * 1000L // Check every 1 minute
        
        // Thresholds
        private const val INACTIVITY_THRESHOLD_MS_DEBUG = 60 * 1000L // 1 minute for debug
        private const val INACTIVITY_THRESHOLD_MS_REAL = 3 * 60 * 60 * 1000L // 3 hours
        
        // Use debug threshold for demo purposes, switch to REAL for production
        private const val INACTIVITY_THRESHOLD_MS = INACTIVITY_THRESHOLD_MS_DEBUG 
        
        // 连续唤醒失败次数阈值，超过此值向家人发送警报
        private const val MAX_CONSECUTIVE_FAILURES = 2
        
        // 加速度计移动检测阈值 (m/s^2)
        private const val ACCEL_MOVEMENT_THRESHOLD = 1.5f

        fun start(context: Context) {
            val intent = Intent(context, ProactiveInteractionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProactiveInteractionService::class.java))
        }
    }

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null

    // App 统一语音服务（阿里云 CosyVoice）
    private lateinit var ttsService: TextToSpeechService
    private lateinit var audioPlayer: AudioPlayerHelper
    
    // 用户配置
    private lateinit var userPreferences: UserPreferences
    private lateinit var historyDao: HistoryDao
    private lateinit var memoryPhotoDao: MemoryPhotoDao
    private lateinit var cognitiveLogDao: CognitiveLogDao

    private var lastMovementTime = AtomicLong(System.currentTimeMillis())
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var checkJob: Job? = null

    // For Step Counter logic
    private var lastStepCount = -1f
    
    // For Accelerometer logic (backup movement detection)
    private var lastAccelMagnitude = 0f
    
    // 连续唤醒失败计数器（唤醒后用户未打开App）
    private val consecutiveWakeUpFailures = AtomicInteger(0)
    private var lastWakeUpTime = 0L
    // 真正的用户移动时间（基于计步器变化，不是定时器重置）
    private var lastRealMovementTime = AtomicLong(0L)
    private val lastQuizPromptTime = AtomicLong(0L)
    private val deviceId: String by lazy {
        Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val QUIZ_PROMPT_INTERVAL_MS = 24 * 60 * 60 * 1000L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")

        // 0. 检查开关，未开启则退出
        userPreferences = UserPreferences.getInstance(applicationContext)
        if (!userPreferences.isProactiveInteractionEnabled()) {
            Log.i(TAG, "Proactive interaction disabled, stopping service")
            stopSelf()
            return
        }
        
        // 1. 创建通知渠道并启动前台服务
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在监测久坐无响应..."))
        
        // 2. 初始化语音服务（使用 App 统一的 CosyVoice）
        ttsService = TextToSpeechService()
        audioPlayer = AudioPlayerHelper(applicationContext)
        
        // 3. 初始化用户配置和历史记录访问
        historyDao = AppDatabase.getInstance(applicationContext).historyDao()
        memoryPhotoDao = AppDatabase.getInstance(applicationContext).memoryPhotoDao()
        cognitiveLogDao = AppDatabase.getInstance(applicationContext).cognitiveLogDao()
        
        // 4. 设置复刻音色ID（如果有）
        val clonedVoiceId = userPreferences.userConfig.value.clonedVoiceId
        if (clonedVoiceId.isNotBlank()) {
            ttsService.setClonedVoiceId(clonedVoiceId)
            Log.d(TAG, "Using cloned voice: $clonedVoiceId")
        }
        
        // 5. 初始化传感器
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (stepCounterSensor == null) {
            Log.w(TAG, "Step Counter Sensor not found! Using accelerometer as backup.")
        } else {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // 加速度传感器作为备用移动检测
        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Accelerometer registered for backup movement detection")
        }

        startMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "主动关怀服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SilverLink 后台守护服务，用于检测久坐并主动关怀"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "久坐无响应警报",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "久坐无响应时触发的紧急警报"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("安全守护已开启")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun startMonitoring() {
        checkJob = serviceScope.launch {
            while (isActive) {
                checkInactivity()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun checkInactivity() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastMove = currentTime - lastMovementTime.get()
        val quietHours = isQuietHoursInBeijing()

        Log.d(TAG, "Checking inactivity: ${timeSinceLastMove / 1000}s elapsed. QuietHours=$quietHours, Threshold: ${INACTIVITY_THRESHOLD_MS / 1000}s")

        if (timeSinceLastMove > INACTIVITY_THRESHOLD_MS) {
            if (quietHours) {
                Log.d(TAG, "Skipping trigger: quiet hours (22:00-08:00)")
                return
            }

            triggerProactiveInteraction()
            // Reset timer to avoid spamming
            lastMovementTime.set(System.currentTimeMillis())
        }
    }

    private fun isQuietHoursInBeijing(): Boolean {
        val tz = TimeZone.getTimeZone("Asia/Shanghai")
        val calendar = Calendar.getInstance(tz)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 8
    }

    private fun triggerProactiveInteraction() {
        Log.i(TAG, "Proactive Interaction Triggered!")
        
        // 检查上次唤醒后是否有响应（通过检查App是否在前台）
        checkPreviousWakeUpResponse()
        
        // 记录本次唤醒时间
        lastWakeUpTime = System.currentTimeMillis()
        
        // 1. Wake Screen
        wakeScreen()
        
        // 2. Generate and speak personalized greeting
        speakGreeting()

        // 2.1 适度提示记忆小游戏（每天最多一次）
        maybePromptMemoryQuiz()
        
        // 3. 增加失败计数（如果用户打开App，会在onResume时重置）
        val failures = consecutiveWakeUpFailures.incrementAndGet()
        Log.d(TAG, "Consecutive wake-up failures: $failures")
        
        // 4. 连续失败2次，发送警报给家人
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            triggerInactivityAlert()
            // 重置计数器，避免重复发送
            consecutiveWakeUpFailures.set(0)
        }
    }

    /**
     * 适度提示记忆小游戏
     */
    private fun maybePromptMemoryQuiz() {
        serviceScope.launch {
            try {
                val now = System.currentTimeMillis()
                val lastPrompt = lastQuizPromptTime.get()
                if (now - lastPrompt < QUIZ_PROMPT_INTERVAL_MS) return@launch

                if (deviceId.isBlank()) return@launch
                val photoCount = memoryPhotoDao.getDownloadedCount(deviceId)
                if (photoCount <= 0) return@launch

                val recentLogs = cognitiveLogDao.getRecentLogs(deviceId, 1)
                if (recentLogs.isNotEmpty()) {
                    val recentTime = recentLogs.first().createdAt
                    if (now - recentTime < 3 * 24 * 60 * 60 * 1000L) return@launch
                }

                delay(4000)
                val prompt = "要不要一起玩记忆小游戏？我给您出几道照片小题。"
                // 使用方言设置
                val config = userPreferences.userConfig.value
                val dialectName = if (config.dialect != Dialect.NONE) config.dialect.displayName else ""
                val result = ttsService.synthesize(prompt, 1.0, dialectName, Emotion.HAPPY)
                result.fold(
                    onSuccess = { audioData ->
                        audioPlayer.play(audioData)
                        lastQuizPromptTime.set(now)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "TTS quiz prompt failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prompt memory quiz", e)
            }
        }
    }
    
    /**
     * 检查上次唤醒后是否有响应
     * 如果用户在上次唤醒后有移动（计步器变化），则重置失败计数
     */
    private fun checkPreviousWakeUpResponse() {
        if (lastWakeUpTime == 0L) return
        
        // 只有真正的用户移动（计步器变化）才算响应，不是定时器重置
        val realMovement = lastRealMovementTime.get()
        Log.d(TAG, "Checking response: realMovement=$realMovement, lastWakeUp=$lastWakeUpTime")
        
        if (realMovement > lastWakeUpTime) {
            Log.d(TAG, "User responded to previous wake-up (real movement detected), resetting failure count and timer")
            consecutiveWakeUpFailures.set(0)
            // 重置计时器从最后一次真实移动的时间开始
            lastMovementTime.set(realMovement)
        } else {
            Log.d(TAG, "No real movement since last wake-up, keeping failure count")
        }
    }
    
    /**
     * 触发久坐无响应紧急提醒（全屏提示 + 电话 + 短信）
     */
    private fun triggerInactivityAlert() {
        Log.w(TAG, "Inactivity alert triggered, showing emergency screen")
        wakeScreen()

        val intent = Intent(this, FallAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra("alert_type", "inactivity")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("久坐无响应")
            .setContentText("正在启动紧急呼救...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed", e)
        }
    }

    private fun wakeScreen() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SilverLink:ProactiveWakeLock"
            )
            wakeLock.acquire(3000) // Release after 3 seconds
        }
    }

    /**
     * 使用 AI 生成个性化唤醒词并播放
     */
    private fun speakGreeting() {
        serviceScope.launch {
            try {
                // 1. 获取用户配置
                val config = userPreferences.userConfig.value
                val elderName = config.elderName.ifBlank { "您" }
                val elderProfile = config.elderProfile
                
                // 2. 获取最近一条情绪记录
                val latestMoodLog = historyDao.getLatestMoodLog()
                val latestMood = latestMoodLog?.mood ?: "NEUTRAL"
                val latestMoodNote = latestMoodLog?.note ?: ""
                
                Log.d(TAG, "Generating greeting for: name=$elderName, profile=$elderProfile, mood=$latestMood")
                
                // 3. 调用 AI 生成个性化唤醒词
                val greetingText = generateGreetingWithAI(elderName, elderProfile, latestMood, latestMoodNote)
                Log.d(TAG, "AI generated greeting: $greetingText")
                
                // 4. 使用 App 统一的语音服务播放
                // 获取方言名称（如 "四川话"、"广东话"）
                val dialectName = if (config.dialect != Dialect.NONE) {
                    config.dialect.displayName
                } else {
                    ""
                }
                // 根据最近情绪设置语音情感
                val emotion = Emotion.fromLabel(latestMood)
                val result = ttsService.synthesize(greetingText, 1.0, dialectName, emotion)
                result.fold(
                    onSuccess = { audioData ->
                        Log.d(TAG, "TTS synthesis successful, playing audio...")
                        audioPlayer.play(audioData)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "TTS synthesis failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in speakGreeting", e)
            }
        }
    }

    /**
     * 使用 Qwen AI 生成个性化唤醒词
     */
    private suspend fun generateGreetingWithAI(
        elderName: String,
        elderProfile: String,
        latestMood: String,
        latestMoodNote: String
    ): String {
        return try {
            val assistantName = userPreferences.userConfig.value.assistantName.ifBlank { "小银" }
            val systemPrompt = """
                你是SilverLink陪伴应用的语音助手"$assistantName"。现在需要为久坐的长辈生成一句温暖的唤醒问候语。
                要求：
                1. 简短（不超过30字）、温暖、自然
                2. 建议起身活动，可以结合长辈的兴趣爱好
                3. 如果有情绪记录，可以适当关心
                4. 只输出问候语本身，不要有引号或其他格式
            """.trimIndent()
            
            val userPrompt = buildString {
                append("长辈称呼：$elderName\n")
                if (elderProfile.isNotBlank()) {
                    append("长辈信息：$elderProfile\n")
                }
                if (latestMoodNote.isNotBlank()) {
                    append("最近情绪：$latestMood，相关内容：$latestMoodNote\n")
                }
                append("请生成唤醒问候语。")
            }
            
            Log.d(TAG, "AI prompt: $userPrompt")
            
            val request = QwenRequest(
                input = Input(
                    messages = listOf(
                        Message("system", systemPrompt),
                        Message("user", userPrompt)
                    )
                )
            )
            
            val response = RetrofitClient.api.chat(request)
            val generatedText = response.output.choices?.firstOrNull()?.message?.content
                ?: response.output.text
                ?: getFallbackGreeting(elderName)
            
            // 清理可能的引号
            generatedText.trim().removeSurrounding("\"").removeSurrounding(""", """)
        } catch (e: Exception) {
            Log.e(TAG, "AI greeting generation failed", e)
            getFallbackGreeting(elderName)
        }
    }

    /**
     * 兜底问候语（网络失败时使用）
     */
    private fun getFallbackGreeting(elderName: String): String {
        return "${elderName}，您坐很久了，要不要起来活动一下？"
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // 计算加速度的总变化量（去除重力后的运动检测）
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
                
                // 检测加速度显著变化（手机被拿起/移动）
                val delta = kotlin.math.abs(magnitude - lastAccelMagnitude)
                if (lastAccelMagnitude > 0 && delta > ACCEL_MOVEMENT_THRESHOLD) {
                    val now = System.currentTimeMillis()
                    lastMovementTime.set(now)
                    lastRealMovementTime.set(now)
                    Log.d(TAG, "Movement detected via accelerometer (delta: $delta)")
                }
                lastAccelMagnitude = magnitude
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values[0]
                if (lastStepCount == -1f) {
                    lastStepCount = steps
                } else {
                    if (steps != lastStepCount) {
                        // Steps increased -> Movement detected
                        val now = System.currentTimeMillis()
                        lastMovementTime.set(now)
                        lastRealMovementTime.set(now) // Track real user movement separately
                        lastStepCount = steps
                        Log.d(TAG, "Movement detected (steps: $steps)")
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure service stays alive
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        audioPlayer.release()
        checkJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service Destroyed")
    }

    // Restart service if task is removed (e.g., app swiped away)
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed, restarting service")
        val restartIntent = Intent(applicationContext, ProactiveInteractionService::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        androidx.core.content.ContextCompat.startForegroundService(applicationContext, restartIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
