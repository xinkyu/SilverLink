package com.silverlink.app.feature.chat

import android.util.Base64
import android.util.Log
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.AsrContentItem
import com.silverlink.app.data.remote.model.AsrInput
import com.silverlink.app.data.remote.model.AsrMessage
import com.silverlink.app.data.remote.model.QwenAsrRequest
import java.io.File

/**
 * 语音识别服务 - 调用 Qwen-Audio API 进行语音转文字
 */
class SpeechRecognitionService {

    companion object {
        private const val TAG = "SpeechRecognitionService"
    }

    /**
     * 识别音频文件
     * @param audioFilePath 音频文件路径 (.m4a)
     * @return 识别的文字，失败返回 Result.failure
     */
    suspend fun recognize(audioFilePath: String): Result<String> {
        return try {
            val file = File(audioFilePath)
            if (!file.exists()) {
                return Result.failure(Exception("音频文件不存在"))
            }

            // 读取文件并转换为 Base64
            val audioBytes = file.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            
            Log.d(TAG, "Audio file size: ${audioBytes.size} bytes")

            // 构建请求 (多模态生成格式)
            val request = QwenAsrRequest(
                model = "qwen2-audio-instruct",
                input = AsrInput(
                    messages = listOf(
                        AsrMessage(
                            role = "user",
                            content = listOf(
                                AsrContentItem.audio(base64Audio, "m4a"),
                                AsrContentItem.text("请将这段语音转换为文字，只输出转换后的文字内容，不要添加任何其他说明。")
                            )
                        )
                    )
                )
            )

            // 调用 API
            val response = RetrofitClient.asrApi.transcribe(request)
            
            // 解析结果
            val text = response.output?.choices?.firstOrNull()?.message?.content?.firstOrNull()?.text
            
            if (text.isNullOrBlank()) {
                Result.failure(Exception("未能识别语音内容"))
            } else {
                Log.d(TAG, "Recognition result: $text")
                Result.success(text.trim())
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Speech recognition failed", e)
            Result.failure(Exception("语音识别失败: ${e.message}"))
        }
    }
}
