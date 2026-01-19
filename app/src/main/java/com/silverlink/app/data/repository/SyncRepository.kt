package com.silverlink.app.data.repository

import android.content.Context
import android.provider.Settings
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole
import com.silverlink.app.data.local.PairingInfo
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.MedicationData
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
        elderName: String,
        elderProfile: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        // 生成本地配对码
        val code = userPrefs.completeFamilySetup(elderName, elderProfile)
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
     * 会传递 familyDeviceId 进行配对验证，确保只能获取已配对长辈的数据
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
        
        // 传递 familyDeviceId 进行配对验证
        cloudBase.getMedicationLogs(targetDeviceId, currentDeviceId, date)
    }

    /**
     * 长辈端获取自己的服药记录（不需要配对验证）
     */
    suspend fun getSelfMedicationLogs(
        date: String? = null
    ): Result<List<MedicationLogData>> = withContext(Dispatchers.IO) {
        cloudBase.getMedicationLogs(currentDeviceId, null, date)
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
     * 会传递 familyDeviceId 进行配对验证，确保只能获取已配对长辈的数据
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
        
        // 传递 familyDeviceId 进行配对验证
        cloudBase.getMoodLogs(targetDeviceId, currentDeviceId, days)
    }

    /**
     * 长辈端获取自己的情绪记录（不需要配对验证）
     */
    suspend fun getSelfMoodLogs(
        days: Int = 7
    ): Result<List<MoodLogData>> = withContext(Dispatchers.IO) {
        cloudBase.getMoodLogs(currentDeviceId, null, days)
    }
    
    // ==================== 药品管理 ====================
    
    /**
     * 为长辈添加药品（家人端调用）
     */
    suspend fun addMedicationForElder(
        name: String,
        dosage: String,
        times: String
    ): Result<MedicationData> = withContext(Dispatchers.IO) {
        // 获取已配对的长辈设备ID
        val elderDeviceId = cloudBase.getPairedElderDeviceId(currentDeviceId).getOrNull()
        
        if (elderDeviceId == null) {
            return@withContext Result.failure(Exception("未找到已配对的长辈"))
        }
        
        cloudBase.addMedication(
            elderDeviceId = elderDeviceId,
            familyDeviceId = currentDeviceId,
            name = name,
            dosage = dosage,
            times = times
        )
    }

    /**
     * 更新长辈药品时间（长辈端调用）
     */
    suspend fun updateMedicationTimesForElder(
        name: String,
        dosage: String,
        times: String
    ): Result<MedicationData> = withContext(Dispatchers.IO) {
        cloudBase.updateMedicationTimes(
            elderDeviceId = currentDeviceId,
            name = name,
            dosage = dosage,
            times = times
        )
    }

    /**
     * 更新长辈药品时间（家人端调用）
     */
    suspend fun updateMedicationTimesForPairedElder(
        name: String,
        dosage: String,
        times: String
    ): Result<MedicationData> = withContext(Dispatchers.IO) {
        val elderDeviceId = cloudBase.getPairedElderDeviceId(currentDeviceId).getOrNull()

        if (elderDeviceId == null) {
            return@withContext Result.failure(Exception("未找到已配对的长辈"))
        }

        cloudBase.updateMedicationTimes(
            elderDeviceId = elderDeviceId,
            name = name,
            dosage = dosage,
            times = times
        )
    }
    
    /**
     * 获取长辈的药品列表（家人端调用）
     */
    suspend fun getElderMedicationsList(): Result<List<MedicationData>> = withContext(Dispatchers.IO) {
        val elderDeviceId = cloudBase.getPairedElderDeviceId(currentDeviceId).getOrNull()
        
        if (elderDeviceId == null) {
            return@withContext Result.failure(Exception("未找到已配对的长辈"))
        }
        
        cloudBase.getMedicationList(elderDeviceId)
    }

    /**
     * 长辈端获取自己的药品列表（不需要配对验证）
     */
    suspend fun getSelfMedicationsList(): Result<List<MedicationData>> = withContext(Dispatchers.IO) {
        cloudBase.getMedicationList(currentDeviceId)
    }
    
    /**
     * 从云端同步药品到本地（长辈端调用）
     */
    suspend fun syncMedicationsFromCloud(
        medicationDao: com.silverlink.app.data.local.dao.MedicationDao
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cloudMedications = cloudBase.getMedicationList(currentDeviceId).getOrNull()
                ?: return@withContext Result.success(0)
            
            var syncedCount = 0
            for (cloudMed in cloudMedications) {
                // 检查本地是否已存在同名同剂量的药品，避免重复插入
                val existingMed = medicationDao.getMedicationByNameAndDosage(cloudMed.name, cloudMed.dosage)
                
                if (existingMed == null) {
                    // 不存在则插入
                    val localMedication = com.silverlink.app.data.local.entity.Medication(
                        name = cloudMed.name,
                        dosage = cloudMed.dosage,
                        times = cloudMed.times,
                        isTakenToday = false
                    )
                    medicationDao.insertMedication(localMedication)
                    syncedCount++
                    android.util.Log.d("SyncRepository", "同步新药品: ${cloudMed.name}")
                } else if (existingMed.times != cloudMed.times) {
                    // 存在但时间不同，合并时间并更新
                    val localTimes = existingMed.getTimeList()
                    val cloudTimes = cloudMed.times.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val mergedTimes = (localTimes + cloudTimes).distinct().sorted()
                    val mergedTimesStr = mergedTimes.joinToString(",")

                    if (existingMed.times != mergedTimesStr) {
                        val updatedMed = existingMed.copy(times = mergedTimesStr)
                        medicationDao.updateMedication(updatedMed)
                        android.util.Log.d("SyncRepository", "合并药品时间: ${cloudMed.name}")
                    }

                    // 如果本地合并后时间与云端不同，长辈端尝试回写云端
                    if (isElderDevice() && cloudMed.times != mergedTimesStr) {
                        cloudBase.updateMedicationTimes(
                            elderDeviceId = currentDeviceId,
                            name = cloudMed.name,
                            dosage = cloudMed.dosage,
                            times = mergedTimesStr
                        )
                    }
                }
            }
            
            Result.success(syncedCount)
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "同步药品失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 从云端同步服药记录到本地（长辈端调用）
     */
    suspend fun syncMedicationLogsFromCloud(
        historyDao: com.silverlink.app.data.local.dao.HistoryDao,
        date: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("SyncRepository", "开始同步服药记录, elderDeviceId=$currentDeviceId, date=$date")
            
            // 长辈端直接用自己的设备ID获取服药记录，不需要配对验证
            val cloudLogs = cloudBase.getMedicationLogs(currentDeviceId, null, date).getOrNull()
                ?: return@withContext Result.success(0)
            
            android.util.Log.d("SyncRepository", "从云端获取到 ${cloudLogs.size} 条服药记录")
            
            var syncedCount = 0
            for (cloudLog in cloudLogs) {
                // 检查本地是否已存在该记录（根据日期、药品名和时间点）
                val existingCount = historyDao.getMedicationLogCount(
                    date = cloudLog.date,
                    medicationId = cloudLog.medicationId,
                    scheduledTime = cloudLog.scheduledTime
                )
                
                if (existingCount == 0) {
                    val localLog = com.silverlink.app.data.local.entity.MedicationLogEntity(
                        medicationId = cloudLog.medicationId,
                        medicationName = cloudLog.medicationName,
                        dosage = cloudLog.dosage,
                        scheduledTime = cloudLog.scheduledTime,
                        status = cloudLog.status,
                        date = cloudLog.date
                    )
                    historyDao.insertMedicationLog(localLog)
                    syncedCount++
                    android.util.Log.d("SyncRepository", "同步服药记录: ${cloudLog.medicationName} @ ${cloudLog.scheduledTime}")
                }
            }
            
            android.util.Log.d("SyncRepository", "服药记录同步完成, 新增 $syncedCount 条")
            Result.success(syncedCount)
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "同步服药记录失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 从云端同步情绪记录到本地（长辈端调用）
     */
    suspend fun syncMoodLogsFromCloud(
        historyDao: com.silverlink.app.data.local.dao.HistoryDao,
        days: Int = 30
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("SyncRepository", "开始同步情绪记录, elderDeviceId=$currentDeviceId")
            
            // 长辈端直接用自己的设备ID获取情绪记录
            val cloudLogs = cloudBase.getMoodLogs(currentDeviceId, null, days).getOrNull()
                ?: return@withContext Result.success(0)
            
            android.util.Log.d("SyncRepository", "从云端获取到 ${cloudLogs.size} 条情绪记录")
            
            var syncedCount = 0
            for (cloudLog in cloudLogs) {
                // 解析 createdAt 为时间戳
                val timestamp = parseCreatedAtToMillis(cloudLog.createdAt)
                
                // 简单去重：检查相同日期和情绪的记录数
                val existingLogs = historyDao.getMoodLogsByDate(cloudLog.date)
                val isDuplicate = existingLogs.any { existing ->
                    existing.mood.equals(cloudLog.mood, ignoreCase = true) &&
                    existing.note == cloudLog.note
                }
                
                if (!isDuplicate) {
                    val localLog = com.silverlink.app.data.local.entity.MoodLogEntity(
                        mood = cloudLog.mood.uppercase(),
                        note = cloudLog.note,
                        date = cloudLog.date,
                        createdAt = timestamp ?: System.currentTimeMillis()
                    )
                    historyDao.insertMoodLog(localLog)
                    syncedCount++
                    android.util.Log.d("SyncRepository", "同步情绪记录: ${cloudLog.mood} @ ${cloudLog.date}")
                }
            }
            
            android.util.Log.d("SyncRepository", "情绪记录同步完成, 新增 $syncedCount 条")
            Result.success(syncedCount)
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "同步情绪记录失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    private fun parseCreatedAtToMillis(raw: String): Long? {
        val numeric = raw.trim().toLongOrNull()
        if (numeric != null) {
            return if (numeric in 1L..9_999_999_999L) numeric * 1000L else numeric
        }
        
        try {
            // ISO 8601 格式
            if (raw.contains("T") && raw.contains("Z")) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(raw)?.time
            }
            if (raw.contains("T") && raw.endsWith("Z")) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(raw)?.time
            }
        } catch (_: Exception) {}
        
        return null
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
    
    // ==================== 警报相关 ====================
    
    /**
     * 获取未读警报（家人端调用）
     */
    suspend fun getAlerts(
        unreadOnly: Boolean = true
    ): Result<List<com.silverlink.app.data.remote.AlertData>> = withContext(Dispatchers.IO) {
        try {
            cloudBase.getAlerts(currentDeviceId, unreadOnly)
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "获取警报失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 标记警报已读（家人端调用）
     */
    suspend fun dismissAlert(alertId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cloudBase.dismissAlert(alertId, currentDeviceId)
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "标记警报已读失败: ${e.message}")
            Result.failure(e)
        }
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

