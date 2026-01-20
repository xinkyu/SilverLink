package com.silverlink.app.feature.falldetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.silverlink.app.data.local.UserPreferences

/**
 * 开机启动接收器
 * 设备启动后自动启动跌倒检测服务（如果用户已开启）
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking fall detection setting")
            
            val userPreferences = UserPreferences.getInstance(context)
            
            if (userPreferences.isFallDetectionEnabled()) {
                Log.i(TAG, "Fall detection is enabled, starting service")
                FallDetectionService.start(context)
            } else {
                Log.d(TAG, "Fall detection is disabled, not starting service")
            }
        }
    }
}
