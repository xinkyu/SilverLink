package com.silverlink.app.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.model.Emotion
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

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Idle)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    // 当前用户情绪状态
    private val _currentEmotion = MutableStateFlow(Emotion.NEUTRAL)
    val currentEmotion: StateFlow<Emotion> = _currentEmotion.asStateFlow()

    private val audioRecorder = AudioRecorder(application)
    private val speechService = SpeechRecognitionService()
    private val ttsService = TextToSpeechService()
    private val audioPlayer = AudioPlayerHelper(application)
    private var currentAudioFile: String? = null

    // 基础 System Prompt
    private val baseSystemPrompt = """
        你叫'小银'，是一个温柔、耐心的年轻人，专门陪伴老人。
        你的回答要简短、温暖，不要使用复杂的网络用语。
        如果老人提到身体不适，请建议他们联系子女或就医。
        如果老人发来疑似诈骗的信息，请帮他们分析并预警。
    """.trimIndent()

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
     * 根据当前情绪生成动态 System Prompt
     */
    private fun buildSystemPrompt(): Message {
        val emotionHint = _currentEmotion.value.promptHint
        val fullPrompt = if (emotionHint.isNotBlank()) {
            "$baseSystemPrompt\n\n【用户情绪提示】$emotionHint"
        } else {
            baseSystemPrompt
        }
        return Message(role = "system", content = fullPrompt)
    }

    /**
     * 根据情绪获取 TTS 语速 (差异加大以便感知)
     */
    private fun getTtsRateForEmotion(emotion: Emotion): Double {
        return when (emotion) {
            Emotion.SAD -> 0.75       // 明显放慢，更温柔
            Emotion.ANXIOUS -> 0.8    // 放慢，更平静
            Emotion.ANGRY -> 0.8      // 放慢，更耐心
            Emotion.HAPPY -> 1.1      // 稍快，更活泼
            Emotion.NEUTRAL -> 1.0    // 正常语速
        }
    }

    /**
     * 根据文字输入检测情绪 (使用 AI 分析)
     */
    private fun detectEmotionFromText(text: String) {
        viewModelScope.launch {
            try {
                val emotion = analyzeTextEmotionWithAI(text)
                _currentEmotion.value = emotion
                Log.d(TAG, "AI emotion detected: ${emotion.name} from: $text")
            } catch (e: Exception) {
                Log.e(TAG, "AI emotion analysis failed, using fallback", e)
                // 失败时使用关键词匹配作为备用
                _currentEmotion.value = guessEmotionFromKeywords(text)
            }
        }
    }

    /**
     * 使用 Qwen AI 分析文字情绪
     */
    private suspend fun analyzeTextEmotionWithAI(text: String): Emotion {
        val request = QwenRequest(
            input = Input(
                messages = listOf(
                    Message("system", "你是一个情绪分析助手。分析用户输入的情绪，只回答以下情绪之一：HAPPY, SAD, ANGRY, ANXIOUS, NEUTRAL。不要解释，只输出一个情绪词。"),
                    Message("user", text)
                )
            )
        )
        
        val response = RetrofitClient.api.chat(request)
        val emotionLabel = response.output.choices?.firstOrNull()?.message?.content 
            ?: response.output.text 
            ?: "NEUTRAL"
        
        return Emotion.fromLabel(emotionLabel.trim())
    }

    /**
     * 关键词匹配检测情绪（备用方案）
     */
    private fun guessEmotionFromKeywords(text: String): Emotion {
        return when {
            text.contains("难过") || text.contains("伤心") || 
            text.contains("疼") || text.contains("痛") ||
            text.contains("累") || text.contains("郁闷") -> Emotion.SAD
            
            text.contains("担心") || text.contains("害怕") ||
            text.contains("紧张") || text.contains("焦虑") -> Emotion.ANXIOUS
            
            text.contains("生气") || text.contains("讨厌") ||
            text.contains("烦") || text.contains("愤怒") -> Emotion.ANGRY
            
            text.contains("开心") || text.contains("高兴") ||
            text.contains("快乐") || text.contains("幸福") -> Emotion.HAPPY
            
            else -> Emotion.NEUTRAL
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
                onSuccess = { speechResult ->
                    _voiceState.value = VoiceState.Idle
                    
                    // 更新情绪状态
                    _currentEmotion.value = speechResult.emotion
                    Log.d(TAG, "Detected emotion: ${speechResult.emotion.displayName}")
                    
                    // 自动发送识别的文字
                    sendMessage(speechResult.text)
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
    fun speakText(text: String, emotion: Emotion = _currentEmotion.value) {
        if (text.isBlank()) return

        val ttsRate = getTtsRateForEmotion(emotion)
        Log.d(TAG, "Speaking with rate=$ttsRate for emotion=${emotion.name}")

        viewModelScope.launch {
            _ttsState.value = TtsState.Synthesizing
            
            val result = ttsService.synthesize(text, ttsRate)
            
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

        // 对文字输入也进行情绪检测
        detectEmotionFromText(content)

        val userMessage = Message("user", content)
        val currentHistory = _messages.value
        _messages.value = currentHistory + userMessage

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 使用动态 System Prompt（根据情绪调整）
                val apiMessages = mutableListOf<Message>()
                apiMessages.add(buildSystemPrompt())
                // Add last few messages for context
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

                // 自动播放 AI 回复的语音（根据情绪调整语速）
                speakText(assistantMessageContent)

            } catch (e: Exception) {
                e.printStackTrace()
                val errorMessage = "网络好像有点卡，请检查一下网络连接哦。"
                _messages.value = _messages.value + Message("assistant", errorMessage)
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
