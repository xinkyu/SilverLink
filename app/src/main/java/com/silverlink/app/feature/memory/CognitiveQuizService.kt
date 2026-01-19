package com.silverlink.app.feature.memory

import android.content.Context
import android.util.Log
import android.provider.Settings
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.dao.CognitiveStats
import com.silverlink.app.data.local.entity.CognitiveSummary
import com.silverlink.app.data.local.entity.CognitiveLogEntity
import com.silverlink.app.data.local.entity.MemoryPhotoEntity
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "CognitiveQuizService"

/**
 * 认知评估服务
 * 负责生成记忆考察问题、验证答案、记录结果
 */
class CognitiveQuizService(private val context: Context) {
    
    private val database = SilverLinkApp.database
    private val memoryPhotoDao = database.memoryPhotoDao()
    private val cognitiveLogDao = database.cognitiveLogDao()
    private val userPrefs = UserPreferences.getInstance(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    /**
     * 问题类型
     */
    enum class QuestionType(val displayName: String, val promptTemplate: String) {
        PERSON("人物识别", "照片里的这个人是谁？"),
        LOCATION("地点回忆", "这张照片是在哪里拍的？"),
        EVENT("事件回忆", "这张照片记录的是什么事情？")
    }
    
    /**
     * 测验问题
     */
    data class QuizQuestion(
        val photo: MemoryPhotoEntity,
        val questionType: QuestionType,
        val questionText: String,
        val expectedAnswers: List<String>,  // 可能的正确答案列表
        val hint: String? = null            // 可选提示
    )
    
    /**
     * 测验结果
     */
    sealed class QuizResult {
        data class Correct(
            val question: QuizQuestion,
            val userAnswer: String,
            val encouragement: String,
            val responseTimeMs: Long
        ) : QuizResult()
        
        data class Incorrect(
            val question: QuizQuestion,
            val userAnswer: String,
            val correctAnswer: String,
            val gentleHint: String,
            val responseTimeMs: Long
        ) : QuizResult()
        
        data class PartiallyCorrect(
            val question: QuizQuestion,
            val userAnswer: String,
            val confidence: Float,
            val feedback: String,
            val responseTimeMs: Long
        ) : QuizResult()
    }
    
    /**
     * 生成随机测验问题
     * 优先从本地数据库获取，若无照片则尝试从云端获取
     */
    suspend fun generateQuiz(): QuizQuestion? {
        if (deviceId.isBlank()) {
            Log.w(TAG, "Elder device ID not found")
            return null
        }
        
        // 1. 先尝试从本地数据库获取已下载的包含人物的照片
        val localPhoto = memoryPhotoDao.getRandomPersonPhoto(deviceId)
        if (localPhoto != null) {
            Log.d(TAG, "Using local photo for quiz: ${localPhoto.cloudId}")
            return generateQuestionFromPhoto(localPhoto)
        }
        
        // 2. 本地无照片时，尝试从云端获取
        Log.d(TAG, "No local photos, fetching from cloud...")
        try {
            val cloudResult = CloudBaseService.getMemoryPhotos(
                elderDeviceId = deviceId,
                pageSize = 50
            )
            
            cloudResult.onSuccess { photos ->
                // 筛选包含人物的照片
                val photosWithPeople = photos.filter { !it.people.isNullOrBlank() }
                val selectedPhoto = photosWithPeople.randomOrNull() ?: photos.randomOrNull()
                
                if (selectedPhoto != null) {
                    Log.d(TAG, "Using cloud photo for quiz: ${selectedPhoto.id}")
                    // 转换为 Entity 用于问题生成
                    val entity = MemoryPhotoEntity(
                        cloudId = selectedPhoto.id,
                        elderDeviceId = selectedPhoto.elderDeviceId,
                        familyDeviceId = selectedPhoto.familyDeviceId,
                        imageUrl = selectedPhoto.imageUrl,
                        thumbnailUrl = selectedPhoto.thumbnailUrl,
                        localPath = null,
                        thumbnailPath = null,
                        description = selectedPhoto.description,
                        aiDescription = selectedPhoto.aiDescription,
                        takenDate = selectedPhoto.takenDate,
                        location = selectedPhoto.location,
                        people = selectedPhoto.people,
                        tags = selectedPhoto.tags,
                        isDownloaded = false
                    )
                    return generateQuestionFromPhoto(entity)
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to fetch cloud photos", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching cloud photos", e)
        }
        
        Log.w(TAG, "No suitable photos for quiz")
        return null
    }
    
    /**
     * 从照片生成测验问题
     */
    private fun generateQuestionFromPhoto(photo: MemoryPhotoEntity): QuizQuestion {
        val questionType = selectQuestionType(photo)
        val (questionText, expectedAnswers) = generateQuestionContent(photo, questionType)
        
        return QuizQuestion(
            photo = photo,
            questionType = questionType,
            questionText = questionText,
            expectedAnswers = expectedAnswers,
            hint = generateHint(photo, questionType)
        )
    }
    
    /**
     * 根据照片内容选择合适的问题类型
     */
    private fun selectQuestionType(photo: MemoryPhotoEntity): QuestionType {
        return when {
            !photo.people.isNullOrBlank() -> QuestionType.PERSON
            !photo.location.isNullOrBlank() -> QuestionType.LOCATION
            else -> QuestionType.EVENT
        }
    }
    
    /**
     * 生成问题内容和预期答案
     */
    private fun generateQuestionContent(
        photo: MemoryPhotoEntity, 
        questionType: QuestionType
    ): Pair<String, List<String>> {
        val elderName = userPrefs.userConfig.value.elderName.trim()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        
        return when (questionType) {
            QuestionType.PERSON -> {
                val people = photo.people?.split(",")?.map { it.trim() } ?: emptyList()
                val question = "${prefix}您还记得照片里的这个人是谁吗？"
                question to people
            }
            QuestionType.LOCATION -> {
                val location = photo.location ?: ""
                val question = "${prefix}这张照片是在哪里拍的呀？"
                question to listOf(location)
            }
            QuestionType.EVENT -> {
                val description = photo.description.takeIf { it.isNotBlank() } 
                    ?: photo.aiDescription
                val question = "${prefix}这张照片记录的是什么事情？"
                question to listOf(description)
            }
        }
    }
    
    /**
     * 生成提示信息
     */
    private fun generateHint(photo: MemoryPhotoEntity, questionType: QuestionType): String? {
        return when (questionType) {
            QuestionType.PERSON -> {
                photo.takenDate?.let { "这是${it}拍的照片" }
            }
            QuestionType.LOCATION -> {
                photo.people?.let { "当时$it 和您在一起" }
            }
            QuestionType.EVENT -> {
                photo.takenDate?.let { "这是${it}的事情" }
            }
        }
    }
    
    /**
     * 验证用户答案
     * 使用 Qwen AI 进行语义匹配
     */
    suspend fun verifyAnswer(
        question: QuizQuestion,
        userAnswer: String,
        responseTimeMs: Long
    ): QuizResult {
        val elderName = userPrefs.userConfig.first().elderName.trim()
        val prefix = if (elderName.isNotBlank()) "${elderName}，" else ""
        
        // 使用 AI 进行语义匹配
        val matchResult = matchAnswerWithAI(
            userAnswer = userAnswer,
            expectedAnswers = question.expectedAnswers,
            questionType = question.questionType
        )
        
        val result = when {
            matchResult.isCorrect -> {
                QuizResult.Correct(
                    question = question,
                    userAnswer = userAnswer,
                    encouragement = generateEncouragement(prefix),
                    responseTimeMs = responseTimeMs
                )
            }
            matchResult.confidence >= 0.5f -> {
                QuizResult.PartiallyCorrect(
                    question = question,
                    userAnswer = userAnswer,
                    confidence = matchResult.confidence,
                    feedback = "${prefix}很接近了！${question.expectedAnswers.firstOrNull() ?: ""}",
                    responseTimeMs = responseTimeMs
                )
            }
            else -> {
                QuizResult.Incorrect(
                    question = question,
                    userAnswer = userAnswer,
                    correctAnswer = question.expectedAnswers.firstOrNull() ?: "",
                    gentleHint = "${prefix}没关系，这是${question.expectedAnswers.firstOrNull()}。下次您一定能记住！",
                    responseTimeMs = responseTimeMs
                )
            }
        }
        
        // 记录结果到数据库
        saveQuizResult(question, userAnswer, result, responseTimeMs, matchResult.confidence)
        
        return result
    }
    
    /**
     * 使用 AI 进行答案语义匹配
     */
    private suspend fun matchAnswerWithAI(
        userAnswer: String,
        expectedAnswers: List<String>,
        questionType: QuestionType
    ): AnswerMatchResult {
        try {
            val expectedStr = expectedAnswers.joinToString("、")
            val prompt = """
                你是一个答案匹配助手。判断用户的回答是否与预期答案匹配。
                
                问题类型：${questionType.displayName}
                预期答案：$expectedStr
                用户回答：$userAnswer
                
                请判断用户回答是否正确，考虑以下情况：
                - 称呼变体（如"儿子"和"孩子"、"老伴"和"老婆"都算对）
                - 简称或昵称（如"故宫"和"北京故宫"都算对）
                - 大致正确的描述也算对
                
                回复格式（只输出 JSON，不要其他内容）：
                {"correct": true/false, "confidence": 0.0-1.0}
            """.trimIndent()
            
            val request = QwenRequest(
                input = Input(
                    messages = listOf(
                        Message("system", "你是一个 JSON 输出助手，只输出有效的 JSON。"),
                        Message("user", prompt)
                    )
                )
            )
            
            val response = RetrofitClient.api.chat(request)
            val content = response.output.choices?.firstOrNull()?.message?.content
                ?: response.output.text ?: ""
            
            // 解析 JSON
            val jsonPattern = """\{.*?"correct"\s*:\s*(true|false).*?"confidence"\s*:\s*([\d.]+).*?\}""".toRegex()
            val match = jsonPattern.find(content)
            
            return if (match != null) {
                val isCorrect = match.groupValues[1] == "true"
                val confidence = match.groupValues[2].toFloatOrNull() ?: 0f
                AnswerMatchResult(isCorrect, confidence)
            } else {
                // 备用：简单字符串匹配
                fallbackMatch(userAnswer, expectedAnswers)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI matching failed", e)
            return fallbackMatch(userAnswer, expectedAnswers)
        }
    }
    
    /**
     * 备用匹配方案：简单字符串匹配
     */
    private fun fallbackMatch(userAnswer: String, expectedAnswers: List<String>): AnswerMatchResult {
        val normalizedAnswer = userAnswer.lowercase().replace(" ", "")
        for (expected in expectedAnswers) {
            val normalizedExpected = expected.lowercase().replace(" ", "")
            if (normalizedAnswer.contains(normalizedExpected) || 
                normalizedExpected.contains(normalizedAnswer)) {
                return AnswerMatchResult(true, 1.0f)
            }
        }
        return AnswerMatchResult(false, 0f)
    }
    
    /**
     * 生成鼓励语
     */
    private fun generateEncouragement(prefix: String): String {
        val encouragements = listOf(
            "${prefix}太棒了！您的记忆力真好！",
            "${prefix}完全正确！记忆力很棒呢！",
            "${prefix}对啦！您记得特别清楚！",
            "${prefix}厉害！一下就答对了！"
        )
        return encouragements.random()
    }
    
    /**
     * 保存测验结果到数据库
     */
    private suspend fun saveQuizResult(
        question: QuizQuestion,
        userAnswer: String,
        result: QuizResult,
        responseTimeMs: Long,
        confidence: Float
    ) {
        val isCorrect = result is QuizResult.Correct
        
        val log = CognitiveLogEntity(
            elderDeviceId = deviceId,
            photoCloudId = question.photo.cloudId,
            questionType = question.questionType.name,
            expectedAnswer = question.expectedAnswers.firstOrNull() ?: "",
            actualAnswer = userAnswer,
            isCorrect = isCorrect,
            responseTimeMs = responseTimeMs,
            confidence = confidence
        )
        
        val logId = cognitiveLogDao.insert(log)
        Log.d(TAG, "Quiz result saved: correct=$isCorrect, time=${responseTimeMs}ms")

        // 同步到云端（失败不影响本地）
        try {
            val syncResult = CloudBaseService.logCognitiveResult(
                elderDeviceId = deviceId,
                photoId = question.photo.cloudId,
                questionType = question.questionType.name.lowercase(),
                expectedAnswer = question.expectedAnswers.firstOrNull() ?: "",
                actualAnswer = userAnswer,
                isCorrect = isCorrect,
                responseTimeMs = responseTimeMs,
                confidence = confidence
            )
            if (syncResult.isSuccess) {
                cognitiveLogDao.markAsSynced(logId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync cognitive log", e)
        }
    }
    
    /**
     * 获取认知统计
     */
    suspend fun getStats(days: Int = 7): CognitiveStats? {
        if (deviceId.isBlank()) return null
        
        val sinceTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return cognitiveLogDao.getStats(deviceId, sinceTime)
    }

    /**
     * 生成本地认知周报摘要
     */
    suspend fun generateLocalSummary(days: Int = 7): CognitiveSummary? {
        if (deviceId.isBlank()) return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - days * 24 * 60 * 60 * 1000L
        val stats = cognitiveLogDao.getStats(deviceId, startTime)
        val avgTime = cognitiveLogDao.getAverageResponseTime(deviceId, startTime) ?: 0L
        return CognitiveSummary(
            totalQuestions = stats.total,
            correctAnswers = stats.correct,
            averageResponseTimeMs = avgTime,
            startDate = dateFormat.format(java.util.Date(startTime)),
            endDate = dateFormat.format(java.util.Date(endTime))
        )
    }

    /**
     * 计算认知趋势（improving / stable / declining）
     */
    suspend fun calculateTrend(days: Int = 7): String {
        if (deviceId.isBlank()) return "stable"

        val now = System.currentTimeMillis()
        val currentStart = now - days * 24 * 60 * 60 * 1000L
        val previousStart = now - days * 2 * 24 * 60 * 60 * 1000L

        val currentStats = cognitiveLogDao.getStats(deviceId, currentStart)
        val previousStats = cognitiveLogDao.getStats(deviceId, previousStart)

        val diff = currentStats.correctRate - previousStats.correctRate
        return when {
            diff >= 0.05f -> "improving"
            diff <= -0.05f -> "declining"
            else -> "stable"
        }
    }
    
    /**
     * 答案匹配结果
     */
    private data class AnswerMatchResult(
        val isCorrect: Boolean,
        val confidence: Float
    )
}
