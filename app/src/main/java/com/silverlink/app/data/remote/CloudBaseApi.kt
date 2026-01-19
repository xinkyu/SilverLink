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
    
    // ==================== 紧急事件 ====================
    
    /**
     * 上报紧急事件（老人端跌倒时调用）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("emergency/report")
    suspend fun reportEmergency(@Body request: EmergencyReportRequest): ApiResponse<EmergencyEventData>
    
    /**
     * 查询紧急事件（家人端轮询调用）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("emergency/query")
    suspend fun queryEmergencyEvents(@Body request: QueryEmergencyRequest): ApiResponse<List<EmergencyEventData>>
    
    /**
     * 标记紧急事件已处理
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("emergency/resolve")
    suspend fun resolveEmergency(@Body request: ResolveEmergencyRequest): ApiResponse<Unit>
    
    // ==================== 药品管理 ====================
    
    /**
     * 添加药品（家人端为长辈添加）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("medication/add")
    suspend fun addMedication(@Body request: AddMedicationRequest): ApiResponse<MedicationData>
    
    /**
     * 获取药品列表
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("medication/list")
    suspend fun getMedicationList(@Body request: GetMedicationListRequest): ApiResponse<List<MedicationData>>

    /**
     * 更新药品时间
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("medication/update")
    suspend fun updateMedication(@Body request: UpdateMedicationRequest): ApiResponse<MedicationData>
    
    /**
     * 删除药品
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("medication/delete")
    suspend fun deleteMedication(@Body request: DeleteMedicationRequest): ApiResponse<Unit>
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
    val familyDeviceId: String? = null,
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
    val familyDeviceId: String? = null,
    val days: Int = 7
)

@Serializable
data class GetPairedElderRequest(
    val familyDeviceId: String
)

@Serializable
data class AddMedicationRequest(
    val elderDeviceId: String,
    val familyDeviceId: String,
    val name: String,
    val dosage: String,
    val times: String  // 逗号分隔的时间，如 "08:00,12:00,18:00"
)

@Serializable
data class UpdateMedicationRequest(
    val elderDeviceId: String,
    val name: String,
    val dosage: String,
    val times: String
)

@Serializable
data class GetMedicationListRequest(
    val elderDeviceId: String
)

@Serializable
data class DeleteMedicationRequest(
    val elderDeviceId: String,
    val medicationId: String
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

@Serializable
data class MedicationData(
    val id_: String,
    val name: String,
    val dosage: String,
    val times: String,
    val createdAt: String,
    val createdBy: String  // "family" | "elder"
)

// ==================== 紧急事件数据类 ====================

@Serializable
data class EmergencyReportRequest(
    val elderDeviceId: String,
    val eventType: String = "fall",  // 事件类型：fall（跌倒）
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class QueryEmergencyRequest(
    val familyDeviceId: String,
    val elderDeviceId: String? = null,  // 可选，指定查询哪个老人
    val onlyUnresolved: Boolean = true  // 只查询未处理的事件
)

@Serializable
data class ResolveEmergencyRequest(
    val eventId: String,
    val familyDeviceId: String
)

@Serializable
data class EmergencyEventData(
    val id: String,
    val elderDeviceId: String,
    val elderName: String = "",
    val eventType: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
    val resolved: Boolean = false,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null
)
