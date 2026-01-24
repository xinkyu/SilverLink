package com.silverlink.app.feature.chat

import android.util.Log
import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 阿里云 CosyVoice 语音合成服务
 * 使用 WebSocket API 将文本转换为语音
 * 
 * 支持两种模式：
 * 1. 复刻音色模式（推荐）：使用用户录制的声音样本创建的复刻音色
 *    - 支持方言指令（请用四川话表达。）
 *    - 支持情感指令
 * 2. 系统音色模式（备用）：使用预置的系统音色
 *    - 部分系统音色支持情感指令
 * 
 * 支持的方言（复刻音色）：广东话、东北话、甘肃话、贵州话、河南话、湖北话、
 *                      江西话、闽南话、宁夏话、山西话、陕西话、山东话、
 *                      上海话、四川话、天津话、云南话
 */
class TextToSpeechService {

    companion object {
        private const val TAG = "TextToSpeechService"
        private const val WS_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"
        // 复刻音色使用 cosyvoice-v3-plus 模型（支持方言指令）
        private const val MODEL_CLONED = "cosyvoice-v3-plus"
        // 系统音色使用 cosyvoice-v2 模型
        private const val MODEL_SYSTEM = "cosyvoice-v2"
        // 默认系统音色（当没有复刻音色时使用）
        private const val DEFAULT_VOICE = "longanqin"
        private const val FORMAT = "mp3"
        private const val SAMPLE_RATE = 22050
    }
    
    // 复刻音色ID（由外部设置）
    private var clonedVoiceId: String = ""
    
    /**
     * 设置复刻音色ID
     * 复刻音色支持方言指令
     */
    fun setClonedVoiceId(voiceId: String) {
        clonedVoiceId = voiceId
        Log.d(TAG, "Cloned voice ID set: $voiceId")
    }
    
    /**
     * 获取当前使用的音色ID
     */
    fun getCurrentVoiceId(): String {
        return if (clonedVoiceId.isNotBlank()) clonedVoiceId else DEFAULT_VOICE
    }
    
    /**
     * 是否使用复刻音色
     */
    fun isUsingClonedVoice(): Boolean = clonedVoiceId.isNotBlank()
    
