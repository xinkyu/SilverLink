package com.silverlink.app.feature.chat

import android.util.Base64
import android.util.Log
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.VoiceAudioUploadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 阿里云 CosyVoice 声音复刻服务
 * 
 * 使用流程：
 * 1. 录制 10-20 秒的音频样本
 * 2. 上传音频到云存储获取公网URL
 * 3. 调用 createVoice() 创建复刻音色
 * 4. 轮询查询音色状态直到 OK
 * 5. 在 TTS 时使用该 voice_id
 * 
 * 复刻音色支持：
 * - 方言指令（请用四川话表达。）
 * - 情感指令（你说话的情感是happy。）
 * 
 * API文档: https://help.aliyun.com/zh/model-studio/developer-reference/cosyvoice-voice-cloning
 */
class VoiceCloningService {
    
    companion object {
        private const val TAG = "VoiceCloningService"
        
        // RESTful API 端点（根据阿里云官方文档）
        // 创建/查询/更新/删除音色都使用同一个端点，通过 action 区分
        private const val API_URL = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization"
        
        // 声音复刻要求：10-20秒的清晰语音，最长60秒
        const val MIN_DURATION_SECONDS = 10
        const val MAX_DURATION_SECONDS = 60
        const val RECOMMENDED_DURATION_SECONDS = 15
        
        // 驱动音色的语音合成模型
        const val TARGET_MODEL = "cosyvoice-v3-plus"
        
        // 轮询配置
        private const val POLL_INTERVAL_MS = 5000L  // 5秒
        private const val MAX_POLL_ATTEMPTS = 60    // 最多等待5分钟
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * 创建复刻音色（完整流程）
     * 
     * @param audioFile 音频文件（建议 10-20 秒清晰语音）
     * @param voicePrefix 音色名称前缀（仅允许数字和字母，不超过10个字符）
     * @param familyDeviceId 家人设备ID（用于上传音频）
     * @return 复刻音色ID，失败返回错误
     */
    suspend fun createVoice(
        audioFile: File, 
        voicePrefix: String,
        familyDeviceId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating cloned voice from file: ${audioFile.absolutePath}, size: ${audioFile.length()}")
            
            // Step 1: 上传音频到云存储获取公网URL
            Log.d(TAG, "Step 1: Uploading audio to cloud storage...")
            val audioUrl = uploadAudioToCloud(audioFile, familyDeviceId)
            if (audioUrl == null) {
                return@withContext Result.failure(Exception("音频上传失败，无法获取公网URL"))
            }
            Log.d(TAG, "Audio uploaded, URL: ${audioUrl.take(50)}...")
            
            // Step 2: 调用创建音色API
            Log.d(TAG, "Step 2: Creating voice enrollment...")
            val voiceId = submitVoiceCreation(audioUrl, voicePrefix)
            if (voiceId == null) {
                return@withContext Result.failure(Exception("创建音色请求失败"))
            }
            Log.d(TAG, "Voice creation submitted, voice_id: $voiceId")
            
            // Step 3: 轮询查询音色状态
            Log.d(TAG, "Step 3: Polling voice status...")
            val finalStatus = pollVoiceStatus(voiceId)
            
            when (finalStatus) {
                "OK" -> {
                    Log.d(TAG, "Voice cloned successfully! voice_id: $voiceId")
                    Result.success(voiceId)
                }
                "UNDEPLOYED" -> {
                    Log.e(TAG, "Voice clone failed: status=$finalStatus")
                    Result.failure(Exception("音色复刻失败，请检查音频质量后重试"))
                }
                else -> {
                    Log.e(TAG, "Voice clone timeout or unknown status: $finalStatus")
                    Result.failure(Exception("音色复刻超时，请稍后重试"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice clone error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 上传音频到云存储
     */
    /**
     * 上传音频到云存储
     * 修改为直传模式，解决 Cloud Function 6MB 限制问题
     */
    private suspend fun uploadAudioToCloud(audioFile: File, familyDeviceId: String): String? {
        return try {
            val result = CloudBaseService.uploadVoiceDirectToCOS(
                familyDeviceId = familyDeviceId,
                audioFile = audioFile
            )
            
            if (result.isSuccess) {
                result.getOrNull()
            } else {
                Log.e(TAG, "Upload failed: ${result.exceptionOrNull()?.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload audio error", e)
            null
        }
    }
    
    /**
     * 提交创建音色请求
     * 
     * 根据阿里云官方文档，请求格式如下：
     * - 端点: /services/audio/tts/customization
     * - action, target_model, prefix, url, language_hints 都在 input 中
     */
    private fun submitVoiceCreation(audioUrl: String, voicePrefix: String): String? {
        try {
            // 构建请求体 (根据阿里云官方文档格式)
            val requestJson = JSONObject().apply {
                put("model", "voice-enrollment")
                put("input", JSONObject().apply {
                    put("action", "create_voice")
                    put("target_model", TARGET_MODEL)
                    put("prefix", sanitizePrefix(voicePrefix))
                    put("url", audioUrl)
                    put("language_hints", JSONArray().apply {
                        put("zh")
                    })
                })
            }
            
            val requestBodyStr = requestJson.toString()
            val requestBody = requestBodyStr.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer ${RetrofitClient.getApiKey()}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            // 详细日志：显示完整的请求内容用于调试
            Log.d(TAG, "======= Voice Creation Request =======")
            Log.d(TAG, "URL: $API_URL")
            Log.d(TAG, "Audio URL (full): $audioUrl")
            Log.d(TAG, "Request JSON: $requestBodyStr")
            Log.d(TAG, "======================================")
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Create response code: ${response.code}, body: $responseBody")
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                
                // 检查错误
                if (json.has("code") && json.optString("code") != "Success" && json.optString("code").isNotEmpty()) {
                    Log.e(TAG, "API error: ${json.optString("message")}")
                    return null
                }
                
                // 获取 voice_id
                val output = json.optJSONObject("output")
                return output?.optString("voice_id")
            } else {
                Log.e(TAG, "Create request failed: HTTP ${response.code}, $responseBody")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Submit voice creation error", e)
            return null
        }
    }
    
    /**
     * 轮询查询音色状态
     */
    private suspend fun pollVoiceStatus(voiceId: String): String {
        for (attempt in 1..MAX_POLL_ATTEMPTS) {
            try {
                val status = queryVoiceStatus(voiceId)
                Log.d(TAG, "Poll attempt $attempt/$MAX_POLL_ATTEMPTS: status=$status")
                
                when (status) {
                    "OK", "UNDEPLOYED" -> return status
                    "DEPLOYING" -> {
                        // 继续等待
                        delay(POLL_INTERVAL_MS)
                    }
                    else -> {
                        // 未知状态，继续等待
                        delay(POLL_INTERVAL_MS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Poll error at attempt $attempt", e)
                delay(POLL_INTERVAL_MS)
            }
        }
        return "TIMEOUT"
    }
    
    /**
     * 查询单个音色状态
     */
    private fun queryVoiceStatus(voiceId: String): String? {
        try {
            val requestJson = JSONObject().apply {
                put("model", "voice-enrollment")
                put("input", JSONObject().apply {
                    put("action", "query_voice")
                    put("voice_id", voiceId)
                })
            }
            
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer ${RetrofitClient.getApiKey()}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val output = json.optJSONObject("output")
                return output?.optString("status")
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Query voice status error", e)
            return null
        }
    }
    
    /**
     * 查询已有的复刻音色列表
     */
    suspend fun listVoices(prefix: String? = null): Result<List<ClonedVoice>> = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("model", "voice-enrollment")
                put("input", JSONObject().apply {
                    put("action", "list_voice")
                    if (prefix != null) {
                        put("prefix", prefix)
                    }
                    put("page_index", 0)
                    put("page_size", 100)
                })
            }
            
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer ${RetrofitClient.getApiKey()}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val output = json.optJSONObject("output")
                val voices = output?.optJSONArray("voices") ?: JSONArray()
                
                val voiceList = mutableListOf<ClonedVoice>()
                for (i in 0 until voices.length()) {
                    val voiceJson = voices.getJSONObject(i)
                    voiceList.add(ClonedVoice(
                        voiceId = voiceJson.getString("voice_id"),
                        status = voiceJson.optString("status", ""),
                        createdAt = voiceJson.optString("gmt_create", "")
                    ))
                }
                
                Result.success(voiceList)
            } else {
                Result.failure(Exception("Failed to list voices: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "List voices error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除复刻音色
     */
    suspend fun deleteVoice(voiceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("model", "voice-enrollment")
                put("input", JSONObject().apply {
                    put("action", "delete_voice")
                    put("voice_id", voiceId)
                })
            }
            
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer ${RetrofitClient.getApiKey()}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d(TAG, "Voice deleted: $voiceId")
                Result.success(true)
            } else {
                Result.failure(Exception("Delete failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete voice error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 根据文件扩展名获取音频格式
     */
    private fun getAudioFormat(file: File): String {
        return when (file.extension.lowercase()) {
            "wav" -> "wav"
            "mp3" -> "mp3"
            "m4a", "aac" -> "m4a"
            else -> "wav" // 默认
        }
    }
    
    /**
     * 清理前缀，确保只包含允许的字符
     * 根据阿里云文档，prefix 只允许字母和数字
     */
    private fun sanitizePrefix(prefix: String): String {
        // 仅允许字母和数字，不超过10个字符
        return prefix
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .take(10)
            .ifEmpty { "voice" }
    }
    
    /**
     * 复刻音色信息
     */
    data class ClonedVoice(
        val voiceId: String,
        val status: String,
        val createdAt: String
    )
}
