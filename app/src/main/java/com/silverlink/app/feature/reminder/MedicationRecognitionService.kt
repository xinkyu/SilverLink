package com.silverlink.app.feature.reminder

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.ContentItem
import com.silverlink.app.data.remote.model.QwenVisionRequest
import com.silverlink.app.data.remote.model.VisionInput
import com.silverlink.app.data.remote.model.VisionMessage
import java.io.ByteArrayOutputStream

/**
 * AI 识别到的药品信息
 */
data class RecognizedMedication(
    val name: String,
    val dosage: String,
    val times: List<String>,
    val rawResponse: String
)

/**
 * AI 返回的 JSON 结构
 */
data class MedicationJson(
    @SerializedName("name")
    val name: String?,
    @SerializedName("dosage")
    val dosage: String?,
    @SerializedName("times")
    val times: List<String>?,
    @SerializedName("frequency")
    val frequency: String?
)

/**
 * 药品识别服务
 * 使用 Qwen-VL 视觉模型分析药品包装图片
 */
class MedicationRecognitionService {
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "MedicationRecognition"
        
        private const val SYSTEM_PROMPT = """你是一个药品包装识别助手。请仔细分析这张药品包装图片，提取以下信息并以JSON格式返回：
{
  "name": "药品名称",
  "dosage": "每次服用剂量（如：1片、2粒、5ml）",
  "times": ["08:00", "12:00", "18:00"],
  "frequency": "每天几次"
}

重要规则：
1. 时间格式必须为 HH:mm（24小时制）
2. 如果说明书写"一日三次"或"每日3次"，请设置 times 为 ["08:00", "12:00", "18:00"]
3. 如果写"一日两次"或"每日2次"，请设置 times 为 ["08:00", "20:00"]
4. 如果写"一日一次"或"每日1次"，请设置 times 为 ["08:00"]
5. 如果写"饭后服用"，请根据常规用餐时间（早8点、午12点、晚18点）推断
6. 如果写"睡前服用"，请设置时间为 ["21:00"]
7. 如果无法识别某个字段，对应字段返回空字符串或空数组
8. 只返回JSON，不要有其他文字说明"""
    }
    
    /**
     * 分析药品图片
     * @param bitmap 拍摄的药品包装图片
     * @return 识别结果，失败返回 null
     */
    suspend fun recognizeMedication(bitmap: Bitmap): Result<RecognizedMedication> {
        return try {
            // 将 Bitmap 转换为 Base64
            val base64Image = bitmapToBase64(bitmap)
            
            // 构建请求
            val request = QwenVisionRequest(
                model = "qwen-vl-plus",
                input = VisionInput(
                    messages = listOf(
                        VisionMessage(
                            role = "user",
                            content = listOf(
                                ContentItem.image("data:image/jpeg;base64,$base64Image"),
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
            val medicationJson = gson.fromJson(jsonStr, MedicationJson::class.java)
            
            val recognized = RecognizedMedication(
                name = medicationJson.name ?: "",
                dosage = medicationJson.dosage ?: "",
                times = medicationJson.times ?: listOf("08:00"),
                rawResponse = textContent
            )
            
            Result.success(recognized)
            
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 将 Bitmap 转换为 Base64 字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减少传输大小
        val scaledBitmap = if (bitmap.width > 1024 || bitmap.height > 1024) {
            val scale = minOf(1024f / bitmap.width, 1024f / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    /**
     * 从 AI 响应中提取 JSON 部分
     */
    private fun extractJson(text: String): String {
        // 尝试查找 JSON 块
        val jsonPattern = Regex("""\{[\s\S]*\}""")
        val match = jsonPattern.find(text)
        return match?.value ?: text
    }
}
