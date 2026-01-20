package com.silverlink.app.data.remote

import android.util.Log
import com.silverlink.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    
    // 直接硬编码测试，排除本地配置文件的干扰
    private const val CLOUD_BASE_URL: String = "https://silverlink-9gdqj1ne4d834dab-1396514174.ap-shanghai.app.tcloudbase.com/"
    
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
    
    // ==================== 警报相关 ====================
    
    /**
     * 发送警报（老人端调用）
     * 用于通知家人端老人长时间无响应等情况
     */
    suspend fun sendAlert(
        elderDeviceId: String,
        alertType: String,
        message: String,
        elderName: String = ""
    ): Result<Unit> {
        return try {
            Log.d("CloudBase", "发送警报: type=$alertType, elderDeviceId=$elderDeviceId")
            val response = api.sendAlert(
                SendAlertRequest(
                    elderDeviceId = elderDeviceId,
                    alertType = alertType,
                    message = message,
                    elderName = elderName
                )
            )
            if (response.success) {
                Log.d("CloudBase", "警报发送成功")
                Result.success(Unit)
            } else {
                Log.e("CloudBase", "警报发送失败: ${response.message}")
                Result.failure(Exception(response.message ?: "发送警报失败"))
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "发送警报异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 查询未读警报（家人端调用）
     */
    suspend fun getAlerts(
        familyDeviceId: String,
        unreadOnly: Boolean = true
    ): Result<List<AlertData>> {
        return try {
            val response = api.getAlerts(
                QueryAlertRequest(
                    familyDeviceId = familyDeviceId,
                    unreadOnly = unreadOnly
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
    
    /**
     * 标记警报已读（家人端调用）
     */
    suspend fun dismissAlert(
        alertId: String,
        familyDeviceId: String
    ): Result<Unit> {
        return try {
            val response = api.dismissAlert(
                DismissAlertRequest(
                    alertId = alertId,
                    familyDeviceId = familyDeviceId
                )
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "标记警报失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== 记忆照片相关 ====================
    
    /**
     * 获取记忆照片列表
     * @param elderDeviceId 长辈设备ID
     * @param familyDeviceId 家人设备ID（可选，用于验证权限）
     * @param page 页码
     * @param pageSize 每页数量
     * @param sinceTimestamp 获取此时间戳之后的照片（用于增量同步）
     */
    suspend fun getMemoryPhotos(
        elderDeviceId: String,
        familyDeviceId: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
        sinceTimestamp: Long? = null
    ): Result<List<MemoryPhotoData>> {
        return try {
            val response = api.getMemoryPhotos(
                GetPhotosRequest(
                    elderDeviceId = elderDeviceId,
                    familyDeviceId = familyDeviceId,
                    page = page,
                    pageSize = pageSize,
                    sinceTimestamp = sinceTimestamp
                )
            )
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "获取照片列表异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 搜索记忆照片（根据自然语言查询）
     * @param elderDeviceId 长辈设备ID
     * @param query 查询文本（如"去年去北京"）
     * @param limit 返回结果数量
     */
    suspend fun searchMemoryPhotos(
        elderDeviceId: String,
        query: String,
        limit: Int = 10
    ): Result<List<MemoryPhotoData>> {
        return try {
            Log.d("CloudBase", "搜索照片: query=$query")
            val response = api.searchMemoryPhotos(
                SearchPhotosRequest(
                    elderDeviceId = elderDeviceId,
                    query = query,
                    limit = limit
                )
            )
            if (response.success && response.data != null) {
                Log.d("CloudBase", "搜索到 ${response.data.size} 张照片")
                Result.success(response.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "搜索照片异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // ==================== 认知评估相关 ====================
    
    /**
     * 记录认知评估结果
     */
    suspend fun logCognitiveResult(
        elderDeviceId: String,
        photoId: String,
        questionType: String,
        expectedAnswer: String,
        actualAnswer: String,
        isCorrect: Boolean,
        responseTimeMs: Long,
        confidence: Float = 0f
    ): Result<Unit> {
        return try {
            val response = api.logCognitiveResult(
                CognitiveLogRequest(
                    elderDeviceId = elderDeviceId,
                    photoId = photoId,
                    questionType = questionType,
                    expectedAnswer = expectedAnswer,
                    actualAnswer = actualAnswer,
                    isCorrect = isCorrect,
                    responseTimeMs = responseTimeMs,
                    confidence = confidence
                )
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "记录认知结果失败"))
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "记录认知结果异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取认知评估报告（家人端或长辈端查看）
     */
    suspend fun getCognitiveReport(
        elderDeviceId: String,
        familyDeviceId: String? = null,
        days: Int = 7
    ): Result<CognitiveReportData?> {
        return try {
            val response = api.getCognitiveReport(
                GetCognitiveReportRequest(
                    elderDeviceId = elderDeviceId,
                    familyDeviceId = familyDeviceId,
                    days = days
                )
            )
            if (response.success) {
                Result.success(response.data)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "获取认知报告异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // ==================== 直传 COS 相关 ====================
    
    /**
     * 获取照片上传凭证
     */
    suspend fun getPhotoUploadCredentials(
        elderDeviceId: String,
        familyDeviceId: String,
        fileExtension: String = "jpg"
    ): Result<PhotoUploadCredentials> {
        return try {
            Log.d("CloudBase", "获取上传凭证: elderDeviceId=$elderDeviceId")
            val response = api.getPhotoUploadCredentials(
                PhotoCredentialsRequest(
                    elderDeviceId = elderDeviceId,
                    familyDeviceId = familyDeviceId,
                    fileExtension = fileExtension
                )
            )
            if (response.success && response.data != null) {
                if (response.data.uploadUrl.isNullOrBlank()) {
                    Log.e("CloudBase", "获取上传凭证失败: 无法获取上传URL")
                    Result.failure(Exception(response.data.message ?: "无法获取上传URL"))
                } else {
                    Log.d("CloudBase", "获取上传凭证成功: cloudPath=${response.data.cloudPath}")
                    Result.success(response.data)
                }
            } else {
                Log.e("CloudBase", "获取上传凭证失败: ${response.message}")
                Result.failure(Exception(response.message ?: "获取上传凭证失败"))
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("CloudBase", "获取上传凭证异常: HTTP ${e.code()} ${errorBody ?: ""}", e)
            Result.failure(Exception("获取上传凭证异常: HTTP ${e.code()} ${errorBody ?: ""}".trim()))
        } catch (e: Exception) {
            Log.e("CloudBase", "获取上传凭证异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 保存照片元数据（图片已直传 COS 后调用）
     */
    suspend fun savePhotoMetadata(
        elderDeviceId: String,
        familyDeviceId: String,
        photoId: String,
        cloudPath: String,
        fileId: String? = null,
        description: String = "",
        aiDescription: String = "",
        takenDate: String? = null,
        location: String? = null,
        people: String? = null,
        tags: String? = null
    ): Result<MemoryPhotoData> {
        return try {
            Log.d("CloudBase", "保存照片元数据: photoId=$photoId")
            val response = api.savePhotoMetadata(
                SavePhotoMetadataRequest(
                    elderDeviceId = elderDeviceId,
                    familyDeviceId = familyDeviceId,
                    photoId = photoId,
                    cloudPath = cloudPath,
                    fileId = fileId,
                    description = description,
                    aiDescription = aiDescription,
                    takenDate = takenDate,
                    location = location,
                    people = people,
                    tags = tags
                )
            )
            if (response.success && response.data != null) {
                Log.d("CloudBase", "保存元数据成功: id=${response.data.id}")
                Result.success(response.data)
            } else {
                Log.e("CloudBase", "保存元数据失败: ${response.message}")
                Result.failure(Exception(response.message ?: "保存元数据失败"))
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "保存元数据异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 直传照片到 COS（完整流程）
     * 1. 获取上传凭证
     * 2. PUT 请求直传图片到 COS
     * 3. 保存元数据到数据库
     */
    suspend fun uploadPhotoDirectToCOS(
        elderDeviceId: String,
        familyDeviceId: String,
        imageBytes: ByteArray,
        description: String = "",
        aiDescription: String = "",
        takenDate: String? = null,
        location: String? = null,
        people: String? = null,
        tags: String? = null
    ): Result<MemoryPhotoData> {
        return try {
            val startTime = System.currentTimeMillis()
            
            // 1. 获取上传凭证
            Log.d("CloudStorage", "步骤1: 获取上传凭证")
            val credentialsResult = getPhotoUploadCredentials(elderDeviceId, familyDeviceId)
            val credentials = credentialsResult.getOrElse { 
                return Result.failure(Exception("获取上传凭证失败: ${it.message}"))
            }
            
            // 2. 直传到 COS
            // 重要：x-cos-meta-fileid 头必须使用 cosFileId（base64编码），而不是 fileId（cloud://路径）
            // 因为 CloudBase SDK 在计算签名时使用的是 cosFileId
            Log.d("CloudStorage", "步骤2: 直传云存储, 大小=${imageBytes.size / 1024}KB")
            Log.d("CloudStorage", "cosFileId=${credentials.cosFileId}")
            val uploadResult = uploadToCOS(
                uploadUrl = credentials.uploadUrl,
                authorization = credentials.authorization,
                token = credentials.token,
                imageBytes = imageBytes,
                contentType = credentials.contentType,
                cosFileId = credentials.cosFileId  // 使用 cosFileId 而非 fileId
            )
            if (uploadResult.isFailure) {
                return Result.failure(Exception("直传云存储失败: ${uploadResult.exceptionOrNull()?.message}"))
            }
            
            val uploadTime = System.currentTimeMillis() - startTime
            Log.d("CloudStorage", "直传成功, 耗时=${uploadTime}ms")
            
            // 3. 保存元数据
            Log.d("CloudStorage", "步骤3: 保存元数据")
            val photoResult = savePhotoMetadata(
                elderDeviceId = elderDeviceId,
                familyDeviceId = familyDeviceId,
                photoId = credentials.photoId,
                cloudPath = credentials.cloudPath,
                fileId = credentials.fileId,
                description = description,
                aiDescription = aiDescription,
                takenDate = takenDate,
                location = location,
                people = people,
                tags = tags
            )
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d("CloudStorage", "上传完成, 总耗时=${totalTime}ms")
            
            photoResult
        } catch (e: Exception) {
            Log.e("CloudStorage", "直传照片异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 使用 PUT 请求直传图片到 COS
     * 
     * 签名验证说明：
     * - COS 签名 (authorization) 中的 q-header-list 指定了哪些 header 参与签名计算
     * - 例如 q-header-list=host;x-cos-meta-fileid 表示签名包含 host 和 x-cos-meta-fileid
     * - 发送请求时必须包含这些 header，否则会 SignatureDoesNotMatch
     * - 重要：x-cos-meta-fileid 使用的是 cosFileId（base64编码），不是 fileId（cloud://路径）
     */
    private suspend fun uploadToCOS(
        uploadUrl: String?,
        authorization: String,
        token: String,
        imageBytes: ByteArray,
        contentType: String?,
        cosFileId: String?  // 注意：必须使用 cosFileId，不是 fileId
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (uploadUrl.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("无法获取上传URL"))
                }
                
                Log.d("CloudStorage", "COS 上传参数: url=$uploadUrl")
                Log.d("CloudStorage", "COS 上传参数: authorization=${authorization.take(50)}...")
                Log.d("CloudStorage", "COS 上传参数: cosFileId=$cosFileId")
                
                val safeContentType = (contentType ?: "image/jpeg").trim()
                val requestBody = okhttp3.RequestBody.create(safeContentType.toMediaType(), imageBytes)

                val requestBuilder = okhttp3.Request.Builder()
                    .url(uploadUrl)
                    .put(requestBody)
                    .addHeader("Content-Type", safeContentType)
                
                // 添加 COS 签名 - authorization 字符串包含完整的签名信息
                // 格式: q-sign-algorithm=sha1&q-ak=...&q-sign-time=...&q-key-time=...&q-header-list=...&q-url-param-list=&q-signature=...
                if (authorization.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", authorization.trim())
                }
                
                // 添加临时密钥的 token
                if (token.isNotBlank()) {
                    requestBuilder.addHeader("x-cos-security-token", token.trim())
                }
                
                // x-cos-meta-fileid 是签名中 q-header-list 包含的 header，必须添加
                // 必须使用 cosFileId（CloudBase SDK 签名时使用的值）
                if (!cosFileId.isNullOrBlank()) {
                    requestBuilder.addHeader("x-cos-meta-fileid", cosFileId.trim())
                }

                val request = requestBuilder.build()
                
                Log.d("CloudStorage", "COS PUT 请求 headers: ${request.headers}")

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d("CloudStorage", "COS PUT 成功, code=${response.code}")
                    Result.success(Unit)
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e("CloudStorage", "COS PUT 失败: ${response.code}, $errorBody")
                    Result.failure(Exception("COS 上传失败: ${response.code}"))
                }
            } catch (e: Exception) {
                Log.e("CloudStorage", "COS PUT 异常: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    // ==================== 位置相关 ====================
    
    /**
     * 上传位置（老人端调用）
     * 每5分钟上传一次位置，云端会保留最近2小时的记录
     */
    suspend fun updateLocation(
        elderDeviceId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float = 0f,
        address: String = ""
    ): Result<Unit> {
        return try {
            Log.d("CloudBase", "上传位置: lat=$latitude, lng=$longitude, accuracy=$accuracy")
            val response = api.updateLocation(
                UpdateLocationRequest(
                    elderDeviceId = elderDeviceId,
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = accuracy,
                    address = address
                )
            )
            if (response.success) {
                Log.d("CloudBase", "位置上传成功")
                Result.success(Unit)
            } else {
                Log.e("CloudBase", "位置上传失败: ${response.message}")
                Result.failure(Exception(response.message ?: "位置上传失败"))
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "位置上传异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 查询位置（家人端调用）
     * 返回老人的最新位置和最近2小时的位置历史
     */
    suspend fun queryLocation(
        elderDeviceId: String,
        familyDeviceId: String? = null
    ): Result<LocationQueryResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("CloudBase", "查询位置(OkHttp): elderDeviceId=$elderDeviceId")
            // 直接拼装 URL，绕过 Retrofit，确保万无一失
            val baseUrl = "https://silverlink-9gdqj1ne4d834dab-1396514174.ap-shanghai.app.tcloudbase.com/location-query"
            val url = "$baseUrl?elderDeviceId=$elderDeviceId&familyDeviceId=${familyDeviceId ?: ""}"
            
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                Log.d("CloudBase", "OkHttp响应: $bodyString")
                if (!bodyString.isNullOrBlank()) {
                    val apiResponse = retrofit2.converter.gson.GsonConverterFactory.create()
                        .responseBodyConverter(
                            com.google.gson.reflect.TypeToken.getParameterized(ApiResponse::class.java, LocationQueryResult::class.java).type,
                            arrayOf(),
                            retrofit
                        )?.convert(okhttp3.ResponseBody.create(null, bodyString)) as? ApiResponse<LocationQueryResult>

                    if (apiResponse != null && apiResponse.success && apiResponse.data != null) {
                         Result.success(apiResponse.data)
                    } else {
                         Result.success(LocationQueryResult(null, emptyList()))
                    }
                } else {
                    Result.failure(Exception("响应为空"))
                }
            } else {
                Log.e("CloudBase", "OkHttp错误: code=${response.code}, message=${response.message}")
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("CloudBase", "位置查询异常: ${e.message}", e)
            Result.failure(e)
        }
    }
}
