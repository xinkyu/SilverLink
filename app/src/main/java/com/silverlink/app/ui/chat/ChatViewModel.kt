package com.silverlink.app.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.ChatMessageEntity
import com.silverlink.app.data.local.ConversationEntity
import com.silverlink.app.data.local.MemoryRecordEntity
import com.silverlink.app.data.local.UserProfileMemoryEntity
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest
import com.silverlink.app.feature.chat.AudioPlayerHelper
import com.silverlink.app.feature.chat.AudioRecorder
import com.silverlink.app.feature.chat.HybridMemoryRagService
import com.silverlink.app.feature.chat.MemoryMaintenanceService
import com.silverlink.app.feature.chat.MemoryRetrievalService
import com.silverlink.app.feature.chat.RagConfig
import com.silverlink.app.feature.chat.RagDebugSnapshot
import com.silverlink.app.feature.chat.SpeechRecognitionService
import com.silverlink.app.feature.chat.TextToSpeechService
import com.silverlink.app.feature.emotion.EmotionRecognitionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ChatViewModel"
private const val SHORT_TERM_WINDOW_SIZE = 12
private const val MAX_MEMORY_CONTENT_LENGTH = 140
private const val SYSTEM_PROMPT_CHAR_BUDGET = 1800
private const val MEMORY_EXTRACTION_IDLE_DELAY_MS = 45_000L
private const val MEMORY_EXTRACTION_PERIODIC_MS = 20 * 60 * 1000L
private const val MEMORY_EXTRACTION_BATCH_SIZE = 30
private const val LLM_MEMORY_CURSOR_KEY = "llm_memory_cursor_id"
private const val LLM_MEMORY_LAST_RUN_KEY = "llm_memory_last_run_at"
private const val MEMORY_LOG_PREFIX = "[LLM-Memory]"

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

    private val _memoryRecords = MutableStateFlow<List<MemoryRecordEntity>>(emptyList())
    val memoryRecords: StateFlow<List<MemoryRecordEntity>> = _memoryRecords.asStateFlow()

    private val _profileMemories = MutableStateFlow<List<UserProfileMemoryEntity>>(emptyList())
    val profileMemories: StateFlow<List<UserProfileMemoryEntity>> = _profileMemories.asStateFlow()

    private val _ragConfig = MutableStateFlow(RagConfig())
    val ragConfig: StateFlow<RagConfig> = _ragConfig.asStateFlow()

    private val _ragDebugEnabled = MutableStateFlow(false)
    val ragDebugEnabled: StateFlow<Boolean> = _ragDebugEnabled.asStateFlow()

    private val _ragDebugSnapshot = MutableStateFlow<RagDebugSnapshot?>(null)
    val ragDebugSnapshot: StateFlow<RagDebugSnapshot?> = _ragDebugSnapshot.asStateFlow()
    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    // 语音命令意图状态 - 用于触发导航和执行操作
    enum class SafetyFeature { FALL_DETECTION, PROACTIVE_INTERACTION, LOCATION_SHARING }
    
    sealed class VoiceCommandIntent {
        object None : VoiceCommandIntent()
        // 导航类
        object OpenRealtimeCall : VoiceCommandIntent()
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
    private val speechService = SpeechRecognitionService(application)
    private val ttsService = TextToSpeechService()
    private val audioPlayer = AudioPlayerHelper(application)
    private var currentAudioFile: String? = null
    private var realtimeManager: RealtimeConversationManager? = null
    private var streamingTts: StreamingTtsControllerImpl? = null
    private var realtimeAsr: StreamingAsrClientApi? = null
    
    // 数据库访问
    private val appDatabase = AppDatabase.getInstance(application)
    private val chatDao = appDatabase.chatDao()
    private val historyDao = appDatabase.historyDao()
    private val memoryDao = appDatabase.memoryDao()
    private val hybridRagService by lazy {
        HybridMemoryRagService(memoryDao, ::extractKeywords)
    }
    private val memoryRetrievalService: MemoryRetrievalService by lazy {
        hybridRagService
    }
    private val memoryMaintenanceService = MemoryMaintenanceService(memoryDao)
    private val userPrefs = UserPreferences.getInstance(application)
    private val syncRepository = com.silverlink.app.data.repository.SyncRepository.getInstance(application)
    private val memoryExtractionMutex = Mutex()
    private var idleMemoryExtractionJob: Job? = null
    private var periodicMemoryExtractionJob: Job? = null

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

        startPeriodicMemoryExtraction()

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
            clonedVoiceIdProvider = {
                userPrefs.userConfig.value.clonedVoiceId
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

    private fun extractKeywords(text: String): List<String> {
        val normalized = text
            .lowercase()
            .replace(Regex("[\\n\\r\\t，。！？、；：,.!?;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return emptyList()

        val stopWords = setOf("我们", "你们", "这个", "那个", "然后", "就是", "已经", "还是", "因为", "所以", "今天", "现在")
        val tokenTerms = normalized
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }

        val compact = normalized.replace(" ", "")
        val ngramTerms = mutableSetOf<String>()
        for (n in 2..3) {
            if (compact.length >= n) {
                for (i in 0..compact.length - n) {
                    val gram = compact.substring(i, i + n)
                    if (gram.any { it.isLetterOrDigit() || it.code in 0x4E00..0x9FA5 }) {
                        ngramTerms.add(gram)
                    }
                }
            }
        }

        val domainTerms = listOf(
            "高血压", "糖尿病", "冠心病", "心脏病", "头晕", "吃药", "药", "儿子", "女儿", "孙子", "孙女", "老伴", "外孙",
            "名字", "称呼", "住在", "喜欢"
        ).filter { compact.contains(it) }

        return (domainTerms + tokenTerms + ngramTerms)
            .distinct()
            .filter { it.length >= 2 && it !in stopWords }
            .take(12)
    }

    private fun estimateImportance(content: String, emotion: Emotion): Float {
        var score = 0.35f
        if (content.contains("高血压") || content.contains("糖尿病") || content.contains("心脏") || content.contains("头晕")) {
            score += 0.35f
        }
        if (content.contains("儿子") || content.contains("女儿") || content.contains("孙子") || content.contains("老伴")) {
            score += 0.2f
        }
        if (emotion == Emotion.SAD || emotion == Emotion.ANXIOUS) {
            score += 0.15f
        }
        return score.coerceIn(0.1f, 1.0f)
    }

    private fun scheduleIdleMemoryExtraction() {
        idleMemoryExtractionJob?.cancel()
        Log.d(TAG, "$MEMORY_LOG_PREFIX schedule idle extraction in ${MEMORY_EXTRACTION_IDLE_DELAY_MS}ms")
        idleMemoryExtractionJob = viewModelScope.launch {
            delay(MEMORY_EXTRACTION_IDLE_DELAY_MS)
            runLlmMemoryExtraction("idle")
        }
    }

    private fun startPeriodicMemoryExtraction() {
        periodicMemoryExtractionJob?.cancel()
        Log.d(TAG, "$MEMORY_LOG_PREFIX start periodic extraction, interval=${MEMORY_EXTRACTION_PERIODIC_MS}ms")
        periodicMemoryExtractionJob = viewModelScope.launch {
            while (isActive) {
                delay(MEMORY_EXTRACTION_PERIODIC_MS)
                runLlmMemoryExtraction("periodic")
            }
        }
    }

    private suspend fun runLlmMemoryExtraction(trigger: String) {
        memoryExtractionMutex.withLock {
            runCatching {
                val cursor = memoryDao.getUserProfileMemory(LLM_MEMORY_CURSOR_KEY)?.value?.toLongOrNull() ?: 0L
                val messages = chatDao.getUserMessagesAfterId(cursor, MEMORY_EXTRACTION_BATCH_SIZE)
                Log.d(TAG, "$MEMORY_LOG_PREFIX trigger=$trigger cursor=$cursor fetched=${messages.size}")
                if (messages.isEmpty()) {
                    Log.d(TAG, "$MEMORY_LOG_PREFIX no new user messages, skip")
                    return
                }

                val latestMessageId = messages.maxOf { it.id }
                val latestConversationId = messages.lastOrNull()?.conversationId ?: (_currentConversationId.value ?: 0L)
                val firstMessageId = messages.minOf { it.id }
                Log.d(
                    TAG,
                    "$MEMORY_LOG_PREFIX processing messageRange=[$firstMessageId,$latestMessageId], latestConversationId=$latestConversationId"
                )
                val transcript = messages.joinToString("\n") { "[id=${it.id}] ${it.content}" }

                val prompt = """
                    你是养老对话的记忆提炼器。请从下面用户消息中提炼“长期有价值记忆”，输出严格JSON：
                    {
                      "memory_facts": [{"text":"...","importance":0.0}],
                      "profile_updates": [{"key":"preferred_name|age|health_conditions|family_relations|living_location|preferences","value":"...","confidence":0.0}]
                    }
                    规则：
                    1) 仅提炼稳定、可复用信息，忽略临时寒暄。
                    2) memory_facts 每条 text 4-60字，importance 范围 0.1-1.0。
                    3) profile_updates 仅在有明确依据时输出。
                    4) 只返回JSON，不要代码块和解释。

                    用户消息：
                    $transcript
                """.trimIndent()

                val request = QwenRequest(
                    input = Input(
                        messages = listOf(
                            Message("system", "你是严格JSON输出助手。"),
                            Message("user", prompt)
                        )
                    )
                )

                val response = RetrofitClient.api.chat(request)
                val raw = response.output.choices?.firstOrNull()?.message?.content ?: response.output.text ?: "{}"
                val payload = extractJsonObject(raw)
                val obj = JSONObject(payload)

                val memoryFacts = obj.optJSONArray("memory_facts") ?: JSONArray()
                var savedFacts = 0
                for (i in 0 until memoryFacts.length()) {
                    val item = memoryFacts.optJSONObject(i) ?: continue
                    val text = item.optString("text", "").trim().take(MAX_MEMORY_CONTENT_LENGTH)
                    if (text.length < 4) continue
                    val importance = item.optDouble("importance", 0.6).toFloat().coerceIn(0.1f, 1.0f)
                    val keywords = extractKeywords(text)
                    memoryMaintenanceService.upsertMemoryRecord(
                        conversationId = latestConversationId,
                        content = text,
                        keywordsText = keywords.joinToString(","),
                        importance = importance
                    )
                    savedFacts++
                }

                val profileUpdates = obj.optJSONArray("profile_updates") ?: JSONArray()
                var savedProfiles = 0
                for (i in 0 until profileUpdates.length()) {
                    val item = profileUpdates.optJSONObject(i) ?: continue
                    val key = item.optString("key", "").trim()
                    val value = item.optString("value", "").trim()
                    if (key.isBlank() || value.isBlank()) continue
                    val confidence = item.optDouble("confidence", 0.7).toFloat().coerceIn(0.1f, 1.0f)
                    memoryMaintenanceService.mergeProfileMemory(key, value, confidence)
                    savedProfiles++
                }

                Log.d(
                    TAG,
                    "$MEMORY_LOG_PREFIX extracted memoryFacts=${memoryFacts.length()} savedFacts=$savedFacts, profileUpdates=${profileUpdates.length()} savedProfiles=$savedProfiles"
                )

                memoryDao.upsertUserProfileMemory(
                    UserProfileMemoryEntity(
                        key = LLM_MEMORY_CURSOR_KEY,
                        value = latestMessageId.toString(),
                        confidence = 1.0f,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                memoryDao.upsertUserProfileMemory(
                    UserProfileMemoryEntity(
                        key = LLM_MEMORY_LAST_RUN_KEY,
                        value = "${System.currentTimeMillis()}|$trigger",
                        confidence = 1.0f,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                Log.d(TAG, "$MEMORY_LOG_PREFIX done trigger=$trigger newCursor=$latestMessageId")

                refreshMemoryCenter()
            }.onFailure {
                Log.w(TAG, "LLM memory extraction failed (trigger=$trigger)", it)
            }
        }
    }

    private fun extractJsonObject(raw: String): String {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else "{}"
    }

    private fun extractMemoryFacts(content: String): List<String> {
        val text = content.trim()
        if (text.length < 6) return emptyList()

        val facts = mutableListOf<String>()

        Regex("我叫([\\u4E00-\\u9FA5A-Za-z]{1,12})").find(text)?.groupValues?.getOrNull(1)?.let {
            facts.add("偏好称呼是$it")
        }
        Regex("我今年([0-9]{2,3})岁").find(text)?.groupValues?.getOrNull(1)?.let {
            facts.add("年龄约${it}岁")
        }

        val diseaseHits = listOf("高血压", "糖尿病", "冠心病", "心脏病", "失眠", "关节炎", "脑梗", "哮喘")
            .filter { text.contains(it) }
        if (diseaseHits.isNotEmpty()) {
            facts.add("健康状况：${diseaseHits.distinct().joinToString("、")}")
        }

        val relationHits = listOf("儿子", "女儿", "孙子", "孙女", "老伴", "外孙", "外孙女")
            .filter { text.contains(it) }
        if (relationHits.isNotEmpty()) {
            facts.add("家庭关系提及：${relationHits.distinct().joinToString("、")}")
        }

        val habitPattern = Regex("(每天|经常|通常|总是)([^。！？]{2,20})")
        habitPattern.findAll(text).forEach { m ->
            val habit = (m.groupValues.getOrNull(1).orEmpty() + m.groupValues.getOrNull(2).orEmpty()).trim()
            if (habit.length in 4..20) {
                facts.add("生活习惯：$habit")
            }
        }

        // 如果没有抽取到结构化事实，且语句明显包含健康或个人重要状态，再保留短摘要
        if (facts.isEmpty()) {
            // 移除了过于宽泛的 "我", "我的", "家里" 避免每条包含“我”的日常对话都被存入记忆
            val personalSignals = listOf("身体", "吃药", "睡", "头晕", "血压", "不舒服", "疼", "去医院")
            if (personalSignals.any { text.contains(it) }) {
                facts.add(text.take(MAX_MEMORY_CONTENT_LENGTH))
            }
        }

        return facts
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length >= 4 }
            .distinct()
            .take(3)
    }

    // 旧的本地规则提炼流程已废弃，改由后台 LLM 提炼任务处理。

    private suspend fun buildCoreProfileMemoryLines(): List<String> {
        val lines = mutableListOf<String>()
        val elderName = getElderName()
        if (elderName.isNotBlank()) {
            lines.add("偏好称呼：$elderName")
        }

        val elderProfile = getElderProfile()
        if (elderProfile.isNotBlank()) {
            lines.add("基础画像：$elderProfile")
        }

        val hasMajorDisease = userPrefs.userConfig.value.hasMajorDisease
        val majorDiseaseDetails = userPrefs.userConfig.value.majorDiseaseDetails
        if (hasMajorDisease && majorDiseaseDetails.isNotBlank()) {
            lines.add("健康重点：$majorDiseaseDetails")
        }

        memoryDao.listUserProfileMemories().forEach { profile ->
            val keyLabel = when (profile.key) {
                "preferred_name" -> "偏好称呼"
                "age" -> "年龄"
                "health_conditions" -> "健康状况"
                "family_relations" -> "家庭关系"
                "living_location" -> "居住地"
                "preferences" -> "偏好"
                else -> profile.key
            }
            lines.add("$keyLabel：${profile.value}")
        }

        return lines.distinct().take(8)
    }

    private fun clipText(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text else text.take(maxLength) + "…"
    }

    /**
     * 根据当前情绪生成动态 System Prompt，并注入核心画像和长期记忆。
     */
    private suspend fun buildSystemPrompt(userInput: String): Message {
        val emotionHint = _currentEmotion.value.promptHint
        val elderName = getElderName()

        val dialect = userPrefs.userConfig.value.dialect
        val coreProfileLines = buildCoreProfileMemoryLines()
        val ragContext = memoryRetrievalService.buildGroundedContext(
            query = userInput,
            limit = _ragConfig.value.topK,
            maxChars = 560
        )
        if (_ragDebugEnabled.value) {
            _ragDebugSnapshot.value = memoryRetrievalService.getLastDebugSnapshot()
        }
        
        val nameHint = if (elderName.isNotBlank()) {
            "用户称呼为“$elderName”，回复时请优先使用该称呼，避免使用“爷爷奶奶”等泛称。"
        } else {
            "请避免使用“爷爷奶奶”等泛称，改用“您”进行称呼。"
        }
        val profileHint = if (coreProfileLines.isNotEmpty()) {
            "【核心记忆】${clipText(coreProfileLines.joinToString("；"), 380)}"
        } else {
            ""
        }

        val longTermMemoryHint = if (ragContext.isNotBlank()) {
            "【长期记忆RAG上下文】\n${clipText(ragContext, 560)}"
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
            append("\n\n【记忆使用规则】若“长期记忆RAG上下文”中存在与当前问题直接相关的信息，请优先基于该信息作答，并给出简洁明确回答。")
            append("\n\n【称呼提示】$nameHint")
            if (profileHint.isNotBlank()) append("\n$profileHint")
            if (longTermMemoryHint.isNotBlank()) append("\n$longTermMemoryHint")
            if (dialectHint.isNotBlank()) append("\n$dialectHint")
            if (emotionHint.isNotBlank()) append("\n【用户情绪提示】$emotionHint")
        }
        return Message(role = "system", content = clipText(fullPrompt, SYSTEM_PROMPT_CHAR_BUDGET))
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
     * 根据文字输入检测情绪 (使用本地 ONNX 模型)
     * 同时保存到本地历史和上传到云端
     */
    private suspend fun detectEmotionFromText(text: String) {
        try {
            val emotionService = EmotionRecognitionService.getInstance(getApplication())
            // 如果未初始化（App启动时初始化失败），尝试重新初始化
            if (!emotionService.isReady()) {
                Log.w(TAG, "EmotionRecognitionService not initialized, attempting re-init...")
                emotionService.initialize()
                Log.d(TAG, "EmotionRecognitionService re-initialized successfully")
            }
            val emotion = emotionService.analyzeTextEmotion(text)
            _currentEmotion.value = emotion
            Log.d(TAG, "ONNX text emotion detected: ${emotion.name} from: $text")

            // 保存情绪记录到本地
            saveMoodLog(emotion, text)
        } catch (e: Throwable) {
            Log.e(TAG, "ONNX text emotion analysis failed, using fallback", e)
            // 失败时使用关键词匹配作为备用
            val fallbackEmotion = guessEmotionFromKeywords(text)
            _currentEmotion.value = fallbackEmotion
            saveMoodLog(fallbackEmotion, text)
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

        // ========== 通话模式命令 ==========
        val callModeKeywords = listOf(
            "打开和你的电话对话模式", "电话对话模式", "语音通话模式", "语音电话模式",
            "打开电话模式", "打开通话模式", "和你电话对话"
        )
        if (callModeKeywords.any { text.contains(it) }) {
            _voiceCommandIntent.value = VoiceCommandIntent.OpenRealtimeCall
            respondAndSpeak("${prefix}好的，这就打开和我的语音通话模式。")
            return true
        }

        // ========== 新增记忆命令 ==========
        val addMemoryKeywords = listOf(
            "新增一条记忆", "添加一条记忆", "加一条记忆", "记一条记忆", "记住这件事", "帮我记住"
        )
        if (addMemoryKeywords.any { text.contains(it) }) {
            val memoryContent = extractMemoryContentFromCommand(text)
            if (memoryContent.isNullOrBlank()) {
                respondAndSpeak("${prefix}好的，请告诉我这条记忆的具体内容，我会帮您记住。")
            } else {
                addMemoryFromUi(memoryContent)
                respondAndSpeak("${prefix}好的，我已经帮您记住了：${memoryContent.take(40)}")
            }
            return true
        }

        // ========== 每日服药/情绪总结命令 ==========
        val askMedicationSummary = isMedicationSummaryQuery(text)
        val askMoodSummary = isMoodSummaryQuery(text)
        if (askMedicationSummary || askMoodSummary) {
            val target = resolveDateQueryTarget(text)
            viewModelScope.launch {
                val parts = mutableListOf<String>()
                if (askMedicationSummary) {
                    parts.add(buildMedicationSummaryForDate(target.date, target.label))
                }
                if (askMoodSummary) {
                    parts.add(buildMoodSummaryForDate(target.date, target.label))
                }
                respondAndSpeak("${prefix}${parts.joinToString(" ")}")
            }
            return true
        }

        // ========== 最近照片命令 ==========
        val latestPhotoKeywords = listOf("最近一张照片", "最新照片", "最近照片")
        if (latestPhotoKeywords.any { text.contains(it) }) {
            _voiceCommandIntent.value = VoiceCommandIntent.OpenGallery
            respondAndSpeak("${prefix}好的，这就打开记忆相册，默认显示最近一张照片。")
            return true
        }
        
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

    private data class DateQueryTarget(val date: String, val label: String)

    private fun resolveDateQueryTarget(text: String): DateQueryTarget {
        val today = java.time.LocalDate.now()
        return when {
            text.contains("前天") -> DateQueryTarget(today.minusDays(2).toString(), "前天")
            text.contains("昨天") -> DateQueryTarget(today.minusDays(1).toString(), "昨天")
            text.contains("今天") || text.contains("今日") -> DateQueryTarget(today.toString(), "今天")
            else -> {
                val matchedDate = Regex("(20\\d{2}-\\d{2}-\\d{2})").find(text)?.groupValues?.getOrNull(1)
                if (matchedDate != null) DateQueryTarget(matchedDate, matchedDate) else DateQueryTarget(today.toString(), "今天")
            }
        }
    }

    private fun extractMemoryContentFromCommand(text: String): String? {
        val anchors = listOf("新增一条记忆", "添加一条记忆", "加一条记忆", "记一条记忆", "记住这件事", "帮我记住")
        anchors.forEach { anchor ->
            if (text.contains(anchor)) {
                val rest = text.substringAfter(anchor, "")
                    .trim()
                    .trimStart('，', ',', '。', '：', ':', '、')
                    .trim()
                if (rest.isNotBlank()) {
                    return rest
                }
            }
        }
        return null
    }

    private fun isMedicationSummaryQuery(text: String): Boolean {
        val medicationKeywords = listOf(
            "药都全吃了吗", "药都吃了吗", "药有没有吃", "吃药情况", "服药情况", "药吃了没"
        )
        return medicationKeywords.any { text.contains(it) }
    }

    private fun isMoodSummaryQuery(text: String): Boolean {
        val moodKeywords = listOf(
            "情绪怎么样", "心情怎么样", "情绪如何", "心情如何", "今天心情", "昨天情绪"
        )
        return moodKeywords.any { text.contains(it) }
    }

    private suspend fun buildMedicationSummaryForDate(date: String, label: String): String {
        val logs = historyDao.getMedicationLogsByDate(date)
        if (logs.isEmpty()) {
            return "${label}没有查到服药记录。"
        }

        val total = logs.size
        val taken = logs.count { it.status.equals("taken", ignoreCase = true) }
        if (taken == total) {
            return "${label}药都按时吃了，共${total}次。"
        }

        val missedLogs = logs.filterNot { it.status.equals("taken", ignoreCase = true) }
        val missedExamples = missedLogs
            .take(3)
            .joinToString("、") { "${it.medicationName}(${it.scheduledTime})" }
        val suffix = if (missedLogs.size > 3) "等${missedLogs.size}次" else ""

        return if (missedExamples.isNotBlank()) {
            "${label}一共记录${total}次服药，已完成${taken}次，还有${total - taken}次未完成，主要是${missedExamples}${suffix}。"
        } else {
            "${label}一共记录${total}次服药，已完成${taken}次，还有${total - taken}次未完成。"
        }
    }

    private suspend fun buildMoodSummaryForDate(date: String, label: String): String {
        val logs = historyDao.getMoodLogsByDate(date)
        if (logs.isEmpty()) {
            return "${label}还没有情绪记录。"
        }

        val dominantMoodLabel = logs
            .groupingBy { it.mood }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: logs.first().mood
        val dominantMood = Emotion.fromLabel(dominantMoodLabel).displayName
        return "${label}记录了${logs.size}次情绪，整体以${dominantMood}为主。"
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
                    
                    // 自动发送识别的文字；跳过 text-only 复判，避免覆盖跨模态结果
                    sendMessage(speechResult.text, skipTextEmotionDetection = true)
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
        val latestClonedVoiceId = userPrefs.userConfig.value.clonedVoiceId.trim()
        ttsService.setClonedVoiceId(latestClonedVoiceId)
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
    fun sendMessage(content: String, skipTextEmotionDetection: Boolean = false) {
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

        // 对文字输入也进行情绪检测（注意：不要在这里启动独立协程，
        // 需要等情绪检测完成后再保存消息和构建prompt）

        val userMessage = Message("user", content)
        val currentHistory = _messages.value
        _messages.value = currentHistory + userMessage

        viewModelScope.launch {
            // 语音链路已完成跨模态情绪识别时，避免再用 text-only 覆盖结果。
            if (skipTextEmotionDetection) {
                Log.d(TAG, "Skipping text-only emotion detection; using current emotion=${_currentEmotion.value.name}")
                saveMoodLog(_currentEmotion.value, content)
            } else {
                // 先完成情绪检测（顺序执行），这样后续保存和prompt构建能使用正确的情绪
                detectEmotionFromText(content)
            }

            // 保存用户消息到数据库（此时 _currentEmotion 已更新）
            saveMessageToDb(userMessage, _currentEmotion.value)
            scheduleIdleMemoryExtraction()

            _isLoading.value = true
            try {
                // 使用动态 System Prompt（根据情绪调整）
                val apiMessages = mutableListOf<Message>()
                apiMessages.add(buildSystemPrompt(content))
                // 短期记忆窗口：保留最近 N 条消息。
                apiMessages.addAll(currentHistory.takeLast(SHORT_TERM_WINDOW_SIZE))
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
            saveMoodLog(_currentEmotion.value, text)
            saveMessageToDb(userMessage, _currentEmotion.value)
            scheduleIdleMemoryExtraction()

            if (detectVoiceCommand(text)) {
                return@launch
            }

            _isLoading.value = true
            try {
                val apiMessages = mutableListOf<Message>()
                apiMessages.add(buildSystemPrompt(text))
                apiMessages.addAll(currentHistory.takeLast(SHORT_TERM_WINDOW_SIZE))
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

    fun refreshMemoryCenter() {
        viewModelScope.launch {
            _memoryRecords.value = memoryDao.listAllMemories()
            _profileMemories.value = memoryDao.listUserProfileMemories()
            if (_ragDebugEnabled.value) {
                _ragDebugSnapshot.value = memoryRetrievalService.getLastDebugSnapshot()
            }
        }
    }

    fun updateRagConfig(config: RagConfig) {
        val normalized = config.normalized()
        _ragConfig.value = normalized
        memoryRetrievalService.updateConfig(normalized)
        if (_ragDebugEnabled.value) {
            _ragDebugSnapshot.value = memoryRetrievalService.getLastDebugSnapshot()
        }
    }

    fun setRagDebugEnabled(enabled: Boolean) {
        _ragDebugEnabled.value = enabled
        memoryRetrievalService.setDebugEnabled(enabled)
        if (!enabled) {
            _ragDebugSnapshot.value = null
        } else {
            _ragDebugSnapshot.value = memoryRetrievalService.getLastDebugSnapshot()
        }
    }

    fun addMemoryFromUi(content: String, importance: Float = 0.7f) {
        val conversationId = _currentConversationId.value ?: return
        val normalized = content.trim().take(MAX_MEMORY_CONTENT_LENGTH)
        if (normalized.isBlank()) return

        viewModelScope.launch {
            val keywords = extractKeywords(normalized)
            memoryMaintenanceService.upsertMemoryRecord(
                conversationId = conversationId,
                content = normalized,
                keywordsText = keywords.joinToString(","),
                importance = importance.coerceIn(0.1f, 1.0f)
            )
            refreshMemoryCenter()
        }
    }

    fun deleteLongTermMemory(id: Long) {
        viewModelScope.launch {
            memoryDao.deleteMemoryById(id)
            refreshMemoryCenter()
        }
    }

    fun deleteStructuredProfileMemory(key: String) {
        viewModelScope.launch {
            memoryDao.deleteUserProfileMemory(key)
            refreshMemoryCenter()
        }
    }

    fun clearAllLongTermMemories() {
        viewModelScope.launch {
            memoryDao.clearAllMemories()
            memoryDao.clearUserProfileMemories()
            refreshMemoryCenter()
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
        idleMemoryExtractionJob?.cancel()
        periodicMemoryExtractionJob?.cancel()
        realtimeManager?.release()
        audioPlayer.release()
        com.silverlink.app.feature.chat.realtime.SharedAudioSession.reset()
    }
}