    /**
     * 获取当前应使用的模型
     * 复刻音色需要 cosyvoice-v3-plus，系统音色使用 cosyvoice-v2
     */
    private fun getCurrentModel(): String {
        return if (isUsingClonedVoice()) MODEL_CLONED else MODEL_SYSTEM
    }

    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 将文本合成为语音音频数据
     * @param text 待合成的文本
     * @param rate 语速（默认 1.0）
     * @param dialect 方言名称（如 "四川话"、"广东话"，空字符串表示普通话）
     * @param emotion 情感（用于设置语音情感，默认 NEUTRAL）
     * @return 音频数据（MP3 格式）
     */
    suspend fun synthesize(
        text: String,
        rate: Double = 1.0,
        dialect: String = "",
        emotion: Emotion = Emotion.NEUTRAL
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val instruction = buildInstruction(dialect, emotion)
            val voiceId = getCurrentVoiceId()
            Log.d(TAG, "Starting TTS synthesis: voice=$voiceId, rate=$rate, instruction=$instruction, text=${text.take(50)}...")
            val audioData = performTtsSynthesis(text, rate, instruction, voiceId)
            Log.d(TAG, "TTS synthesis completed, audio size: ${audioData.size} bytes")
            if (audioData.isEmpty()) {
                Result.failure(Exception("未收到音频数据"))
            } else {
                Result.success(audioData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS synthesis failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 构建 instruction 参数
     * 
     * 复刻音色支持：
     * - 方言指令：请用四川话表达。
     * - 情感指令：你说话的情感是happy。
     * 
     * 系统音色仅支持部分功能
     * 
     * 支持的方言：广东话、东北话、甘肃话、贵州话、河南话、湖北话、
     *            江西话、闽南话、宁夏话、山西话、陕西话、山东话、
     *            上海话、四川话、天津话、云南话
     */
    private fun buildInstruction(dialect: String, emotion: Emotion): String {
        Log.d(TAG, "buildInstruction called with dialect=$dialect, emotion=$emotion")
        
        val parts = mutableListOf<String>()
        
        // 方言指令（仅复刻音色支持）
        if (dialect.isNotBlank()) {
            if (isUsingClonedVoice()) {
                // 格式：“请用<方言>表达”
                parts.add("请用${dialect}表达")
            } else {
                Log.w(TAG, "方言指令需要复刻音色支持，当前使用系统音色，方言设置 '$dialect' 将被忽略")
            }
        }
        
        // TODO: 再次禁用情感指令，即便修复了标点符号，用户反馈仍然有问题。
        // 暂时只保留方言指令，确保功能可用。
//        val emotionValue = mapEmotionToValue(emotion)
//        if (emotionValue != "neutral") {
//            parts.add("你说话的情感是${emotionValue}")
//        }
       
        if (parts.isEmpty()) {
            return ""
        }
        
        // 使用逗号连接多个指令，并在末尾添加句号
        val instruction = parts.joinToString("，") + "。"
        Log.d(TAG, "Built instruction: $instruction")
        return instruction
    }
    
    /**
     * 将应用内的 Emotion 枚举映射到 CosyVoice 支持的情感值
     * 支持的情感值：neutral、fearful、angry、sad、surprised、happy、disgusted
     */
    private fun mapEmotionToValue(emotion: Emotion): String {
        return when (emotion) {
            Emotion.HAPPY -> "happy"
            Emotion.SAD -> "sad"
            Emotion.ANGRY -> "angry"
            Emotion.ANXIOUS -> "fearful"  // 焦虑映射为 fearful
            Emotion.NEUTRAL -> "neutral"
        }
    }

    private suspend fun performTtsSynthesis(
        inputText: String, 
        rate: Double,
        instruction: String,
        voiceId: String
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        val taskId = UUID.randomUUID().toString().replace("-", "") // API 建议 UUID，这里去掉横线试试，文档说可以带也可以不带
        val audioBuffer = ByteArrayOutputStream()
        val speechRate = rate

        Log.d(TAG, "Creating WebSocket connection, taskId: $taskId, voice: $voiceId, instruction: $instruction")

        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("Authorization", "Bearer ${RetrofitClient.getApiKey()}")
            .build()

        val webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened, sending run-task")
                // 发送 run-task 指令开启任务，包含待合成文本
                val runTaskMessage = createRunTaskMessage(taskId, inputText, speechRate, instruction, voiceId)

                Log.d(TAG, "Sending: $runTaskMessage")
                webSocket.send(runTaskMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received text message: $text")
                try {
                    val json = JSONObject(text)
                    val header = json.getJSONObject("header")
                    val event = header.optString("event", "")

                    when (event) {
                        "task-started" -> {
                            Log.d(TAG, "Task started, sending finish-task")
                            // 文本已在 run-task 中发送，此处直接发送 finish-task
                            val finishTaskMessage = createFinishTaskMessage(taskId)
                            webSocket.send(finishTaskMessage)
                        }
                        "task-finished" -> {
                            Log.d(TAG, "Task finished, audio buffer size: ${audioBuffer.size()}")
                            webSocket.close(1000, "Task completed")
                            if (continuation.isActive) {
                                continuation.resume(audioBuffer.toByteArray())
                            }
                        }
                        "task-failed" -> {
                            val errorMsg = header.optString("error_message", "TTS failed")
                            val errorCode = header.optString("error_code", "Unknown")
                            Log.e(TAG, "Task failed: code=$errorCode, message=$errorMsg")
                            webSocket.close(1000, "Task failed")
                            if (continuation.isActive) {
                                continuation.resumeWithException(Exception("$errorCode: $errorMsg"))
                            }
                        }
                        "result-generated" -> {
                            // 正常的中间结果，音频数据在 binary message 中
                            Log.d(TAG, "Result generated event received")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val size = bytes.size
                Log.d(TAG, "Received binary message: $size bytes")
                // 接收音频数据块
                audioBuffer.write(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                Log.e(TAG, "Response: ${response?.code} ${response?.message}")
                if (continuation.isActive) {
                    continuation.resumeWithException(t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
            }
        })

        continuation.invokeOnCancellation {
            Log.d(TAG, "Coroutine cancelled, closing WebSocket")
            webSocket.cancel()
        }
    }

    private fun createRunTaskMessage(taskId: String, text: String, rate: Double, instruction: String, voiceId: String): String {
        val model = getCurrentModel()
        return JSONObject().apply {
            put("header", JSONObject().apply {
                put("action", "run-task")
                put("task_id", taskId)
                put("streaming", "duplex")
            })
            put("payload", JSONObject().apply {
                put("task_group", "audio")
                put("task", "tts")
                put("function", "SpeechSynthesizer")
                put("model", model)
                put("parameters", JSONObject().apply {
                    put("text_type", "PlainText")
                    // 使用复刻音色ID或默认系统音色
                    put("voice", voiceId)
                    if (instruction.isNotBlank()) {
                        put("instruction", instruction)
                    }
                    put("format", FORMAT)
                    put("sample_rate", SAMPLE_RATE)
                    put("volume", 50)
                    put("rate", rate)
                    put("pitch", 1.0)
                    // 增加语言提示，确保方言包含在中文语境内
                    put("language_hints", org.json.JSONArray().apply {
                        put("zh")
                    })
                })
                put("input", JSONObject().apply {
                    put("text", text)
                })
            })
        }.toString()
    }

    private fun createContinueTaskMessage(taskId: String, text: String): String {
        return JSONObject().apply {
            put("header", JSONObject().apply {
                put("action", "continue-task")
                put("task_id", taskId)
                put("streaming", "duplex")
            })
            put("payload", JSONObject().apply {
                put("input", JSONObject().apply {
                    put("text", text)
                })
            })
        }.toString()
    }

    private fun createFinishTaskMessage(taskId: String): String {
        return JSONObject().apply {
            put("header", JSONObject().apply {
                put("action", "finish-task")
                put("task_id", taskId)
                put("streaming", "duplex") // 必须加这个
            })
            put("payload", JSONObject().apply {
                put("input", JSONObject())
            })
        }.toString()
    }
}
