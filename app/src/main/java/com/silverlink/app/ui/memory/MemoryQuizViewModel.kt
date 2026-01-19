package com.silverlink.app.ui.memory

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.feature.chat.AudioPlayerHelper
import com.silverlink.app.feature.chat.AudioRecorder
import com.silverlink.app.feature.chat.SpeechRecognitionService
import com.silverlink.app.feature.chat.TextToSpeechService
import com.silverlink.app.feature.memory.CognitiveQuizService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "MemoryQuizViewModel"

/**
 * 认知测验 ViewModel
 */
class MemoryQuizViewModel(application: Application) : AndroidViewModel(application) {
    
    private val quizService = CognitiveQuizService(application)
    private val ttsService = TextToSpeechService()
    private val audioPlayerHelper = AudioPlayerHelper(application)
    private val audioRecorder = AudioRecorder(application)
    private val speechService = SpeechRecognitionService()
    
    // UI 状态
    sealed class QuizUiState {
        object Loading : QuizUiState()
        object NoPhotos : QuizUiState()
        data class ShowingQuestion(val question: CognitiveQuizService.QuizQuestion) : QuizUiState()
        object WaitingForAnswer : QuizUiState()
        data class ShowingResult(
            val result: CognitiveQuizService.QuizResult,
            val isCorrect: Boolean
        ) : QuizUiState()
        data class Error(val message: String) : QuizUiState()
    }
    
    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    // 语音状态
    sealed class VoiceState {
        object Idle : VoiceState()
        object Recording : VoiceState()
        object Recognizing : VoiceState()
        data class Error(val message: String) : VoiceState()
    }

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    // 当前问题
    private var currentQuestion: CognitiveQuizService.QuizQuestion? = null
    private var questionStartTime: Long = 0L
    
    // 统计信息
    private val _correctCount = MutableStateFlow(0)
    val correctCount: StateFlow<Int> = _correctCount.asStateFlow()
    
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()
    
    init {
        loadNextQuestion()
    }
    
    /**
     * 加载下一个问题
     */
    fun loadNextQuestion() {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            
            try {
                val question = quizService.generateQuiz()
                if (question != null) {
                    currentQuestion = question
                    questionStartTime = System.currentTimeMillis()
                    _uiState.value = QuizUiState.ShowingQuestion(question)
                    
                    // 语音播报问题
                    speakQuestion(question.questionText)
                } else {
                    _uiState.value = QuizUiState.NoPhotos
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate quiz", e)
                _uiState.value = QuizUiState.Error("无法生成问题，请稍后再试")
            }
        }
    }
    
    /**
     * 提交答案
     */
    fun submitAnswer(answer: String) {
        val question = currentQuestion ?: return
        val responseTime = System.currentTimeMillis() - questionStartTime
        
        viewModelScope.launch {
            _uiState.value = QuizUiState.WaitingForAnswer
            
            try {
                val result = quizService.verifyAnswer(question, answer, responseTime)
                val isCorrect = result is CognitiveQuizService.QuizResult.Correct
                
                _totalCount.value++
                if (isCorrect) {
                    _correctCount.value++
                }
                
                _uiState.value = QuizUiState.ShowingResult(result, isCorrect)
                
                // 语音播报结果
                speakResult(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify answer", e)
                _uiState.value = QuizUiState.Error("验证答案失败，请重试")
            }
        }
    }

    /**
     * 开始录音
     */
    fun startRecording() {
        val filePath = audioRecorder.startRecording()
        if (filePath != null) {
            _voiceState.value = VoiceState.Recording
        } else {
            _voiceState.value = VoiceState.Error("无法启动录音")
        }
    }

    /**
     * 停止录音并识别答案
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
                    submitAnswer(speechResult.text)
                },
                onFailure = { error ->
                    _voiceState.value = VoiceState.Error(error.message ?: "识别失败")
                }
            )
        }
    }

    /**
     * 重置语音状态
     */
    fun resetVoiceState() {
        _voiceState.value = VoiceState.Idle
    }
    
    /**
     * 播放提示
     */
    fun playHint() {
        val question = currentQuestion ?: return
        question.hint?.let { hint ->
            viewModelScope.launch {
                speakText(hint)
            }
        }
    }
    
    /**
     * 重复问题
     */
    fun repeatQuestion() {
        val question = currentQuestion ?: return
        viewModelScope.launch {
            speakQuestion(question.questionText)
        }
    }
    
    /**
     * 语音播报问题
     */
    private suspend fun speakQuestion(text: String) {
        try {
            val result = ttsService.synthesize(text, 0.9)
            result.onSuccess { audioData ->
                Log.d(TAG, "Question speech synthesized: ${audioData.size} bytes")
                audioPlayerHelper.play(audioData)
            }.onFailure { e ->
                Log.e(TAG, "Question TTS synthesis failed", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed", e)
        }
    }
    
    /**
     * 语音播报结果
     */
    private suspend fun speakResult(result: CognitiveQuizService.QuizResult) {
        val text = when (result) {
            is CognitiveQuizService.QuizResult.Correct -> result.encouragement
            is CognitiveQuizService.QuizResult.Incorrect -> result.gentleHint
            is CognitiveQuizService.QuizResult.PartiallyCorrect -> result.feedback
        }
        speakText(text)
    }
    
    /**
     * 语音朗读
     */
    private suspend fun speakText(text: String) {
        try {
            val result = ttsService.synthesize(text, 0.9)
            result.onSuccess { audioData ->
                Log.d(TAG, "Speech synthesized: ${audioData.size} bytes")
                audioPlayerHelper.play(audioData)
            }.onFailure { e ->
                Log.e(TAG, "TTS synthesis failed", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed", e)
        }
    }
    
    /**
     * 获取统计信息描述
     */
    fun getScoreDescription(): String {
        val total = _totalCount.value
        val correct = _correctCount.value
        return if (total > 0) {
            val rate = correct.toFloat() / total
            when {
                rate >= 0.9f -> "太棒了！全部答对！🎉"
                rate >= 0.7f -> "很不错！继续加油！👍"
                rate >= 0.5f -> "做得好！再练练会更好！💪"
                else -> "没关系，多练习就会进步的！❤️"
            }
        } else {
            "开始测试记忆力吧！"
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        audioPlayerHelper.release()
    }
}
