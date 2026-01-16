package com.silverlink.app.feature.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.silverlink.app.data.local.entity.Medication
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 为药品的所有时间点设置闹钟
     */
    fun scheduleAll(medication: Medication) {
        val times = medication.getTimeList()
        times.forEachIndexed { index, time ->
            scheduleForTime(medication, time, index)
        }
    }

    /**
     * 取消药品的所有闹钟
     */
    fun cancelAll(medication: Medication) {
        val times = medication.getTimeList()
        times.forEachIndexed { index, _ ->
            cancelForTime(medication.id, index)
        }
    }

    /**
     * 为单个时间点设置闹钟
     * @param timeIndex 用于生成唯一的 PendingIntent ID
     */
    private fun scheduleForTime(medication: Medication, time: String, timeIndex: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MED_ID", medication.id)
            putExtra("MED_NAME", medication.name)
            putExtra("MED_DOSAGE", medication.dosage)
            putExtra("MED_TIME", time)
        }

        // Parse time HH:mm
        val parts = time.split(":")
        if (parts.size != 2) return
        
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        // If time has passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 使用 medicationId * 100 + timeIndex 作为唯一ID，支持每个药品最多100个时间点
        val requestCode = medication.id * 100 + timeIndex

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    Log.e("AlarmScheduler", "Cannot schedule exact alarms")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun cancelForTime(medicationId: Int, timeIndex: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = medicationId * 100 + timeIndex
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    // 保留旧方法的兼容性，内部调用新方法
    fun schedule(medication: Medication) {
        scheduleAll(medication)
    }

    fun cancel(medication: Medication) {
        cancelAll(medication)
    }
}

