package com.silverlink.app.feature.reminder

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.feature.chat.AudioPlayerHelper
import com.silverlink.app.feature.chat.TextToSpeechService
import com.silverlink.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ReminderAlertActivity"

class ReminderAlertActivity : ComponentActivity() {
    
    private var ringtone: Ringtone? = null
    private val ttsService = TextToSpeechService()
    private lateinit var audioPlayer: AudioPlayerHelper
    private val recognitionService = MedicationRecognitionService()
    private val verificationHelper = MedicationVerificationHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        audioPlayer = AudioPlayerHelper(this)
        
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
                    },
                    onFindPill = { bitmap ->
                        stopRingtone()
                        verifyPillAndSpeak(bitmap)
                    }
                )
            }
        }
    }

    /**
     * 验证药品并语音播报结果
     */
    private fun verifyPillAndSpeak(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. 使用 AI 识别药品
                val recognitionResult = withContext(Dispatchers.IO) {
                    recognitionService.recognizeMedication(bitmap)
                }
                
                recognitionResult.fold(
                    onSuccess = { recognized ->
                        Log.d(TAG, "Recognized medication: ${recognized.name}")
                        
                        // 2. 获取当前计划的所有药品
                        val medications = withContext(Dispatchers.IO) {
                            SilverLinkApp.database.medicationDao().getAllMedications().first()
                        }
                        
                        // 3. 获取当前时间
                        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date())
                        
                        // 4. 验证药品
                        val verificationResult = verificationHelper.verifyMedication(
                            recognizedName = recognized.name,
                            scheduledMeds = medications,
                            currentTime = currentTime
                        )
                        
                        // 5. 生成并播报语音回复
                        val response = generateVoiceResponse(verificationResult)
                        speakText(response)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Recognition failed", error)
                        val response = generateUnknownMedResponse()
                        speakText(response)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in verifyPillAndSpeak", e)
                speakText("抱歉，出了点问题，请再试一次。")
            }
        }
    }
    
    /**
     * 根据验证结果生成语音回复
     */
    private fun generateVoiceResponse(result: VerificationResult): String {
        val elderName = getElderName()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        
        return when (result) {
            is VerificationResult.Correct -> {
                "${prefix}是的，这就是${result.medicationName}，请服用${result.dosage}。"
            }
            is VerificationResult.WrongMed -> {
                "${prefix}不对哦，这个是${result.recognizedName}。你现在需要吃的是${result.correctName}，${result.correctDosage}。"
            }
            is VerificationResult.NoScheduleNow -> {
                if (result.nextTime != null) {
                    "${prefix}现在不是吃药时间哦，下次吃药时间是${result.nextTime}。"
                } else {
                    "${prefix}现在没有需要吃的药哦。"
                }
            }
            is VerificationResult.UnknownMed -> {
                generateUnknownMedResponse()
            }
        }
    }
    
    private fun generateUnknownMedResponse(): String {
        val elderName = getElderName()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        return "${prefix}抱歉，我没能认出这个药。你可以把药瓶正面对着我再试一次吗？"
    }
    
    private fun getElderName(): String {
        return UserPreferences.getInstance(this).userConfig.value.elderName.trim()
    }
    
    /**
     * 语音播报文本
     */
    private fun speakText(text: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = ttsService.synthesize(text)
                result.fold(
                    onSuccess = { audioData ->
                        audioPlayer.play(audioData)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "TTS failed", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking text", e)
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
        audioPlayer.release()
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
    onSnooze: () -> Unit,
    onFindPill: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    
    // 验证状态
    var isVerifying by remember { mutableStateOf(false) }
    var verificationMessage by remember { mutableStateOf<String?>(null) }
    
    // 相机拍照相关
    var photoFile by remember { mutableStateOf<File?>(null) }
    
    // 相机权限检查
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    
    // 拍照启动器
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            isVerifying = true
            verificationMessage = "正在识别药品..."
            val bitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath)
            if (bitmap != null) {
                onFindPill(bitmap)
                // 重置状态（实际语音播报由 Activity 处理）
                isVerifying = false
                verificationMessage = null
            } else {
                isVerifying = false
                verificationMessage = "图片加载失败"
            }
        }
    }
    
    // 启动相机拍照
    fun launchCamera() {
        val file = File(context.cacheDir, "pill_check_${System.currentTimeMillis()}.jpg")
        photoFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        cameraLauncher.launch(uri)
    }
    
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
            
            // 验证状态提示
            if (verificationMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = verificationMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 我吃过了
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

                // 稍后提醒
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
                
                // 帮我找药 - 新增按钮
                Button(
                    onClick = {
                        if (hasCameraPermission) {
                            launchCamera()
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GradientStart.copy(alpha = 0.9f),
                        contentColor = CardSurface
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "帮我找药",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
