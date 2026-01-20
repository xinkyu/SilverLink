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
    
    // ==================== 警报管理 ====================
    
    /**
     * 发送警报（老人端调用，通知家人端）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("alert/send")
    suspend fun sendAlert(@Body request: SendAlertRequest): ApiResponse<Unit>
    
    /**
     * 查询警报（家人端轮询）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("alert/query")
    suspend fun getAlerts(@Body request: QueryAlertRequest): ApiResponse<List<AlertData>>
    
    /**
     * 标记警报已读（家人端调用）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("alert/dismiss")
    suspend fun dismissAlert(@Body request: DismissAlertRequest): ApiResponse<Unit>
    
    // ==================== 记忆照片管理 ====================
    
    /**
     * 获取记忆照片列表
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("memory-photo-list")
    suspend fun getMemoryPhotos(@Body request: GetPhotosRequest): ApiResponse<List<MemoryPhotoData>>
    
    /**
     * 搜索记忆照片（根据自然语言查询）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("memory-photo-search")
    suspend fun searchMemoryPhotos(@Body request: SearchPhotosRequest): ApiResponse<List<MemoryPhotoData>>
    
    /**
     * 获取照片上传凭证（用于直传 COS）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("memory-photo-credentials")
    suspend fun getPhotoUploadCredentials(@Body request: PhotoCredentialsRequest): ApiResponse<PhotoUploadCredentials>
    
    /**
     * 保存照片元数据（图片已直传 COS 后调用）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("memory-photo-save")
    suspend fun savePhotoMetadata(@Body request: SavePhotoMetadataRequest): ApiResponse<MemoryPhotoData>
    
    // ==================== 认知评估管理 ====================
    
    /**
     * 记录认知评估结果
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("cognitive-log")
    suspend fun logCognitiveResult(@Body request: CognitiveLogRequest): ApiResponse<Unit>
    
    /**
     * 获取认知评估报告（家人端查看）
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("cognitive-report")
    suspend fun getCognitiveReport(@Body request: GetCognitiveReportRequest): ApiResponse<CognitiveReportData>
    
    // ==================== 声音复刻管理 ====================
    
    /**
     * 上传声音复刻音频到云存储
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("voice-audio-upload")
    suspend fun uploadVoiceAudio(@Body request: VoiceAudioUploadRequest): ApiResponse<VoiceAudioUploadResult>

    /**
     * 获取声音复刻音频上传凭证
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("voice-audio-credentials")
    suspend fun getVoiceUploadCredentials(@Body request: VoiceCredentialsRequest): ApiResponse<PhotoUploadCredentials>
    
    /**
     * 获取声音复刻音频公网URL
     */
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("voice-audio-get-url")
    suspend fun getVoicePublicUrl(@Body request: VoiceGetUrlRequest): ApiResponse<VoiceGetUrlResult>
}

// ==================== 请求数据类 ====================

@Serializable
data class CreatePairingRequest(
    val code: String,
    val elderName: String,
    val elderProfile: String = "",
    val dialect: String = "NONE",
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

@Serializable
data class SendAlertRequest(
    val elderDeviceId: String,
    val alertType: String,      // "inactivity" | "sos" | "medication_missed"
    val message: String,
    val elderName: String = ""
)

@Serializable
data class QueryAlertRequest(
    val familyDeviceId: String,
    val unreadOnly: Boolean = true
)

@Serializable
data class DismissAlertRequest(
    val alertId: String,
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
    val elderProfile: String = "",
    val dialect: String = "NONE",
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

@Serializable
data class AlertData(
    val id: String,
    val alertType: String,
    val message: String,
    val elderName: String,
    val elderDeviceId: String,
    val isRead: Boolean,
    val createdAt: String
)

// ==================== 记忆照片相关 ====================

@Serializable
data class GetPhotosRequest(
    val elderDeviceId: String,
    val familyDeviceId: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20,
    val sinceTimestamp: Long? = null  // 获取此时间戳之后的照片（用于增量同步）
)

@Serializable
data class SearchPhotosRequest(
    val elderDeviceId: String,
    val query: String,                // 自然语言查询
    val limit: Int = 10
)

@Serializable
data class MemoryPhotoData(
    val id: String,
    val elderDeviceId: String,
    val familyDeviceId: String,
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val description: String,
    val aiDescription: String,
    val takenDate: String? = null,
    val location: String? = null,
    val people: String? = null,
    val tags: String? = null,
    val createdAt: String
)

// ==================== 认知评估相关 ====================

@Serializable
data class CognitiveLogRequest(
    val elderDeviceId: String,
    val photoId: String,
    val questionType: String,         // "person", "location", "date", "event"
    val expectedAnswer: String,
    val actualAnswer: String,
    val isCorrect: Boolean,
    val responseTimeMs: Long,
    val confidence: Float = 0f
)

@Serializable
data class GetCognitiveReportRequest(
    val elderDeviceId: String,
    val familyDeviceId: String? = null,
    val days: Int = 7                 // 统计最近几天的数据
)

@Serializable
data class CognitiveReportData(
    val totalQuestions: Int,
    val correctAnswers: Int,
    val correctRate: Float,
    val averageResponseTimeMs: Long,
    val trend: String,                // "improving", "stable", "declining"
    val startDate: String,
    val endDate: String
)

// ==================== 直传 COS 相关 ====================

@Serializable
data class PhotoCredentialsRequest(
    val elderDeviceId: String,
    val familyDeviceId: String,
    val fileExtension: String = "jpg"
)

@Serializable
data class PhotoUploadCredentials(
    val photoId: String,
    val cloudPath: String,
    val uploadUrl: String? = null,  // 可能为 null，表示需要回退到 Base64 方式
    val authorization: String = "",
    val token: String = "",
    val contentType: String? = null,
    val fileId: String = "",
    val cosFileId: String? = null,
    val expiresAt: String = "",
    val fallbackToBase64: Boolean = false,  // 是否需要回退到 Base64 方式
    val message: String? = null,
    val directUrl: String? = null  // 直接 COS URL（需要桶设置为公共读取）
)

@Serializable
data class SavePhotoMetadataRequest(
    val elderDeviceId: String,
    val familyDeviceId: String,
    val photoId: String,
    val cloudPath: String,
    val fileId: String? = null,
    val description: String = "",
    val aiDescription: String = "",
    val takenDate: String? = null,
    val location: String? = null,
    val people: String? = null,
    val tags: String? = null
)

// ==================== 声音复刻相关 ====================

@Serializable
data class VoiceAudioUploadRequest(
    val familyDeviceId: String,
    val audioBase64: String,
    val format: String = "wav"
)

@Serializable
data class VoiceAudioUploadResult(
    val url: String = "",
    val fileId: String = "",
    val cloudPath: String = ""
)

@Serializable
data class VoiceCredentialsRequest(
    val familyDeviceId: String,
    val format: String = "wav"
)

@Serializable
data class VoiceGetUrlRequest(
    val fileId: String
)

@Serializable
data class VoiceGetUrlResult(
    val url: String,
    val fileId: String
)
