package com.silverlink.app.feature.falldetection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.silverlink.app.data.local.UserPreferences

/**
 * 跌倒检测管理器
 * 提供统一的接口来启动/停止跌倒检测服务
 */
class FallDetectionManager private constructor(private val context: Context) {

    private val userPrefs = UserPreferences.getInstance(context)

    companion object {
        private const val TAG = "FallDetectionManager"
        
        @Volatile
        private var instance: FallDetectionManager? = null

        fun getInstance(context: Context): FallDetectionManager {
            return instance ?: synchronized(this) {
                instance ?: FallDetectionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 检查跌倒检测是否正在运行
     */
    fun isDetectionRunning(): Boolean {
        return FallDetectionService.isRunning
    }

    /**
     * 启动跌倒检测服务
     * @return 是否成功启动
     */
    fun startDetection(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Required permissions not granted")
            return false
        }

        try {
            val intent = Intent(context, FallDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            // 保存启用状态
            userPrefs.setFallDetectionEnabled(true)
            
            Log.d(TAG, "Fall detection service started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start fall detection service", e)
            return false
        }
    }

    /**
     * 停止跌倒检测服务
     */
    fun stopDetection() {
        try {
            val intent = Intent(context, FallDetectionService::class.java)
            context.stopService(intent)
            
            // 保存禁用状态
            userPrefs.setFallDetectionEnabled(false)
            
            Log.d(TAG, "Fall detection service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop fall detection service", e)
        }
    }

    /**
     * 切换跌倒检测状态
     * @return 切换后的状态（true=运行中，false=已停止）
     */
    fun toggleDetection(): Boolean {
        return if (isDetectionRunning()) {
            stopDetection()
            false
        } else {
            startDetection()
        }
    }

    /**
     * 检查是否有必要的权限
     */
    fun hasRequiredPermissions(): Boolean {
        // 基础权限检查
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return hasCallPermission && hasSmsPermission
    }

    /**
     * 获取需要请求的权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,  // 用于获取默认电话账户
            Manifest.permission.ACCESS_FINE_LOCATION  // 用于GPS位置分享
        )

        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }

    /**
     * 获取未授予的权限列表
     */
    fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}
