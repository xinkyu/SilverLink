package com.silverlink.app.data.remote

import android.util.Log
import com.silverlink.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 腾讯云 CloudBase 云函数服务
 * 
 * 架构说明：
 * - Android 端通过 HTTP 请求调用云函数
 * - 云函数处理业务逻辑并读写云数据库
 * - 无需集成官方 SDK，轻量且稳定
 * 
 * 部署步骤：
 * 1. 在腾讯云 CloudBase 控制台创建环境
 * 2. 部署云函数（见 cloud-functions 目录）
 * 3. 配置 HTTP 访问服务
 * 4. 将云函数访问地址填入 CLOUD_BASE_URL
 */
object CloudBaseService {
    
    // CloudBase URL is loaded from BuildConfig (set in local.properties)
    private val CLOUD_BASE_URL: String = BuildConfig.CLOUDBASE_URL
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }
    
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(CLOUD_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    val api: CloudBaseApi by lazy {
        retrofit.create(CloudBaseApi::class.java)
    }
    
    // ==================== 配对相关 ====================
    
    /**
     * 创建配对码（家人端调用）
     */
    suspend fun createPairingCode(
        code: String,
        elderName: String,
        familyDeviceId: String
    ): Result<PairingCodeData> {
        return try {
            Log.d("CloudBase", "创建配对码: code=$code, elderName=$elderName, deviceId=$familyDeviceId")
            val response = api.createPairingCode(
                CreatePairingRequest(
                    code = code,
                    elderName = elderName,
                    familyDeviceId = familyDeviceId
                )
            )
            Log.d("CloudBase", "创建配对码响应: success=${response.success}, message=${response.message}, data=${response.data}")
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message ?: "创建配对码失败"))
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "创建配对码异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 验证配对码（长辈端调用）
     */
    suspend fun verifyPairingCode(
        code: String,
        elderDeviceId: String
    ): Result<PairingResult?> {
        return try {
            Log.d("CloudBase", "验证配对码: code=$code, elderDeviceId=$elderDeviceId")
            val response = api.verifyPairingCode(
                VerifyPairingRequest(code = code, elderDeviceId = elderDeviceId)
            )
            Log.d("CloudBase", "验证配对码响应: success=${response.success}, message=${response.message}, data=${response.data}")
            if (response.success) {
                Result.success(response.data)
            } else {
                // 配对码无效不算错误，返回 null
                Log.d("CloudBase", "配对码验证失败: ${response.message}")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "验证配对码异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取已配对的长辈设备ID（家人端调用）
     */
    suspend fun getPairedElderDeviceId(familyDeviceId: String): Result<String?> {
        return try {
            val response = api.getPairedElderDeviceId(
                GetPairedElderRequest(familyDeviceId = familyDeviceId)
            )
            if (response.success) {
                Result.success(response.data)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== 服药记录 ====================
    
    /**
     * 添加服药记录
     */
    suspend fun addMedicationLog(
        elderDeviceId: String,
        medicationId: Int,
        medicationName: String,
        dosage: String,
        scheduledTime: String,
        status: String
    ): Result<Unit> {
        return try {
            val response = api.addMedicationLog(
                MedicationLogRequest(
                    elderDeviceId = elderDeviceId,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    dosage = dosage,
                    scheduledTime = scheduledTime,
                    status = status
                )
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "添加服药记录失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 查询服药记录
     */
    suspend fun getMedicationLogs(
        elderDeviceId: String,
        familyDeviceId: String? = null,
        date: String? = null
    ): Result<List<MedicationLogData>> {
        return try {
            val response = api.getMedicationLogs(
                QueryMedicationRequest(
                    elderDeviceId = elderDeviceId,
                    familyDeviceId = familyDeviceId,
                    date = date
                )
            )
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== 情绪记录 ====================
    
    /**
     * 添加情绪记录
     */
    suspend fun addMoodLog(
        elderDeviceId: String,
        mood: String,
        note: String,
        conversationSummary: String = ""
    ): Result<Unit> {
        return try {
            val response = api.addMoodLog(
                MoodLogRequest(
                    elderDeviceId = elderDeviceId,
                    mood = mood,
                    note = note,
                    conversationSummary = conversationSummary
                )
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "添加情绪记录失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 查询情绪记录
     */
    suspend fun getMoodLogs(
        elderDeviceId: String,
        familyDeviceId: String? = null,
        days: Int = 7
    ): Result<List<MoodLogData>> {
        return try {
            val response = api.getMoodLogs(
                QueryMoodRequest(
                    elderDeviceId = elderDeviceId,
                    familyDeviceId = familyDeviceId,
                    days = days
                )
            )
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== 药品管理 ====================
    
    /**
     * 添加药品（家人端为长辈添加）
     */
    suspend fun addMedication(
        elderDeviceId: String,
        familyDeviceId: String,
        name: String,
        dosage: String,
        times: String
    ): Result<MedicationData> {
        return try {
            Log.d("CloudBase", "添加药品: name=$name, dosage=$dosage, times=$times")
            val response = api.addMedication(
                AddMedicationRequest(
                    elderDeviceId = elderDeviceId,
                    familyDeviceId = familyDeviceId,
                    name = name,
                    dosage = dosage,
                    times = times
                )
            )
            if (response.success && response.data != null) {
                Log.d("CloudBase", "添加药品成功: ${response.data}")
                Result.success(response.data)
            } else {
                Log.e("CloudBase", "添加药品失败: ${response.message}")
                Result.failure(Exception(response.message ?: "添加药品失败"))
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "添加药品异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 更新药品时间（长辈端/家人端）
     */
    suspend fun updateMedicationTimes(
        elderDeviceId: String,
        name: String,
        dosage: String,
        times: String
    ): Result<MedicationData> {
        return try {
            val response = api.updateMedication(
                UpdateMedicationRequest(
                    elderDeviceId = elderDeviceId,
                    name = name,
                    dosage = dosage,
                    times = times
                )
            )
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message ?: "更新药品失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取药品列表
     */
    suspend fun getMedicationList(
        elderDeviceId: String
    ): Result<List<MedicationData>> {
        return try {
            val response = api.getMedicationList(
                GetMedicationListRequest(elderDeviceId = elderDeviceId)
            )
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 删除药品
     */
    suspend fun deleteMedication(
        elderDeviceId: String,
        medicationId: String
    ): Result<Unit> {
        return try {
            val response = api.deleteMedication(
                DeleteMedicationRequest(
                    elderDeviceId = elderDeviceId,
                    medicationId = medicationId
                )
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "删除药品失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
