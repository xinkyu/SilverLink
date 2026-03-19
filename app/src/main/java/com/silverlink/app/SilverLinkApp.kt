package com.silverlink.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.silverlink.app.BuildConfig
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.Dialect
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.feature.health.HealthDebugLogger
import com.silverlink.app.feature.health.OppoHealthSdkManager
import com.silverlink.app.feature.emotion.EmotionRecognitionService
import com.silverlink.app.feature.memory.MemorySyncService
import com.silverlink.app.feature.reminder.DailyResetWorker
import com.silverlink.sdk.health.HealthServiceBridgeFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SilverLinkApp : Application() {
    
    companion object {
        lateinit var database: AppDatabase
    }

    override fun onCreate() {
        super.onCreate()

        HealthDebugLogger.logCurrentSigningSha1(this)
        HealthServiceBridgeFactory.setForceMock(BuildConfig.FORCE_MOCK_HEALTH_DATA)
        Log.i("SilverLinkApp", "Health mock mode=${BuildConfig.FORCE_MOCK_HEALTH_DATA}")

        // Initialize Room Database using Singleton
        database = AppDatabase.getInstance(this)


        // 安排每日凌晨重置任务
        scheduleDailyReset()

        // 安排记忆照片后台同步
        MemorySyncService.schedule(this)

        val userPreferences = UserPreferences.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
            if (userPreferences.isOppoHealthSdkConsentGranted()) {
                val result = OppoHealthSdkManager.initializeIfConsented(this@SilverLinkApp, userPreferences)
                if (result.isSuccess) {
                    Log.d("SilverLinkApp", "OPPO health SDK initialized")
                } else {
                    Log.w("SilverLinkApp", "OPPO health SDK init failed", result.exceptionOrNull())
                }
            }
        }

        // 后台初始化 ONNX 情绪识别模型
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EmotionRecognitionService.getInstance(this@SilverLinkApp).initialize()
                Log.d("SilverLinkApp", "Emotion recognition models loaded")
            } catch (e: Throwable) {
                Log.e("SilverLinkApp", "Failed to load emotion models", e)
            }
        }
    }

    /**
     * 安排每日凌晨 0:00 执行的重置任务
     * 使用 WorkManager 确保任务在后台可靠执行
     */
    private fun scheduleDailyReset() {
        // 计算到下一个凌晨的延迟时间
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1) // 下一个凌晨
        }
        val initialDelay = midnight.timeInMillis - now.timeInMillis

        val dailyResetRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(
            24, TimeUnit.HOURS // 每24小时执行一次
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false) // 即使电量低也执行
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyResetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // 如果已存在则保留
            dailyResetRequest
        )
    }
}
