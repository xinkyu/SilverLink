package com.silverlink.app.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.ChatMessageEntity
import com.silverlink.app.data.local.ConversationEntity
import com.silverlink.app.data.local.UserPreferences
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

private const val TAG = "ChatViewModel"

sealed class VoiceState {
    object Idle : VoiceState()
    object Recording : VoiceState()
    object Recognizing : VoiceState()
    data class Error(val message: String) : VoiceState()
}

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

    private val _currentEmotion = MutableStateFlow(Emotion.NEUTRAL)
    val currentEmotion: StateFlow<Emotion> = _currentEmotion.asStateFlow()

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Idle)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    // 会话管理
    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    // 防止重复初始化的标记
    private var isInitialized = false
    private var isCreatingConversation = false

    private val audioRecorder = AudioRecorder(application)
    private val speechService = SpeechRecognitionService()
    private val ttsService = TextToSpeechService()
    private val audioPlayer = AudioPlayerHelper(application)
    private var currentAudioFile: String? = null
    
    // 数据库访问
    private val chatDao = AppDatabase.getInstance(application).chatDao()
    private val historyDao = AppDatabase.getInstance(application).historyDao()
    private val userPrefs = UserPreferences.getInstance(application)
    private val syncRepository = com.silverlink.app.data.repository.SyncRepository.getInstance(application)

    private val baseSystemPrompt = """
        你叫'小银'，是一个温柔、耐心的年轻人，专门陪伴老人。
        你的回答要简短、温暖，不要使用复杂的网络用语。
        如果老人提到身体不适，请建议他们联系子女或就医。
        如果老人发来疑似诈骗的信息，请帮他们分析并预警。
    """.trimIndent()

    private fun getElderName(): String = userPrefs.userConfig.value.elderName.trim()
    private fun getElderProfile(): String = userPrefs.userConfig.value.elderProfile.trim()

    private fun buildGreeting(): String {
        val elderName = getElderName()
        return if (elderName.isNotBlank()) {
            "${elderName}好，我是小银。今天身体怎么样？有什么想跟我聊聊的吗？"
        } else {
            "您好，我是小银。今天身体怎么样？有什么想跟我聊聊的吗？"
        }
    }

    init {
        // 加载会话列表
        loadConversations()

        // 设置音频播放完成监听器
        audioPlayer.setOnCompletionListener {
            _ttsState.value = TtsState.Idle
        }
        audioPlayer.setOnErrorListener { errorMsg ->
            _ttsState.value = TtsState.Error(errorMsg)
        }
    }

    private fun loadConversations() {
        if (isInitialized) return
        isInitialized = true
        
        viewModelScope.launch {
            val list = chatDao.getConversationList()
            _conversations.value = list
            
            if (list.isNotEmpty()) {
                // 默认选中最近的一个
                switchConversation(list.first().id)
            } else {
                // 如果没有任何会话，创建一个新的
                createNewConversation()
            }
        }
    }
    
    /**
     * 刷新会话列表（不触发自动切换）
     */
    private suspend fun refreshConversationList() {
        val list = chatDao.getConversationList()
        _conversations.value = list
    }

    fun createNewConversation() {
        if (isCreatingConversation) return
        isCreatingConversation = true
        
        viewModelScope.launch {
            try {
                val newConversation = ConversationEntity()
                val id = chatDao.insertConversation(newConversation)
                
                // 刷新会话列表
                refreshConversationList()
                
                // 切换到新会话
                _currentConversationId.value = id
                
                // 显示欢迎语（不保存到数据库，等用户发送第一条消息时再保存）
                val welcomeMessage = Message("assistant", buildGreeting())
                _messages.value = listOf(welcomeMessage)
                saveMessageToDb(welcomeMessage, null)
            } finally {
                isCreatingConversation = false
            }
        }
    }

    fun switchConversation(conversationId: Long) {
        if (_currentConversationId.value == conversationId) return
        
        _currentConversationId.value = conversationId
        _messages.value = emptyList() // 清空当前显示，避免闪烁
        loadChatHistory(conversationId)
    }
    
    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            val isCurrentConversation = _currentConversationId.value == conversationId
            
            chatDao.deleteConversation(conversationId)
            
            // 刷新会话列表
            refreshConversationList()
            
            // 如果删除的是当前会话，需要切换
            if (isCurrentConversation) {
                val remainingList = _conversations.value
                if (remainingList.isNotEmpty()) {
                    // 切换到第一个会话
                    _currentConversationId.value = null // 先清空，确保 switchConversation 能执行
                    switchConversation(remainingList.first().id)
                } else {
                    // 没有会话了，创建新的
                    _currentConversationId.value = null
                    createNewConversation()
                }
            }
        }
    }

    private fun loadChatHistory(conversationId: Long) {
        viewModelScope.launch {
            // 使用一次性查询，避免 Flow 持续监听导致循环
            val entities = chatDao.getMessageList(conversationId)
            if (entities.isNotEmpty()) {
                _messages.value = entities.map { Message(it.role, it.content) }
            } else {
                // 新会话，显示欢迎语并保存
                val welcomeMessage = Message("assistant", buildGreeting())
                _messages.value = listOf(welcomeMessage)
                saveMessageToDb(welcomeMessage, null)
            }
        }
    }

    private suspend fun saveMessageToDb(message: Message, emotion: Emotion?) {
        val conversationId = _currentConversationId.value ?: return
        try {
            val entity = ChatMessageEntity(
                conversationId = conversationId,
                role = message.role,
                content = message.content,
                emotion = emotion?.name
            )
            chatDao.insertMessage(entity)
            
            // 如果是新会话的第一条用户消息，更新标题
            val conversation = chatDao.getConversation(conversationId)
            if (conversation != null && conversation.title == "新对话" && message.role == "user") {
                val newTitle = if (message.content.length > 10) message.content.take(10) + "..." else message.content
                chatDao.updateConversation(conversation.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
            } else if (conversation != null) {
                // 仅更新时间
                chatDao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message", e)
        }
    }

    /**
     * 根据当前情绪生成动态 System Prompt
     */
    private fun buildSystemPrompt(): Message {
        val emotionHint = _currentEmotion.value.promptHint
        val elderName = getElderName()
        val elderProfile = getElderProfile()
        val nameHint = if (elderName.isNotBlank()) {
            "用户称呼为“$elderName”，回复时请优先使用该称呼，避免使用“爷爷奶奶”等泛称。"
        } else {
            "请避免使用“爷爷奶奶”等泛称，改用“您”进行称呼。"
        }
        val profileHint = if (elderProfile.isNotBlank()) {
            "【长辈信息】$elderProfile。请据此进行更有针对性的回应。"
        } else {
            ""
        }
        val fullPrompt = if (emotionHint.isNotBlank()) {
            "$baseSystemPrompt\n\n【称呼提示】$nameHint\n${profileHint.ifBlank { "" }}${if (profileHint.isNotBlank()) "\n" else ""}\n【用户情绪提示】$emotionHint"
        } else {
            "$baseSystemPrompt\n\n【称呼提示】$nameHint${if (profileHint.isNotBlank()) "\n$profileHint" else ""}"
        }
        return Message(role = "system", content = fullPrompt)
    }

    /**
     * 根据情绪获取 TTS 语速
     */
    private fun getTtsRateForEmotion(emotion: Emotion): Double {
        return when (emotion) {
            Emotion.SAD -> 0.75
            Emotion.ANXIOUS -> 0.8
            Emotion.ANGRY -> 0.8
            Emotion.HAPPY -> 1.1
            Emotion.NEUTRAL -> 1.0
        }
    }

    /**
     * 根据文字输入检测情绪 (使用 AI 分析)
     * 同时保存到本地历史和上传到云端
     */
    private fun detectEmotionFromText(text: String) {
        viewModelScope.launch {
            try {
                val emotion = analyzeTextEmotionWithAI(text)
                _currentEmotion.value = emotion
                Log.d(TAG, "AI emotion detected: ${emotion.name} from: $text")
                
                // 保存情绪记录到本地
                saveMoodLog(emotion, text)
            } catch (e: Exception) {
                Log.e(TAG, "AI emotion analysis failed, using fallback", e)
                // 失败时使用关键词匹配作为备用
                val fallbackEmotion = guessEmotionFromKeywords(text)
                _currentEmotion.value = fallbackEmotion
                saveMoodLog(fallbackEmotion, text)
            }
        }
    }
    
    /**
     * 保存情绪记录到本地并同步到云端
     */
    private suspend fun saveMoodLog(emotion: Emotion, triggerText: String) {
        try {
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            
            Log.d(TAG, "Saving mood log: emotion=${emotion.name}, date=$currentDate, text=${triggerText.take(50)}")
            
            // 保存到本地历史记录
            val moodLogEntity = com.silverlink.app.data.local.entity.MoodLogEntity(
                mood = emotion.name,
                note = triggerText.take(100), // 截取前100个字符作为摘要
                date = currentDate
            )
            val insertedId = historyDao.insertMoodLog(moodLogEntity)
            Log.d(TAG, "Mood log saved to local database with id: $insertedId")
            
            // 同步到云端（静默失败）
            syncRepository.syncMoodLog(
                mood = emotion.name,
                note = triggerText.take(100),
                conversationSummary = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save mood log", e)
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
            // 保存用户消息到数据库
            saveMessageToDb(userMessage, _currentEmotion.value)

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

                // 保存 AI 回复到数据库
                saveMessageToDb(assistantMessage, null)

                // 自动播放 AI 回复的语音（根据情绪调整语速）
                speakText(assistantMessageContent)

            } catch (e: Exception) {
                e.printStackTrace()
                val errorMessage = "网络好像有点卡，请检查一下网络连接哦。"
                val errorMsg = Message("assistant", errorMessage)
                _messages.value = _messages.value + errorMsg
                saveMessageToDb(errorMsg, null)
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
