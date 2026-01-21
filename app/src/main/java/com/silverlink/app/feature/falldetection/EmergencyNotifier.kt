package com.silverlink.app.feature.falldetection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.entity.EmergencyContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 紧急通知器
 * 负责在跌倒检测后发送SMS和拨打电话给紧急联系人
 */
class EmergencyNotifier(private val context: Context) {
    
    companion object {
        private const val TAG = "EmergencyNotifier"
    }

    enum class AlertType {
        FALL,
        INACTIVITY
    }
    
    private val locationHelper = LocationHelper(context)
    private val emergencyContactDao = AppDatabase.getInstance(context).emergencyContactDao()
    private val userPreferences = UserPreferences.getInstance(context)
    
    /**
     * 发送紧急通知到所有紧急联系人
     * 优先拨打电话（无需等待），然后后台发送SMS
     * @return 发送结果：成功发送的联系人数量
     */
    suspend fun sendEmergencyNotification(alertType: AlertType = AlertType.FALL): Int {
        var successCount = 0
        
        try {
            // 1. 获取所有紧急联系人
            val contacts = withContext(Dispatchers.IO) {
                emergencyContactDao.getAllContactsSync()
            }
            
            if (contacts.isEmpty()) {
                Log.w(TAG, "No emergency contacts configured")
                return 0
            }
            
            // 2. ★ 立即拨打首要联系人电话（不等待其他操作）
            val primaryContact = contacts.find { it.isPrimary } ?: contacts.firstOrNull()
            primaryContact?.let {
                makeEmergencyCall(it)
            }
            
            // 3. 后台获取位置并发送SMS（不阻塞电话）
            withContext(Dispatchers.IO) {
                try {
                    val location = locationHelper.getCurrentLocation()
                    val locationText = locationHelper.generateLocationText(location)
                    val elderName = userPreferences.userConfig.value.elderName.ifBlank { "您的家人" }
                    val message = buildEmergencyMessage(elderName, locationText, alertType)
                    
                    // 发送SMS给所有联系人
                    for (contact in contacts) {
                        if (sendSms(contact, message)) {
                            successCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send SMS notifications", e)
                }
            }
            
            Log.i(TAG, "Emergency notification sent to $successCount contacts")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send emergency notification", e)
        }
        
        return successCount
    }
    
    /**
     * 构建紧急消息内容
     */
    private fun buildEmergencyMessage(
        elderName: String,
        locationText: String,
        alertType: AlertType
    ): String {
        val title = when (alertType) {
            AlertType.FALL -> "$elderName 可能发生跌倒！"
            AlertType.INACTIVITY -> "$elderName 长时间未移动且多次无响应！"
        }

        val detail = when (alertType) {
            AlertType.FALL -> "系统检测到疑似跌倒事件，请尽快确认情况。"
            AlertType.INACTIVITY -> "系统检测到久坐无响应情况，请尽快确认情况。"
        }

        return """
【SilverLink紧急通知】
$title

$detail

$locationText

此消息由SilverLink安全守护系统自动发送。
        """.trimIndent()
    }
    
    /**
     * 发送SMS给联系人
     */
    private fun sendSms(contact: EmergencyContactEntity, message: String): Boolean {
        if (!hasSmsPermission()) {
            Log.w(TAG, "No SMS permission")
            return false
        }
        
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
            
            // 长消息需要分割发送
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                contact.phone,
                null,
                parts,
                null,
                null
            )
            
            Log.d(TAG, "SMS sent to ${contact.name} (${contact.phone})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to ${contact.name}", e)
            false
        }
    }
    
    /**
     * 拨打紧急电话 - 直接拨出，不显示选择器
     * 对于双卡手机，自动使用卡槽1
     */
    private fun makeEmergencyCall(contact: EmergencyContactEntity) {
        Log.d(TAG, "Attempting emergency call to ${contact.name} (${contact.phone})")
        
        try {
            // 直接使用 ACTION_CALL 拨打电话
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contact.phone}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                // 对于双卡手机，指定使用默认SIM卡（通常是卡槽1）
                // 使用 android.telecom.extra 指定 SIM 卡
                putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE_ID", "0")
                putExtra("com.android.phone.extra.slot", 0)  // SIM 卡槽 0 = 卡1
            }
            context.startActivity(callIntent)
            Log.i(TAG, "Emergency call initiated to ${contact.name} (${contact.phone})")
        } catch (e: SecurityException) {
            Log.e(TAG, "CALL_PHONE permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make emergency call", e)
        }
    }
    
    /**
     * 检查SMS权限
     */
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查电话权限
     */
    fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取紧急联系人数量
     */
    suspend fun getContactCount(): Int {
        return withContext(Dispatchers.IO) {
            emergencyContactDao.getContactCount()
        }
    }
}
