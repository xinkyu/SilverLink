package com.silverlink.app.feature.falldetection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.entity.EmergencyContact
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.EmergencyReportRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 紧急通知器
 * 负责在检测到跌倒且用户未取消时发送紧急通知
 */
class EmergencyNotifier(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyNotifier"
    }

    /**
     * 发送紧急通知给所有紧急联系人
     * @return 是否成功发送至少一条通知
     */
    suspend fun sendEmergencyNotifications(): Boolean = withContext(Dispatchers.IO) {
        try {
            val contacts = SilverLinkApp.database.emergencyContactDao().getAllContactsList()
            
            if (contacts.isEmpty()) {
                Log.w(TAG, "No emergency contacts configured")
                return@withContext false
            }

            var successCount = 0
            
            // 上报紧急事件到云端（让家人端App收到通知）
            reportEmergencyToCloud()

            // 获取主要联系人，如果没有设置主要联系人则使用第一个联系人
            val primaryContact = contacts.find { it.isPrimary } ?: contacts.first()
            
            // 给主要联系人发送短信并拨打电话
            if (sendSmsToContact(primaryContact)) successCount++
            // 直接拨打紧急电话
            makeEmergencyCall(primaryContact.phone)

            // 发送短信给其他联系人
            contacts.filter { it.id != primaryContact.id }.forEach { contact ->
                if (sendSmsToContact(contact)) successCount++
            }

            Log.d(TAG, "Emergency notifications sent to $successCount contacts")
            return@withContext successCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send emergency notifications", e)
            return@withContext false
        }
    }
    
    /**
     * 上报紧急事件到云端
     */
    private suspend fun reportEmergencyToCloud() {
        try {
            val location = getLastKnownLocation()
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            
            val request = EmergencyReportRequest(
                elderDeviceId = deviceId,
                eventType = "fall",
                latitude = location?.latitude,
                longitude = location?.longitude,
                timestamp = System.currentTimeMillis()
            )
            
            val response = CloudBaseService.api.reportEmergency(request)
            if (response.success) {
                Log.d(TAG, "Emergency event reported to cloud successfully")
            } else {
                Log.w(TAG, "Failed to report emergency to cloud: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report emergency to cloud", e)
            // 云端上报失败不影响短信和电话通知
        }
    }

    /**
     * 发送紧急短信给指定联系人
     */
    private fun sendSmsToContact(contact: EmergencyContact): Boolean {
        return try {
            if (!hasSmsPermission()) {
                Log.w(TAG, "SMS permission not granted")
                return false
            }

            val message = buildEmergencyMessage(contact.name)
            
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // 分段发送长短信
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                contact.phone,
                null,
                parts,
                null,
                null
            )

            Log.d(TAG, "SMS sent to ${contact.name}: ${contact.phone}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to ${contact.name}", e)
            false
        }
    }

    /**
     * 拨打紧急电话 - 使用 TelecomManager 直接拨打，避免弹出SIM卡选择界面
     */
    fun makeEmergencyCall(phoneNumber: String): Boolean {
        return try {
            if (!hasCallPermission()) {
                Log.w(TAG, "Call permission not granted")
                return false
            }

            val uri = Uri.parse("tel:$phoneNumber")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 使用 TelecomManager 直接拨打电话
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                
                // 获取默认的电话账户
                val extras = Bundle()
                
                // 尝试获取默认的电话账户（需要 READ_PHONE_STATE 权限）
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                    == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val defaultPhoneAccount = telecomManager.getDefaultOutgoingPhoneAccount("tel")
                        if (defaultPhoneAccount != null) {
                            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, defaultPhoneAccount)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get default phone account", e)
                    }
                }
                
                telecomManager.placeCall(uri, extras)
                Log.d(TAG, "Emergency call initiated via TelecomManager to $phoneNumber")
            } else {
                // 对于旧版本 Android，使用传统方式
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = uri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Emergency call initiated via Intent to $phoneNumber")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make emergency call", e)
            false
        }
    }

    /**
     * 构建紧急短信内容（包含GPS位置）
     */
    private fun buildEmergencyMessage(recipientName: String): String {
        val location = getLastKnownLocation()
        val locationText = if (location != null) {
            val lat = location.latitude
            val lng = location.longitude
            // 使用高德地图链接（中国可用）
            "\n位置: https://uri.amap.com/marker?position=$lng,$lat&name=紧急位置"
        } else {
            "\n（无法获取位置信息）"
        }
        
        return """
    【银龄守护紧急通知】
            
    亲爱的$recipientName，
            
    您的家人可能发生了跌倒事故！
            
    系统在刚才检测到老人可能跌倒，且在15秒内未收到"我没事"的确认。
            
    请立即联系确认老人安全状况。$locationText
            
    - 银龄守护 SilverLink
        """.trimIndent()
    }
    
    /**
     * 获取最后已知的位置
     */
    private fun getLastKnownLocation(): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted")
            return null
        }
        
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 尝试从GPS获取位置
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            
            // 如果GPS没有位置，尝试网络定位
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            
            // 如果还是没有，尝试被动定位
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }
            
            if (location != null) {
                Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")
            } else {
                Log.w(TAG, "No location available")
            }
            
            location
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location", e)
            null
        }
    }

    /**
     * 检查短信权限
     */
    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查电话权限
     */
    private fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
