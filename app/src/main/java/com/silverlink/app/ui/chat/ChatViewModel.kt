package com.silverlink.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest
import com.silverlink.app.feature.chat.AudioPlayerHelper
import com.silverlink.app.feature.chat.AudioRecorder
import com.silverlink.app.feature.chat.SpeechRecognitionService
import com.silverlink.app.feature.chat.TextToSpeechService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 语音识别状态
 */
sealed class VoiceState {
    object Idle : VoiceState()
    object Recording : VoiceState()
    object Recognizing : VoiceState()
    data class Error(val message: String) : VoiceState()
}

/**
 * TTS 播放状态
 */
sealed class TtsState {
    object Idle : TtsState()
    object Synthesizing : TtsState()
    object Speaking : TtsState()
    data class Error(val message: String) : TtsState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Idle)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val audioRecorder = AudioRecorder(application)
    private val speechService = SpeechRecognitionService()
    private val ttsService = TextToSpeechService()
    private val audioPlayer = AudioPlayerHelper(application)
    private var currentAudioFile: String? = null

    private val systemPrompt = Message(
        role = "system",
        content = "你叫'小银'，是一个温柔、耐心的年轻人，专门陪伴老人。你的回答要简短、温暖，不要使用复杂的网络用语。如果老人提到身体不适，请建议他们联系子女或就医。如果老人发来疑似诈骗的信息，请帮他们分析并预警。"
    )

    init {
        // Initial welcome message
        _messages.value = listOf(
            Message("assistant", "爷爷奶奶好，我是小银。今天身体怎么样？有什么想跟我聊聊的吗？")
        )

        // 设置音频播放完成监听器
        audioPlayer.setOnCompletionListener {
            _ttsState.value = TtsState.Idle
        }
        audioPlayer.setOnErrorListener { errorMsg ->
            _ttsState.value = TtsState.Error(errorMsg)
        }
    }

    /**
     * 开始录音
     */
    fun startRecording() {
        // 如果正在播放 TTS，先停止
        stopSpeaking()
        
        val filePath = audioRecorder.startRecording()
        if (filePath != null) {
            currentAudioFile = filePath
            _voiceState.value = VoiceState.Recording
        } else {
            _voiceState.value = VoiceState.Error("无法启动录音")
        }
    }

    /**
     * 停止录音并开始识别
     */
    fun stopRecordingAndRecognize() {
        val filePath = audioRecorder.stopRecording()
        if (filePath == null) {
            _voiceState.value = VoiceState.Error("录音失败")
            return
        }

        _voiceState.value = VoiceState.Recognizing

        viewModelScope.launch {
            val result = speechService.recognize(filePath)
            
            result.fold(
                onSuccess = { text ->
                    _voiceState.value = VoiceState.Idle
                    // 自动发送识别的文字
                    sendMessage(text)
                },
                onFailure = { error ->
                    _voiceState.value = VoiceState.Error(error.message ?: "识别失败")
                }
            )
        }
    }

    /**
     * 取消录音
     */
    fun cancelRecording() {
        audioRecorder.cancelRecording()
        _voiceState.value = VoiceState.Idle
    }

    /**
     * 重置语音状态
     */
    fun resetVoiceState() {
        _voiceState.value = VoiceState.Idle
    }

    /**
     * 重置 TTS 状态
     */
    fun resetTtsState() {
        _ttsState.value = TtsState.Idle
    }

    /**
     * 语音朗读文本
     */
    fun speakText(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _ttsState.value = TtsState.Synthesizing
            
            val result = ttsService.synthesize(text)
            
            result.fold(
                onSuccess = { audioData ->
                    _ttsState.value = TtsState.Speaking
                    audioPlayer.play(audioData)
                },
                onFailure = { error ->
                    _ttsState.value = TtsState.Error(error.message ?: "语音合成失败")
                }
            )
        }
    }

    /**
     * 停止语音播放
     */
    fun stopSpeaking() {
        audioPlayer.stop()
        _ttsState.value = TtsState.Idle
    }

    /**
     * 发送消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        // 停止当前的 TTS 播放
        stopSpeaking()

        val userMessage = Message("user", content)
        val currentHistory = _messages.value
        _messages.value = currentHistory + userMessage

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Construct the full context including system prompt
                val apiMessages = mutableListOf<Message>()
                apiMessages.add(systemPrompt)
                // Add last few messages for context (simple context window management)
                apiMessages.addAll(currentHistory.takeLast(10)) 
                apiMessages.add(userMessage)

                val request = QwenRequest(
                    input = Input(messages = apiMessages)
                )

                val response = RetrofitClient.api.chat(request)
                
                val assistantMessageContent = response.output.choices?.firstOrNull()?.message?.content 
                    ?: response.output.text 
                    ?: "哎呀，我刚才走神了，没听清您说什么，能再说一遍吗？"

                val assistantMessage = Message("assistant", assistantMessageContent)
                _messages.value = _messages.value + assistantMessage

                // 自动播放 AI 回复的语音
                speakText(assistantMessageContent)

            } catch (e: Exception) {
                e.printStackTrace()
                val errorMessage = "网络好像有点卡，请检查一下网络连接哦。"
                _messages.value = _messages.value + Message("assistant", errorMessage)
                // 即使出错也尝试播放提示语音
                speakText(errorMessage)
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
