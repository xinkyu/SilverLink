package com.silverlink.app.data.repository

import android.content.Context
import android.provider.Settings
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole
import com.silverlink.app.data.local.PairingInfo
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.MedicationLogData
import com.silverlink.app.data.remote.MoodLogData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 同步仓库
 * 处理本地数据与云端（腾讯云 CloudBase）的同步
 * 
 * 架构：Android HTTP 请求 -> 云函数 -> 云数据库
 */
class SyncRepository(private val context: Context) {
    
    private val cloudBase = CloudBaseService
    private val userPrefs = UserPreferences.getInstance(context)
    private val currentDeviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    // ==================== 配对相关 ====================
    
    /**
     * 创建配对码并同步到云端（家人端调用）
     */
    suspend fun createPairingCodeOnCloud(
        elderName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // 生成本地配对码
        val code = userPrefs.completeFamilySetup(elderName)
        val cleanCode = code.replace(" ", "")
        
        // 同步到云端
        val cloudResult = cloudBase.createPairingCode(
            code = cleanCode,
            elderName = elderName,
            familyDeviceId = currentDeviceId
        )
        
        if (cloudResult.isSuccess) {
            Result.success(code)
        } else {
            // 云端失败时仍返回本地配对码，支持本地配对
            Result.success(code)
        }
    }
    
    /**
     * 验证配对码（长辈端调用）
     * 优先从云端验证，云端失败则回退到本地验证
     */
    suspend fun verifyPairingCode(
        inputCode: String
    ): Result<PairingInfo?> = withContext(Dispatchers.IO) {
        val cleanCode = inputCode.replace(" ", "").replace("-", "")
        
        // 尝试云端验证
        try {
            val cloudResult = cloudBase.verifyPairingCode(cleanCode, currentDeviceId)
            
            if (cloudResult.isSuccess) {
                val pairingResult = cloudResult.getOrNull()
                if (pairingResult != null) {
                    // 云端验证成功
                    return@withContext Result.success(PairingInfo(
                        code = cleanCode,
                        elderName = pairingResult.elderName,
                        timestamp = System.currentTimeMillis()
                    ))
                }
            }
        } catch (e: Exception) {
            // 云端验证失败，继续尝试本地验证
        }
        
        // 云端验证失败，尝试本地验证（用于同一设备测试）
        val localPairing = userPrefs.verifyAndGetPairingInfo(inputCode)
        Result.success(localPairing)
    }
    
    /**
     * 完成长辈端激活
     */
    suspend fun completeElderActivation(
        elderName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // 保存本地
        userPrefs.completeElderActivation(elderName)
        Result.success(Unit)
    }
    
    // ==================== 服药记录同步 ====================
    
    /**
     * 同步服药记录到云端
     */
    suspend fun syncMedicationTaken(
        medicationId: Int,
        medicationName: String,
        dosage: String,
        scheduledTime: String,
        status: String = "taken"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cloudBase.addMedicationLog(
                elderDeviceId = currentDeviceId,
                medicationId = medicationId,
                medicationName = medicationName,
                dosage = dosage,
                scheduledTime = scheduledTime,
                status = status
            )
            Result.success(Unit)
        } catch (e: Exception) {
            // 云端同步失败不影响本地功能
            Result.success(Unit)
        }
    }
    
    /**
     * 获取长辈的服药记录（家人端调用）
     */
    suspend fun getElderMedicationLogs(
        elderDeviceId: String? = null,
        date: String? = null
    ): Result<List<MedicationLogData>> = withContext(Dispatchers.IO) {
        // 如果未指定长辈设备ID，获取已配对的长辈设备ID
        val targetDeviceId = elderDeviceId ?: run {
            val pairedResult = cloudBase.getPairedElderDeviceId(currentDeviceId)
            pairedResult.getOrNull()
        }
        
        if (targetDeviceId == null) {
            return@withContext Result.failure(Exception("未找到已配对的长辈"))
        }
        
        cloudBase.getMedicationLogs(targetDeviceId, date)
    }
    
    // ==================== 情绪记录同步 ====================
    
    /**
     * 同步情绪记录到云端
     */
    suspend fun syncMoodLog(
        mood: String,
        note: String,
        conversationSummary: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cloudBase.addMoodLog(
                elderDeviceId = currentDeviceId,
                mood = mood,
                note = note,
                conversationSummary = conversationSummary
            )
            Result.success(Unit)
        } catch (e: Exception) {
            // 云端同步失败不影响本地功能
            Result.success(Unit)
        }
    }
    
    /**
     * 获取长辈的情绪记录（家人端调用）
     */
    suspend fun getElderMoodLogs(
        elderDeviceId: String? = null,
        days: Int = 7
    ): Result<List<MoodLogData>> = withContext(Dispatchers.IO) {
        // 如果未指定长辈设备ID，获取已配对的长辈设备ID
        val targetDeviceId = elderDeviceId ?: run {
            val pairedResult = cloudBase.getPairedElderDeviceId(currentDeviceId)
            pairedResult.getOrNull()
        }
        
        if (targetDeviceId == null) {
            return@withContext Result.failure(Exception("未找到已配对的长辈"))
        }
        
        cloudBase.getMoodLogs(targetDeviceId, days)
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取当前设备ID
     */
    fun getDeviceId(): String = currentDeviceId
    
    /**
     * 检查是否为长辈端
     */
    fun isElderDevice(): Boolean {
        return userPrefs.userConfig.value.role == UserRole.ELDER
    }
    
    /**
     * 检查是否为家人端
     */
    fun isFamilyDevice(): Boolean {
        return userPrefs.userConfig.value.role == UserRole.FAMILY
    }
    
    companion object {
        @Volatile
        private var instance: SyncRepository? = null
        
        fun getInstance(context: Context): SyncRepository {
            return instance ?: synchronized(this) {
                instance ?: SyncRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
