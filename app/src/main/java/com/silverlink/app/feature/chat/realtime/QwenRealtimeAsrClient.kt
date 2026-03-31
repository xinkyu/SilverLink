package com.silverlink.app.feature.chat.realtime

import android.util.Base64
import android.util.Log
import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.model.SpeechResult
import com.silverlink.app.data.remote.RetrofitClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class QwenRealtimeAsrClient(
    private val onPartial: (String) -> Unit,
    private val onFinal: (SpeechResult) -> Unit,
    private val language: String = "zh",
    private val sampleRate: Int = 16000,
    private val model: String = "qwen3-asr-flash-realtime"
) : StreamingAsrClientApi {
    companion object {
        private const val TAG = "QwenRealtimeAsr"
        private const val WS_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isReady = AtomicBoolean(false)
    private val isStarted = AtomicBoolean(false)
    private val isFinishing = AtomicBoolean(false)

    override fun start() {
        if (isStarted.getAndSet(true)) return
        connect()
    }

    override fun pushFrame(frame: ShortArray, isSpeech: Boolean) {
        if (!isReady.get()) return
        if (!isSpeech) return
        val bytes = shortArrayToLittleEndian(frame)
        val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("event_id", "event_${System.currentTimeMillis()}")
            put("type", "input_audio_buffer.append")
            put("audio", base64Audio)
        }
        webSocket?.send(payload.toString())
    }

    override fun finish() {
        if (!isReady.get() || isFinishing.getAndSet(true)) return
        val commitEvent = JSONObject().apply {
            put("event_id", "event_commit_${System.currentTimeMillis()}")
            put("type", "input_audio_buffer.commit")
        }
        val finishEvent = JSONObject().apply {
            put("event_id", "event_finish_${System.currentTimeMillis()}")
            put("type", "session.finish")
        }
        webSocket?.send(commitEvent.toString())
        webSocket?.send(finishEvent.toString())
    }

    override fun stop() {
        isFinishing.set(false)
        isReady.set(false)
        isStarted.set(false)
        webSocket?.close(1000, "client stop")
        webSocket = null
    }

    private fun connect() {
        val request = Request.Builder()
            .url("$WS_URL?model=$model")
            .addHeader("Authorization", "Bearer ${RetrofitClient.getApiKey()}")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                webSocket.send(buildSessionUpdate())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                isReady.set(false)
                isStarted.set(false)
                isFinishing.set(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                isReady.set(false)
                isStarted.set(false)
                isFinishing.set(false)
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            when (type) {
                "session.created" -> {
                    isReady.set(true)
                }
                "conversation.item.input_audio_transcription.text" -> {
                    val partial = json.optString("text")
                    if (partial.isNotBlank()) {
                        onPartial(partial)
                    }
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = json.optString("transcript")
                    onFinal(SpeechResult(text = transcript, emotion = Emotion.NEUTRAL))
                }
                "session.finished" -> {
                    stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    private fun buildSessionUpdate(): String {
        val session = JSONObject().apply {
            put("modalities", org.json.JSONArray().put("text"))
            put("input_audio_format", "pcm")
            put("sample_rate", sampleRate)
            put("input_audio_transcription", JSONObject().apply {
                put("language", language)
            })
            put("turn_detection", JSONObject.NULL)
        }

        return JSONObject().apply {
            put("event_id", "event_${System.currentTimeMillis()}")
            put("type", "session.update")
            put("session", session)
        }.toString()
    }

    private fun shortArrayToLittleEndian(data: ShortArray): ByteArray {
        val output = ByteArray(data.size * 2)
        var index = 0
        data.forEach { sample ->
            output[index++] = (sample.toInt() and 0xFF).toByte()
            output[index++] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return output
    }
}
