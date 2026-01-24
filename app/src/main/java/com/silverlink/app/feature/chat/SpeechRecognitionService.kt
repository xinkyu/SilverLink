package com.silverlink.app.feature.chat

import android.util.Base64
import android.util.Log
import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.model.SpeechResult
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.AsrContentItem
import com.silverlink.app.data.remote.model.AsrInput
import com.silverlink.app.data.remote.model.AsrMessage
import com.silverlink.app.data.remote.model.QwenAsrRequest
import java.io.File

/**
 * 语音识别服务 - 调用 Qwen-Audio API 进行语音转文字和情感分析
 */
class SpeechRecognitionService {

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

            // 步骤1: 语音转文字
            val textResult = transcribeAudio(base64Audio)
            if (textResult.isFailure) {
                return Result.failure(textResult.exceptionOrNull() ?: Exception("语音识别失败"))
            }
            val text = textResult.getOrThrow()
            
            // 步骤2: 分析情绪
            val emotion = analyzeEmotion(base64Audio, text)
            
            Log.d(TAG, "Recognition result: text='$text', emotion=${emotion.name}")
            Result.success(SpeechResult(text = text, emotion = emotion))
            
        } catch (e: Exception) {
            Log.e(TAG, "Speech recognition failed", e)
            Result.failure(Exception("语音识别失败: ${e.message}"))
        }
    }

    suspend fun recognizePcm(audioBytes: ByteArray): Result<SpeechResult> {
        return try {
            if (audioBytes.isEmpty()) {
                return Result.failure(Exception("音频数据为空"))
            }
            val wavBytes = pcmToWav(audioBytes)
            val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            val textResult = transcribeAudio(base64Audio, "wav")
            if (textResult.isFailure) {
                return Result.failure(textResult.exceptionOrNull() ?: Exception("语音识别失败"))
            }
            val text = textResult.getOrThrow()
            val emotion = analyzeEmotion(base64Audio, text, "wav")
            Result.success(SpeechResult(text = text, emotion = emotion))
        } catch (e: Exception) {
            Result.failure(Exception("语音识别失败: ${e.message}"))
        }
    }

    private fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val totalLen = pcmData.size + 44
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt(header, 4, totalDataLen)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt(header, 16, 16)
        writeShort(header, 20, 1)
        writeShort(header, 22, channels.toShort())
        writeInt(header, 24, sampleRate)
        writeInt(header, 28, byteRate)
        writeShort(header, 32, (channels * bitsPerSample / 8).toShort())
        writeShort(header, 34, bitsPerSample.toShort())
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt(header, 40, pcmData.size)

        return header + pcmData
    }

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Short) {
        data[offset] = (value.toInt() and 0xFF).toByte()
        data[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    /**
     * 语音转文字
     */
    private suspend fun transcribeAudio(base64Audio: String, format: String = "m4a"): Result<String> {
        val request = QwenAsrRequest(
            model = "qwen2-audio-instruct",
            input = AsrInput(
                messages = listOf(
                    AsrMessage(
                        role = "user",
                        content = listOf(
                            AsrContentItem.audio(base64Audio, format),
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
     * 分析语音情绪
     * 使用 Qwen-Audio 分析语音中的情感
     */
    private suspend fun analyzeEmotion(base64Audio: String, transcribedText: String, format: String = "m4a"): Emotion {
        return try {
            val request = QwenAsrRequest(
                model = "qwen2-audio-instruct",
                input = AsrInput(
                    messages = listOf(
                        AsrMessage(
                            role = "user",
                            content = listOf(
                                AsrContentItem.audio(base64Audio, format),
                                AsrContentItem.text("""
                                    请分析这段语音中说话人的情绪状态。
                                    只回答以下情绪之一: HAPPY, SAD, ANGRY, ANXIOUS, NEUTRAL
                                    不要解释，只输出一个情绪词。
                                """.trimIndent())
                            )
                        )
                    )
                )
            )

            val response = RetrofitClient.asrApi.transcribe(request)
            val emotionLabel = response.output?.choices?.firstOrNull()?.message?.content?.firstOrNull()?.text
            
            if (!emotionLabel.isNullOrBlank()) {
                Log.d(TAG, "Emotion analysis result: $emotionLabel")
                Emotion.fromLabel(emotionLabel)
            } else {
                // 无法分析时，根据文本内容做简单判断
                guessEmotionFromText(transcribedText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emotion analysis failed, using text-based guess", e)
            // 情感分析失败时，根据文本内容做简单判断
            guessEmotionFromText(transcribedText)
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
