package com.silverlink.app

import android.app.Application
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.feature.memory.MemorySyncService
import com.silverlink.app.feature.reminder.DailyResetWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SilverLinkApp : Application() {
    
    companion object {
        lateinit var database: AppDatabase
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Room Database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "silverlink-db"
        ).build()

        // 安排每日凌晨重置任务
        scheduleDailyReset()

        // 安排记忆照片后台同步
        MemorySyncService.schedule(this)
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
