package com.silverlink.app.feature.chat

import android.util.Log
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
 */
class TextToSpeechService {

    companion object {
        private const val TAG = "TextToSpeechService"
        private const val WS_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"
        private const val MODEL = "cosyvoice-v1"
        // 龙湾 - 温柔女声，适合陪伴老人的场景 (cosyvoice-v1 支持)
        private const val VOICE = "longwan"
        private const val FORMAT = "mp3"
        private const val SAMPLE_RATE = 22050
    }

    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 将文本合成为语音音频数据
     * @param text 待合成的文本
     * @param rate 语速 (0.5-2.0, 默认1.0)
     * @return 音频数据（MP3 格式）
     */
    suspend fun synthesize(text: String, rate: Double = 1.0): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting TTS synthesis for text: ${text.take(50)}... rate=$rate")
            val audioData = performTtsSynthesis(text, rate)
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

    private suspend fun performTtsSynthesis(inputText: String, rate: Double): ByteArray = suspendCancellableCoroutine { continuation ->
        val taskId = UUID.randomUUID().toString()
        val audioBuffer = ByteArrayOutputStream()
        val speechRate = rate  // 保存到局部变量供回调使用

        Log.d(TAG, "Creating WebSocket connection, taskId: $taskId")

        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("Authorization", "Bearer ${RetrofitClient.getApiKey()}")
            .build()

        val webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened, sending run-task")
                // 发送 run-task 指令开启任务，包含待合成文本
                val runTaskMessage = createRunTaskMessage(taskId, inputText, speechRate)

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
                            // 发送 finish-task 指令通知已完成文本发送
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
                            val payload = json.optJSONObject("payload")
                            val errorMsg = payload?.optString("message", "TTS failed") ?: "TTS failed"
                            val errorCode = payload?.optString("code", "") ?: ""
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

    private fun createRunTaskMessage(taskId: String, text: String, rate: Double): String {
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
                put("model", MODEL)
                put("parameters", JSONObject().apply {
                    put("text_type", "PlainText")
                    put("voice", VOICE)
                    put("format", FORMAT)
                    put("sample_rate", SAMPLE_RATE)
                    put("volume", 50)
                    put("rate", rate)  // 动态语速
                    put("pitch", 1.0)
                })
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
            })
            put("payload", JSONObject().apply {
                put("input", JSONObject())
            })
        }.toString()
    }
}
