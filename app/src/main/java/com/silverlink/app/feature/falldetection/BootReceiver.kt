package com.silverlink.app.feature.falldetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.silverlink.app.data.local.UserPreferences

/**
 * 开机启动接收器
 * 在设备启动后自动恢复跌倒检测服务
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking fall detection settings")
            
            // 检查用户是否启用了跌倒检测
            val userPrefs = UserPreferences.getInstance(context)
            if (userPrefs.isFallDetectionEnabled()) {
                Log.d(TAG, "Fall detection was enabled, starting service")
                FallDetectionManager.getInstance(context).startDetection()
            }
        }
    }
}
