package com.silverlink.app.feature.memory

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.ContentItem
import com.silverlink.app.data.remote.model.QwenVisionRequest
import com.silverlink.app.data.remote.model.VisionInput
import com.silverlink.app.data.remote.model.VisionMessage

/**
 * 照片分析结果
 */
data class PhotoAnalysisResult(
    val description: String,          // AI 生成的描述
    val people: List<String>,         // 识别到的人物
    val location: String?,            // 推测的地点
    val tags: List<String>,           // 场景标签
    val estimatedDate: String?,       // 推测的日期（基于图片内容）
    val rawResponse: String           // 原始 AI 响应
)

/**
 * AI 返回的 JSON 结构
 */
private data class PhotoAnalysisJson(
    @SerializedName("description")
    val description: String?,
    @SerializedName("people")
    val people: List<String>?,
    @SerializedName("location")
    val location: String?,
    @SerializedName("tags")
    val tags: List<String>?,
    @SerializedName("estimated_date")
    val estimatedDate: String?,
    @SerializedName("scene")
    val scene: String?
)

/**
 * 照片分析服务
 * 使用 Qwen-VL 视觉模型分析照片内容，生成描述和元数据
 */
class PhotoAnalysisService {
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "PhotoAnalysisService"
        
        private const val SYSTEM_PROMPT = """你是一个专门分析家庭照片的AI助手。请仔细分析这张照片，为老年人的家庭相册生成温馨的描述。

请以JSON格式返回以下信息：
{
  "description": "用第三人称描述照片场景，语气温馨亲切，适合老年人阅读。例如：'这是一张全家福，大家围坐在餐桌前，脸上洋溢着幸福的笑容。'",
  "people": ["照片中可能的人物角色，如'爷爷','奶奶','儿子','孙子'等"],
  "location": "推测的地点，如'客厅','公园','餐厅','故宫'等",
  "tags": ["场景标签，如'家庭聚会','旅行','节日','户外'等"],
  "estimated_date": "如果能从照片内容推测时间，返回如'2018年春节'，否则返回null",
  "scene": "场景类型：portrait/group/landscape/event/other"
}

重要规则：
1. 描述要温馨、积极、适合老年人阅读
2. 人物使用通用称呼（爷爷、奶奶、儿子等），不要编造名字
3. 如果看不清或无法确定，对应字段返回空数组或null
4. 只返回JSON，不要有其他文字说明"""
    }
    
    /**
     * 分析照片内容
     * @param bitmap 照片
     * @return 分析结果
     */
    suspend fun analyzePhoto(bitmap: Bitmap): Result<PhotoAnalysisResult> {
        return try {
            // 将 Bitmap 转换为 Base64
            val base64Image = ImageUtils.bitmapToDataUrl(bitmap)
            
            // 构建请求
            val request = QwenVisionRequest(
                model = "qwen-vl-plus",
                input = VisionInput(
                    messages = listOf(
                        VisionMessage(
                            role = "user",
                            content = listOf(
                                ContentItem.image(base64Image),
                                ContentItem.text(SYSTEM_PROMPT)
                            )
                        )
                    )
                )
            )
            
            // 调用 API
            val response = RetrofitClient.visionApi.analyzeImage(request)
            
            // 解析响应
            val textContent = response.output.choices?.firstOrNull()
                ?.message?.content?.firstOrNull()?.text
            
            if (textContent.isNullOrBlank()) {
                return Result.failure(Exception("AI 未返回有效内容"))
            }
            
            Log.d(TAG, "AI Response: $textContent")
            
            // 从响应中提取 JSON
            val jsonStr = extractJson(textContent)
            val analysisJson = gson.fromJson(jsonStr, PhotoAnalysisJson::class.java)
            
            val result = PhotoAnalysisResult(
                description = analysisJson.description ?: "这是一张珍贵的照片。",
                people = analysisJson.people ?: emptyList(),
                location = analysisJson.location,
                tags = analysisJson.tags ?: emptyList(),
                estimatedDate = analysisJson.estimatedDate,
                rawResponse = textContent
            )
            
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Photo analysis failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 从 AI 响应中提取 JSON 部分
     */
    private fun extractJson(text: String): String {
        val jsonPattern = Regex("""\{[\s\S]*\}""")
        val match = jsonPattern.find(text)
        return match?.value ?: text
    }
    
    /**
     * 生成简短描述（用于列表展示）
     */
    fun generateShortDescription(result: PhotoAnalysisResult): String {
        return result.description.take(50).let {
            if (result.description.length > 50) "$it..." else it
        }
    }
}
