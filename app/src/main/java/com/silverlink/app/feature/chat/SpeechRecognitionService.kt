package com.silverlink.app.feature.chat

import android.content.Context
import android.util.Base64
import android.util.Log
import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.model.SpeechResult
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.AsrContentItem
import com.silverlink.app.data.remote.model.AsrInput
import com.silverlink.app.data.remote.model.AsrMessage
import com.silverlink.app.data.remote.model.QwenAsrRequest
import com.silverlink.app.feature.emotion.EmotionRecognitionService
import java.io.File

/**
 * 语音识别服务 - 调用 Qwen-Audio API 进行语音转文字，使用本地 ONNX 模型进行情感分析
 */
class SpeechRecognitionService(private val context: Context) {

    companion object {
        private const val TAG = "SpeechRecognitionService"
    }

    /**
     * 识别音频文件，返回文本和情绪
     * @param audioFilePath 音频文件路径 (.m4a)
     * @return SpeechResult 包含识别的文字和检测到的情绪
     */
    suspend fun recognize(audioFilePath: String): Result<SpeechResult> {
        return try {
            val file = File(audioFilePath)
            if (!file.exists()) {
                return Result.failure(Exception("音频文件不存在"))
            }

            // 读取文件并转换为 Base64
            val audioBytes = file.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            Log.d(TAG, "Audio file size: ${audioBytes.size} bytes")

            // 步骤1: 语音转文字 (仍使用 Qwen-Audio API)
            val textResult = transcribeAudio(base64Audio)
            if (textResult.isFailure) {
                return Result.failure(textResult.exceptionOrNull() ?: Exception("语音识别失败"))
            }
            val text = textResult.getOrThrow()

            // 步骤2: 使用本地 ONNX 模型分析情绪
            val emotion = analyzeEmotion(audioFilePath, text)

            Log.d(TAG, "Recognition result: text='$text', emotion=${emotion.name}")
            Result.success(SpeechResult(text = text, emotion = emotion))

        } catch (e: Exception) {
            Log.e(TAG, "Speech recognition failed", e)
            Result.failure(Exception("语音识别失败: ${e.message}"))
        }
    }

    /**
     * 语音转文字
     */
    private suspend fun transcribeAudio(base64Audio: String): Result<String> {
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

        val response = RetrofitClient.asrApi.transcribe(request)
        val text = response.output?.choices?.firstOrNull()?.message?.content?.firstOrNull()?.text

        return if (text.isNullOrBlank()) {
            Result.failure(Exception("未能识别语音内容"))
        } else {
            Result.success(text.trim())
        }
    }

    /**
     * 使用 MemoCMT 跨模态模型分析情绪（同时利用文本和音频）
     */
    private suspend fun analyzeEmotion(audioFilePath: String, transcribedText: String): Emotion {
        return try {
            val emotionService = EmotionRecognitionService.getInstance(context)
            // 优先使用跨模态分析（文本+音频融合），准确率更高
            emotionService.analyzeCrossModal(transcribedText, audioFilePath)
        } catch (e: Throwable) {
            Log.e(TAG, "MemoCMT cross-modal analysis failed, trying speech-only", e)
            try {
                EmotionRecognitionService.getInstance(context).analyzeSpeechEmotion(audioFilePath)
            } catch (e2: Throwable) {
                Log.e(TAG, "Speech-only analysis also failed, using keyword guess", e2)
                guessEmotionFromText(transcribedText)
            }
        }
    }

    /**
     * 根据文本内容猜测情绪（备用方案）
     */
    private fun guessEmotionFromText(text: String): Emotion {
        val lowerText = text.lowercase()
        return when {
            // 负面情绪关键词
            lowerText.contains("疼") || lowerText.contains("痛") ||
            lowerText.contains("难受") || lowerText.contains("不舒服") ||
            lowerText.contains("唉") || lowerText.contains("哎") ||
            lowerText.contains("累") || lowerText.contains("烦") -> Emotion.SAD

            // 焦虑关键词
            lowerText.contains("担心") || lowerText.contains("害怕") ||
            lowerText.contains("紧张") || lowerText.contains("焦虑") ||
            lowerText.contains("着急") -> Emotion.ANXIOUS

            // 愤怒关键词
            lowerText.contains("生气") || lowerText.contains("气死") ||
            lowerText.contains("讨厌") || lowerText.contains("烦死") -> Emotion.ANGRY

            // 积极情绪关键词
            lowerText.contains("开心") || lowerText.contains("高兴") ||
            lowerText.contains("太好了") || lowerText.contains("真棒") ||
            lowerText.contains("哈哈") -> Emotion.HAPPY

            else -> Emotion.NEUTRAL
        }
    }
}
