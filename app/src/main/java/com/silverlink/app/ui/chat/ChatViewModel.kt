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
import com.silverlink.app.feature.chat.realtime.ConversationState
import com.silverlink.app.feature.chat.realtime.PcmAudioStream
import com.silverlink.app.feature.chat.realtime.RealtimeConversationManager
import com.silverlink.app.feature.chat.realtime.QwenRealtimeAsrClient
import com.silverlink.app.feature.chat.realtime.StreamingAsrClientApi
import com.silverlink.app.feature.chat.realtime.StreamingTtsControllerImpl
import com.silverlink.app.feature.chat.realtime.WebRtcVadEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    // 语音命令意图状态 - 用于触发导航和执行操作
    enum class SafetyFeature { FALL_DETECTION, PROACTIVE_INTERACTION, LOCATION_SHARING }
    
    sealed class VoiceCommandIntent {
        object None : VoiceCommandIntent()
        // 导航类
        object OpenGallery : VoiceCommandIntent()
        data class SearchPhotos(val query: String) : VoiceCommandIntent()
        object OpenMedicationAdd : VoiceCommandIntent()      // 拍照加药
        object OpenMedicationFind : VoiceCommandIntent()     // 视觉识别找药
        object OpenMemoryQuiz : VoiceCommandIntent()         // 记忆小游戏
        data class OpenMoodAnalysis(val period: String) : VoiceCommandIntent()  // 情绪分析(周/月/年)
        object OpenSafetySettings : VoiceCommandIntent()     // 安全守护页面
        object OpenContacts : VoiceCommandIntent()           // 紧急联系人页面
        // 设置调整类 (直接执行)
        data class AdjustFontSize(val increase: Boolean) : VoiceCommandIntent()
        data class ToggleSafetyFeature(val feature: SafetyFeature, val enable: Boolean) : VoiceCommandIntent()
        // 添加联系人
        data class AddContact(val name: String, val relationship: String, val phone: String) : VoiceCommandIntent()
    }
    
    // 兼容旧 API
    @Deprecated("Use VoiceCommandIntent instead", ReplaceWith("VoiceCommandIntent"))
    sealed class PhotoIntent {
        object None : PhotoIntent()
        object OpenGallery : PhotoIntent()
        data class SearchPhotos(val query: String) : PhotoIntent()
    }
    
    private val _voiceCommandIntent = MutableStateFlow<VoiceCommandIntent>(VoiceCommandIntent.None)
    val voiceCommandIntent: StateFlow<VoiceCommandIntent> = _voiceCommandIntent.asStateFlow()
    
    // 兼容旧 API: photoIntent 映射到 voiceCommandIntent
    val photoIntent: StateFlow<VoiceCommandIntent> = _voiceCommandIntent

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
    private var realtimeManager: RealtimeConversationManager? = null
    private var streamingTts: StreamingTtsControllerImpl? = null
    private var realtimeAsr: StreamingAsrClientApi? = null
    
    // 数据库访问
    private val chatDao = AppDatabase.getInstance(application).chatDao()
    private val historyDao = AppDatabase.getInstance(application).historyDao()
    private val userPrefs = UserPreferences.getInstance(application)
    private val syncRepository = com.silverlink.app.data.repository.SyncRepository.getInstance(application)

    private fun getElderName(): String = userPrefs.userConfig.value.elderName.trim()
    private fun getElderProfile(): String = userPrefs.userConfig.value.elderProfile.trim()
    private fun getAssistantName(): String = userPrefs.userConfig.value.assistantName.trim().ifBlank { "小银" }

    private fun getBaseSystemPrompt(): String {
        val assistantName = getAssistantName()
        return """
        你叫'$assistantName'，是一个温柔、耐心的年轻人，专门陪伴老人。
        你的回答要简短、温暖，不要使用复杂的网络用语。
        如果老人提到身体不适，请建议他们联系子女或就医。
        如果老人发来疑似诈骗的信息，请帮他们分析并预警。
        """.trimIndent()
    }

    private fun buildGreeting(): String {
        val elderName = getElderName()
        val assistantName = getAssistantName()
        return if (elderName.isNotBlank()) {
            "${elderName}好，我是${assistantName}。今天身体怎么样？有什么想跟我聊聊的吗？"
        } else {
            "您好，我是${assistantName}。今天身体怎么样？有什么想跟我聊聊的吗？"
        }
    }

    init {
        // 加载会话列表
        loadConversations()

        // 设置复刻音色ID（如果有）
        val clonedVoiceId = userPrefs.userConfig.value.clonedVoiceId
        Log.d(TAG, "Loaded user config - clonedVoiceId: '$clonedVoiceId', dialect: ${userPrefs.userConfig.value.dialect}")
        if (clonedVoiceId.isNotBlank()) {
            ttsService.setClonedVoiceId(clonedVoiceId)
            Log.d(TAG, "Using cloned voice: $clonedVoiceId")
        } else {
            Log.d(TAG, "No cloned voice set, will use default system voice")
        }

        audioPlayer.setOnCompletionListener {
            _ttsState.value = TtsState.Idle
            realtimeManager?.onPlaybackFinished()
        }
        audioPlayer.setOnErrorListener { errorMsg ->
            _ttsState.value = TtsState.Error(errorMsg)
            realtimeManager?.onPlaybackFinished()
        }

        setupRealtimeConversation()
    }

    private fun setupRealtimeConversation() {
        val vadEngine = WebRtcVadEngine()
        val audioStream = PcmAudioStream()
        val asrClient = QwenRealtimeAsrClient(
            onPartial = { partial ->
                _partialTranscript.value = partial
            },
            onFinal = { speech ->
                handleRealtimeFinal(speech.text, speech.emotion)
            }
        )
        realtimeAsr = asrClient

        streamingTts = StreamingTtsControllerImpl(
            ttsService = ttsService,
            audioPlayer = audioPlayer,
            scope = viewModelScope,
            onPlaybackState = { isPlaying ->
                _ttsState.value = if (isPlaying) TtsState.Speaking else TtsState.Idle
                if (isPlaying) {
                    realtimeManager?.onPlaybackStarted()
                } else {
                    realtimeManager?.onPlaybackFinished()
                }
            },
            emotionProvider = { _currentEmotion.value },
            dialectProvider = {
                val dialect = userPrefs.userConfig.value.dialect
                if (dialect != com.silverlink.app.data.local.Dialect.NONE) dialect.displayName else ""
            },
            rateProvider = { getTtsRateForEmotion(_currentEmotion.value) }
        )
        realtimeManager = RealtimeConversationManager(
            vadEngine = vadEngine,
            audioStream = audioStream,
            asrClient = asrClient,
            silenceTimeoutMs = 450,
            bargeInRmsThreshold = 4000,
            consecutiveFramesRequired = 3,
            playbackCooldownMs = 500,
            onInterrupt = {
                stopSpeaking()
            },
            onFinalText = { text, emotion ->
                handleRealtimeFinal(text, emotion)
            }
        )

        viewModelScope.launch {
            realtimeManager?.state?.collect { state ->
                _conversationState.value = state
            }
        }
    }

    fun startRealtimeConversation() {
        realtimeManager?.start()
    }

    fun stopRealtimeConversation() {
        realtimeManager?.stop()
        _conversationState.value = ConversationState.Idle
        _partialTranscript.value = ""
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

        val dialect = userPrefs.userConfig.value.dialect
        val hasMajorDisease = userPrefs.userConfig.value.hasMajorDisease
        val majorDiseaseDetails = userPrefs.userConfig.value.majorDiseaseDetails
        
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
        
        val healthHint = if (hasMajorDisease && majorDiseaseDetails.isNotBlank()) {
            "【健康状况重要提示】长辈患有：$majorDiseaseDetails。请在对话中格外注意，若长辈提到身体不适或相关症状，请立即建议就医或联系家人。请给予更多的关怀和耐心。"
        } else {
            ""
        }
        
        val dialectHint = if (dialect != com.silverlink.app.data.local.Dialect.NONE) {
            "【用户方言/地区】用户来自${dialect.displayName}地区。${dialect.promptHint}"
        } else {
            ""
        }

        val fullPrompt = buildString {
            append(getBaseSystemPrompt())
            append("\n\n【称呼提示】$nameHint")
            if (profileHint.isNotBlank()) append("\n$profileHint")
            if (healthHint.isNotBlank()) append("\n$healthHint")
            if (dialectHint.isNotBlank()) append("\n$dialectHint")
            if (emotionHint.isNotBlank()) append("\n【用户情绪提示】$emotionHint")
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
    
    // ==================== 照片意图识别 ====================
    
    /**
     * 检测照片相关意图
     * 返回 true 表示检测到照片意图，已处理，不需要继续发送到 AI
     */
    private fun detectVoiceCommand(text: String): Boolean {
        val elderName = getElderName()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        
        // ========== 导航类命令 ==========
        
        // 记忆相册
        val galleryKeywords = listOf("看照片", "看看照片", "老照片", "翻相册", "记忆相册", "看相册", "打开相册")
        if (galleryKeywords.any { text.contains(it) }) {
            _voiceCommandIntent.value = VoiceCommandIntent.OpenGallery
            respondAndSpeak("${prefix}好的，这就打开记忆相册给您看。")
            return true
        }
        
        // 搜索照片
        val searchKeywords = listOf("找照片", "搜照片", "有没有", "那年", "那次", "去哪里", "谁带我")
        if (searchKeywords.any { text.contains(it) }) {
            _voiceCommandIntent.value = VoiceCommandIntent.SearchPhotos(text)
            respondAndSpeak("${prefix}我帮您找找相关的照片。")
            return true
        }
        
        // 拍照加药
        val addMedicationKeywords = listOf("拍照加药", "帮我加药", "添加药品", "录入药品", "打开加药")
        if (addMedicationKeywords.any { text.contains(it) }) {
            _voiceCommandIntent.value = VoiceCommandIntent.OpenMedicationAdd
            respondAndSpeak("${prefix}好的，这就打开拍照加药功能。")
            return true
        }
        
        // 视觉识别找药
        val findMedicationKeywords = listOf("帮我找药", "看看这个药", "这个药对不对", "识别药品", "认一下药", "这是什么药")
        if (findMedicationKeywords.any { text.contains(it) }) {
            _voiceCommandIntent.value = VoiceCommandIntent.OpenMedicationFind
            respondAndSpeak("${prefix}好的，我来帮您看看这个药。")
            return true
        }
        
        // 记忆小游戏
        val memoryQuizKeywords = listOf("记忆小游戏", "玩游戏", "记忆游戏", "做题", "玩玩游戏", "玩一玩")
        if (memoryQuizKeywords.any { text.contains(it) }) {
            _voiceCommandIntent.value = VoiceCommandIntent.OpenMemoryQuiz
            respondAndSpeak("${prefix}好的，我们来玩记忆小游戏吧！")
            return true
        }
        
        // 情绪分析
        val moodAnalysisKeywords = listOf("分析", "情绪", "心情")
        if (moodAnalysisKeywords.any { text.contains(it) }) {
            val period = when {
                text.contains("这周") || text.contains("本周") || text.contains("一周") -> "week"
                text.contains("这月") || text.contains("本月") || text.contains("一个月") -> "month"
                text.contains("这年") || text.contains("今年") || text.contains("一年") -> "year"
                else -> "week"
            }
            _voiceCommandIntent.value = VoiceCommandIntent.OpenMoodAnalysis(period)
            val periodText = when (period) {
                "week" -> "这周"
                "month" -> "这个月"
                "year" -> "今年"
                else -> "这周"
            }
            respondAndSpeak("${prefix}好的，我来给您看看${periodText}的情绪变化。")
            return true
        }
        
        // ========== 设置调整类命令 ==========
        
        // 字体大小
        val fontIncreaseKeywords = listOf("增大字体", "字体大一点", "放大字", "字大一点", "字体调大")
        if (fontIncreaseKeywords.any { text.contains(it) }) {
            executeSettingsCommand(VoiceCommandIntent.AdjustFontSize(increase = true))
            return true
        }
        val fontDecreaseKeywords = listOf("减小字体", "字体小一点", "缩小字", "字小一点", "字体调小")
        if (fontDecreaseKeywords.any { text.contains(it) }) {
            executeSettingsCommand(VoiceCommandIntent.AdjustFontSize(increase = false))
            return true
        }
        
        // 跌倒检测
        val fallDetectionOnKeywords = listOf("打开跌倒检测", "开启跌倒检测", "跌倒检测打开")
        if (fallDetectionOnKeywords.any { text.contains(it) }) {
            executeSettingsCommand(VoiceCommandIntent.ToggleSafetyFeature(SafetyFeature.FALL_DETECTION, true))
            return true
        }
        val fallDetectionOffKeywords = listOf("关闭跌倒检测", "关掉跌倒检测")
        if (fallDetectionOffKeywords.any { text.contains(it) }) {
            executeSettingsCommand(VoiceCommandIntent.ToggleSafetyFeature(SafetyFeature.FALL_DETECTION, false))
            return true
        }
        
        // 久坐守护
        val proactiveOnKeywords = listOf("打开久坐守护", "开启久坐守护", "久坐守护打开", "打开久坐提醒", "打开久坐", "久坐无响应")
        if (proactiveOnKeywords.any { text.contains(it) }) {
            executeSettingsCommand(VoiceCommandIntent.ToggleSafetyFeature(SafetyFeature.PROACTIVE_INTERACTION, true))
            return true
        }
        val proactiveOffKeywords = listOf("关闭久坐守护", "关掉久坐守护", "关闭久坐提醒")
        if (proactiveOffKeywords.any { text.contains(it) }) {
            executeSettingsCommand(VoiceCommandIntent.ToggleSafetyFeature(SafetyFeature.PROACTIVE_INTERACTION, false))
            return true
        }
        
        // 位置共享
        val locationOnKeywords = listOf("打开位置共享", "开启位置共享", "位置共享打开", "共享位置")
        if (locationOnKeywords.any { text.contains(it) }) {
            executeSettingsCommand(VoiceCommandIntent.ToggleSafetyFeature(SafetyFeature.LOCATION_SHARING, true))
            return true
        }
        val locationOffKeywords = listOf("关闭位置共享", "关掉位置共享")
        if (locationOffKeywords.any { text.contains(it) }) {
            executeSettingsCommand(VoiceCommandIntent.ToggleSafetyFeature(SafetyFeature.LOCATION_SHARING, false))
            return true
        }
        
        // ========== 联系人添加 ==========
        val addContactKeywords = listOf("增加联系人", "添加联系人", "加联系人", "紧急联系人")
        if (addContactKeywords.any { text.contains(it) }) {
            // 尝试从文本中解析联系人信息
            viewModelScope.launch {
                parseAndAddContact(text)
            }
            return true
        }
        
        return false
    }
    
    /**
     * 兼容旧 API
     */
    @Deprecated("Use detectVoiceCommand instead")
    private fun detectPhotoIntent(text: String): Boolean = detectVoiceCommand(text)
    
    /**
     * 语音回复并保存消息
     */
    private fun respondAndSpeak(response: String) {
        viewModelScope.launch {
            val assistantMessage = Message("assistant", response)
            _messages.value = _messages.value + assistantMessage
            saveMessageToDb(assistantMessage, null)
            speakText(response)
        }
    }
    
    /**
     * 执行设置调整类命令
     */
    private fun executeSettingsCommand(intent: VoiceCommandIntent) {
        val elderName = getElderName()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        
        when (intent) {
            is VoiceCommandIntent.AdjustFontSize -> {
                val currentScale = userPrefs.getFontScale()
                val newScale = if (intent.increase) {
                    (currentScale + 0.1f).coerceAtMost(1.5f)
                } else {
                    (currentScale - 0.1f).coerceAtLeast(0.9f)
                }
                userPrefs.setFontScale(newScale)
                val actionText = if (intent.increase) "调大" else "调小"
                respondAndSpeak("${prefix}好的，字体已经${actionText}了。")
            }
            is VoiceCommandIntent.ToggleSafetyFeature -> {
                val (featureName, success) = when (intent.feature) {
                    SafetyFeature.FALL_DETECTION -> {
                        userPrefs.setFallDetectionEnabled(intent.enable)
                        if (intent.enable) {
                            com.silverlink.app.feature.falldetection.FallDetectionService.start(getApplication())
                        } else {
                            com.silverlink.app.feature.falldetection.FallDetectionService.stop(getApplication())
                        }
                        "跌倒检测" to true
                    }
                    SafetyFeature.PROACTIVE_INTERACTION -> {
                        userPrefs.setProactiveInteractionEnabled(intent.enable)
                        if (intent.enable) {
                            com.silverlink.app.feature.proactive.ProactiveInteractionService.start(getApplication())
                        } else {
                            com.silverlink.app.feature.proactive.ProactiveInteractionService.stop(getApplication())
                        }
                        "久坐守护" to true
                    }
                    SafetyFeature.LOCATION_SHARING -> {
                        userPrefs.setLocationSharingEnabled(intent.enable)
                        if (intent.enable) {
                            com.silverlink.app.feature.location.LocationTrackingService.start(getApplication())
                        } else {
                            com.silverlink.app.feature.location.LocationTrackingService.stop(getApplication())
                        }
                        "位置共享" to true
                    }
                }
                val actionText = if (intent.enable) "开启" else "关闭"
                respondAndSpeak("${prefix}好的，${featureName}已${actionText}。")
            }
            else -> { /* 非设置类命令不在此处理 */ }
        }
    }
    
    /**
     * 解析并添加联系人
     */
    private suspend fun parseAndAddContact(text: String) {
        val elderName = getElderName()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        
        try {
            // 使用 AI 提取联系人信息
            val request = QwenRequest(
                input = Input(
                    messages = listOf(
                        Message("system", """
                            从用户输入中提取联系人信息，返回JSON格式（不要用markdown代码块包裹）：
                            {"name": "姓名", "relationship": "关系", "phone": "手机号"}
                            如果信息不完整，对应字段返回空字符串。
                            只返回JSON，不要其他文字。
                        """.trimIndent()),
                        Message("user", text)
                    )
                )
            )
            
            val response = RetrofitClient.api.chat(request)
            val jsonText = response.output.choices?.firstOrNull()?.message?.content
                ?: response.output.text
                ?: "{}"
            
            // 解析 JSON
            val json = org.json.JSONObject(jsonText.trim())
            val name = json.optString("name", "").trim()
            val relationship = json.optString("relationship", "").trim()
            val phone = json.optString("phone", "").trim()
            
            if (name.isBlank() || phone.isBlank()) {
                respondAndSpeak("${prefix}请告诉我联系人的姓名和手机号，比如说：添加紧急联系人张三，是我儿子，电话13800138000。")
                return
            }
            
            // 保存到数据库
            val contactDao = AppDatabase.getInstance(getApplication()).emergencyContactDao()
            val contact = com.silverlink.app.data.local.entity.EmergencyContactEntity(
                name = name,
                phone = phone,
                relationship = relationship
            )
            contactDao.insertContact(contact)
            
            val relationshipText = if (relationship.isNotBlank()) "（${relationship}）" else ""
            respondAndSpeak("${prefix}好的，已经添加${name}${relationshipText}为紧急联系人。")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse contact info", e)
            respondAndSpeak("${prefix}抱歉，我没听清楚联系人信息，请再说一遍。")
        }
    }
    
    /**
     * 清除语音命令意图状态（导航后调用）
     */
    fun clearVoiceCommandIntent() {
        _voiceCommandIntent.value = VoiceCommandIntent.None
    }
    
    /**
     * 兼容旧 API
     */
    @Deprecated("Use clearVoiceCommandIntent instead")
    fun clearPhotoIntent() {
        clearVoiceCommandIntent()
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
     * 使用 longanhuan 音色，支持方言、角色、情感设置
     */
    fun speakText(text: String, emotion: Emotion = _currentEmotion.value) {
        if (text.isBlank()) return

        val ttsRate = getTtsRateForEmotion(emotion)
        val dialect = userPrefs.userConfig.value.dialect
        // 获取方言名称用于 instruction（如 "四川话"、"广东话"）
        val dialectName = if (dialect != com.silverlink.app.data.local.Dialect.NONE) {
            dialect.displayName
        } else {
            ""
        }
        Log.d(TAG, "Speaking with rate=$ttsRate, dialect=$dialectName, emotion=${emotion.name}")

        viewModelScope.launch {
            _ttsState.value = TtsState.Synthesizing
            
            // TTS 服务会自动构建包含方言、角色、情感的 instruction
            val result = ttsService.synthesize(text, ttsRate, dialectName, emotion)
            
            result.fold(
                onSuccess = { audioData ->
                    _ttsState.value = TtsState.Speaking
                    realtimeManager?.onPlaybackStarted()
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
        streamingTts?.cancelPlayback()
        audioPlayer.stop()
        _ttsState.value = TtsState.Idle
        realtimeManager?.onPlaybackFinished()
    }

    /**
     * 发送消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        // 停止当前的 TTS 播放
        stopSpeaking()
        
        // 检测照片意图 - 如果是照片相关请求，直接处理不发送到 AI
        if (detectPhotoIntent(content)) {
            // 仍然显示用户消息
            val userMessage = Message("user", content)
            _messages.value = _messages.value + userMessage
            viewModelScope.launch {
                saveMessageToDb(userMessage, null)
            }
            return
        }

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

    private fun handleRealtimeFinal(text: String, emotion: Emotion) {
        if (text.isBlank()) {
            _conversationState.value = ConversationState.Listening
            return
        }

        _currentEmotion.value = emotion
        val userMessage = Message("user", text)
        val currentHistory = _messages.value
        _messages.value = currentHistory + userMessage

        viewModelScope.launch {
            saveMessageToDb(userMessage, _currentEmotion.value)

            _isLoading.value = true
            try {
                val apiMessages = mutableListOf<Message>()
                apiMessages.add(buildSystemPrompt())
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
                saveMessageToDb(assistantMessage, null)

                streamingTts?.requestReply(assistantMessageContent)
            } catch (e: Exception) {
                val errorMessage = "网络好像有点卡，请检查一下网络连接哦。"
                val errorMsg = Message("assistant", errorMessage)
                _messages.value = _messages.value + errorMsg
                saveMessageToDb(errorMsg, null)
                streamingTts?.requestReply(errorMessage)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 检查药品 - 通过拍照识别并验证是否是当前应该吃的药
     */
    fun checkPill(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // 1. 使用 AI 识别药品
                val recognitionService = com.silverlink.app.feature.reminder.MedicationRecognitionService()
                val verificationHelper = com.silverlink.app.feature.reminder.MedicationVerificationHelper()
                
                val recognitionResult = recognitionService.recognizeMedication(bitmap)
                
                recognitionResult.fold(
                    onSuccess = { recognized ->
                        Log.d(TAG, "Recognized medication: ${recognized.name}")
                        
                        // 2. 获取当前计划的所有药品
                        val medications = com.silverlink.app.SilverLinkApp.database.medicationDao()
                            .getAllMedications().first()
                        
                        // 3. 获取当前时间
                        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date())
                        
                        // 4. 验证药品
                        val verificationResult = verificationHelper.verifyMedication(
                            recognizedName = recognized.name,
                            scheduledMeds = medications,
                            currentTime = currentTime
                        )
                        
                        // 5. 生成并播报语音回复
                        val response = generatePillCheckResponse(verificationResult)
                        
                        // 6. 显示为消息
                        val assistantMessage = com.silverlink.app.data.remote.model.Message("assistant", response)
                        _messages.value = _messages.value + assistantMessage
                        saveMessageToDb(assistantMessage, null)
                        
                        // 7. 语音播报
                        speakText(response)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Recognition failed", error)
                        val response = generateUnknownPillResponse()
                        val assistantMessage = com.silverlink.app.data.remote.model.Message("assistant", response)
                        _messages.value = _messages.value + assistantMessage
                        saveMessageToDb(assistantMessage, null)
                        speakText(response)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in checkPill", e)
                val errorMsg = "抱歉，出了点问题，请再试一次。"
                val assistantMessage = com.silverlink.app.data.remote.model.Message("assistant", errorMsg)
                _messages.value = _messages.value + assistantMessage
                speakText(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun generatePillCheckResponse(result: com.silverlink.app.feature.reminder.VerificationResult): String {
        val elderName = getElderName()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        
        return when (result) {
            is com.silverlink.app.feature.reminder.VerificationResult.Correct -> {
                "${prefix}是的，这就是${result.medicationName}，请服用${result.dosage}。"
            }
            is com.silverlink.app.feature.reminder.VerificationResult.WrongMed -> {
                "${prefix}不对哦，这个是${result.recognizedName}。你现在需要吃的是${result.correctName}，${result.correctDosage}。"
            }
            is com.silverlink.app.feature.reminder.VerificationResult.NoScheduleNow -> {
                if (result.nextTime != null) {
                    "${prefix}现在不是吃药时间哦，下次吃药时间是${result.nextTime}。"
                } else {
                    "${prefix}现在没有需要吃的药哦。"
                }
            }
            is com.silverlink.app.feature.reminder.VerificationResult.UnknownMed -> {
                generateUnknownPillResponse()
            }
        }
    }
    
    private fun generateUnknownPillResponse(): String {
        val elderName = getElderName()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        return "${prefix}抱歉，我没能认出这个药。你可以把药瓶正面对着我再试一次吗？"
    }

    override fun onCleared() {
        super.onCleared()
        realtimeManager?.release()
        audioPlayer.release()
        com.silverlink.app.feature.chat.realtime.SharedAudioSession.reset()
    }
}
