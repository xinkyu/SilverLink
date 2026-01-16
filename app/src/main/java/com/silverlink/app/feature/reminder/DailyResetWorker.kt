package com.silverlink.app.feature.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.silverlink.app.SilverLinkApp

/**
 * 每日凌晨重置所有药品的服用状态
 */
class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Resetting daily medication intake status")
            val dao = SilverLinkApp.database.medicationDao()
            dao.resetDailyIntake()
            Log.d(TAG, "Daily reset completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset daily intake", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "DailyResetWorker"
        const val WORK_NAME = "daily_medication_reset"
    }
}
