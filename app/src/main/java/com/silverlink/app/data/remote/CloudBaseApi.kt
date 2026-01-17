package com.silverlink.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import kotlinx.serialization.Serializable

/**
 * 腾讯云 CloudBase 云函数 HTTP API
 * 轻量化架构：Android 端 -> HTTP 请求 -> 云函数 -> 数据库
 */
interface CloudBaseApi {
    
    /**
     * 创建配对码（家人端调用）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("pairing/create")
    suspend fun createPairingCode(@Body request: CreatePairingRequest): ApiResponse<PairingCodeData>
    
    /**
     * 验证配对码（长辈端调用）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("pairing/verify")
    suspend fun verifyPairingCode(@Body request: VerifyPairingRequest): ApiResponse<PairingResult>
    
    /**
     * 添加服药记录
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("medication/log")
    suspend fun addMedicationLog(@Body request: MedicationLogRequest): ApiResponse<Unit>
    
    /**
     * 查询服药记录（家人端查看长辈）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("medication/query")
    suspend fun getMedicationLogs(@Body request: QueryMedicationRequest): ApiResponse<List<MedicationLogData>>
    
    /**
     * 添加情绪记录
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("mood/log")
    suspend fun addMoodLog(@Body request: MoodLogRequest): ApiResponse<Unit>
    
    /**
     * 查询情绪记录（家人端查看长辈）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("mood/query")
    suspend fun getMoodLogs(@Body request: QueryMoodRequest): ApiResponse<List<MoodLogData>>
    
    /**
     * 获取配对的长辈设备ID（家人端调用）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("pairing/get-elder")
    suspend fun getPairedElderDeviceId(@Body request: GetPairedElderRequest): ApiResponse<String?>
}

// ==================== 请求数据类 ====================

@Serializable
data class CreatePairingRequest(
    val code: String,
    val elderName: String,
    val familyDeviceId: String,
    val expiresInMinutes: Int = 30
)

@Serializable
data class VerifyPairingRequest(
    val code: String,
    val elderDeviceId: String
)

@Serializable
data class MedicationLogRequest(
    val elderDeviceId: String,
    val medicationId: Int,
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,
    val status: String,
    val date: String? = null
)

@Serializable
data class QueryMedicationRequest(
    val elderDeviceId: String,
    val date: String? = null
)

@Serializable
data class MoodLogRequest(
    val elderDeviceId: String,
    val mood: String,
    val note: String,
    val conversationSummary: String = "",
    val date: String? = null
)

@Serializable
data class QueryMoodRequest(
    val elderDeviceId: String,
    val days: Int = 7
)

@Serializable
data class GetPairedElderRequest(
    val familyDeviceId: String
)

// ==================== 响应数据类 ====================

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val errorCode: String? = null
)

@Serializable
data class PairingCodeData(
    val code: String,
    val elderName: String,
    val expiresAt: String
)

@Serializable
data class PairingResult(
    val elderName: String,
    val familyDeviceId: String,
    val pairedAt: String
)

@Serializable
data class MedicationLogData(
    val id: String,
    val medicationId: Int,
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,
    val status: String,
    val date: String,
    val createdAt: String
)

@Serializable
data class MoodLogData(
    val id: String,
    val mood: String,
    val note: String,
    val conversationSummary: String,
    val date: String,
    val createdAt: String
)
