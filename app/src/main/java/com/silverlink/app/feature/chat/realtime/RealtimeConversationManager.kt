package com.silverlink.app.feature.chat.realtime

import android.util.Log
import com.silverlink.app.data.model.Emotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 实时对话管理器
 * 
 * 回声消除策略：
 * 1. 使用共享的 audioSessionId (由 SharedAudioSession 管理) 启用硬件回声消除
 * 2. AI 播放期间使用更高的 RMS 阈值过滤低音量的回声
 * 3. 要求连续多帧检测到语音才算有效打断
 * 4. 播放开始后的冷却期内忽略所有语音检测
 */
class RealtimeConversationManager(
    private val vadEngine: WebRtcVadEngine,
    private val audioStream: PcmAudioStream,
    private val asrClient: StreamingAsrClientApi,
    private val silenceTimeoutMs: Long = 450,
    /** 打断时要求的最低 RMS 音量阈值（比原来的 1500 提高到 4000） */
    private val bargeInRmsThreshold: Int = 4000,
    /** 打断需要连续检测到语音的帧数 */
    private val consecutiveFramesRequired: Int = 3,
    /** 播放开始后的冷却期（毫秒），期间忽略所有语音检测 */
    private val playbackCooldownMs: Long = 500,
    private val onInterrupt: () -> Unit,
    private val onFinalText: (String, Emotion) -> Unit
) {
    companion object {
        private const val TAG = "RealtimeConversation"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var captureJob: Job? = null
    private val hasSpeech = AtomicBoolean(false)
    private var lastSpeechMs = 0L
    private val isSpeaking = AtomicBoolean(false)
    
    // 连续帧计数器
    private var consecutiveSpeechFrames = 0
    // 播放开始时间戳
    private var playbackStartTimeMs = 0L

    private val _state = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val state: StateFlow<ConversationState> = _state

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText

    private val _emotion = MutableStateFlow(Emotion.NEUTRAL)
    val emotion: StateFlow<Emotion> = _emotion

    fun start() {
        if (captureJob != null) return
        _state.value = ConversationState.Listening
        hasSpeech.set(false)
        consecutiveSpeechFrames = 0
        captureJob = scope.launch {
            audioStream.start(vadEngine.frameSizeSamples) { frame ->
                processAudioFrame(frame)
            }
        }
    }
    
    private fun processAudioFrame(frame: ShortArray) {
        val rms = calculateRms(frame)
        val vadSpeech = if (frame.size == vadEngine.frameSizeSamples) {
            vadEngine.isSpeech(frame)
        } else {
            false
        }
        
        // 判断是否有效语音
        val isSpeech = if (isSpeaking.get()) {
            // AI 正在播放时，需要更严格的检测
            val inCooldown = System.currentTimeMillis() - playbackStartTimeMs < playbackCooldownMs
            if (inCooldown) {
                // 冷却期内完全忽略语音检测
                false
            } else {
                // 冷却期后，需要 VAD + 高音量阈值
                vadSpeech && rms >= bargeInRmsThreshold
            }
        } else {
            // 正常监听模式，只需要 VAD 检测
            vadSpeech
        }
        
        // 连续帧确认逻辑（仅在 AI 播放期间启用）
        if (isSpeaking.get()) {
            if (isSpeech) {
                consecutiveSpeechFrames++
                Log.v(TAG, "Consecutive speech frames: $consecutiveSpeechFrames, rms: $rms")
            } else {
                consecutiveSpeechFrames = 0
            }
            
            // 未达到连续帧要求时不触发语音开始
            if (consecutiveSpeechFrames < consecutiveFramesRequired) {
                // 仍然推送帧到 ASR（如果已在识别中）
                if (hasSpeech.get()) {
                    asrClient.pushFrame(frame, false)
                }
                return
            }
        }
        
        if (isSpeech) {
            if (!hasSpeech.get()) {
                hasSpeech.set(true)
                lastSpeechMs = System.currentTimeMillis()
                onSpeechStart()
            } else {
                lastSpeechMs = System.currentTimeMillis()
            }
            asrClient.pushFrame(frame, true)
        } else if (hasSpeech.get()) {
            val idle = System.currentTimeMillis() - lastSpeechMs
            if (idle > silenceTimeoutMs) {
                onSpeechEnd()
            }
            asrClient.pushFrame(frame, false)
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        audioStream.stop()
        asrClient.stop()
        _state.value = ConversationState.Idle
        consecutiveSpeechFrames = 0
    }

    fun onPlaybackStarted() {
        isSpeaking.set(true)
        playbackStartTimeMs = System.currentTimeMillis()
        consecutiveSpeechFrames = 0
        _state.value = ConversationState.Speaking
        Log.d(TAG, "Playback started, enabling barge-in protection")
    }

    fun onPlaybackFinished() {
        isSpeaking.set(false)
        consecutiveSpeechFrames = 0
        if (_state.value == ConversationState.Speaking) {
            _state.value = ConversationState.Listening
        }
        Log.d(TAG, "Playback finished, disabling barge-in protection")
    }

    private fun onSpeechStart() {
        Log.d(TAG, "VAD speech start")
        if (isSpeaking.get()) {
            Log.i(TAG, "User barge-in detected, interrupting playback")
            _state.value = ConversationState.Interrupted
            onInterrupt()
        }
        _state.value = ConversationState.Listening
        asrClient.start()
    }

    private fun onSpeechEnd() {
        Log.d(TAG, "VAD speech end")
        hasSpeech.set(false)
        consecutiveSpeechFrames = 0
        _state.value = ConversationState.Processing
        asrClient.finish()
    }

    fun handleAsrPartial(text: String) {
        _partialText.value = text
    }

    fun handleAsrFinal(text: String, emotion: Emotion) {
        _partialText.value = ""
        _emotion.value = emotion
        if (text.isBlank()) {
            _state.value = ConversationState.Listening
            return
        }
        _state.value = ConversationState.Processing
        onFinalText(text, emotion)
    }

    fun release() {
        captureJob?.cancel()
        scope.cancel()
        vadEngine.close()
    }

    private fun calculateRms(frame: ShortArray): Int {
        if (frame.isEmpty()) return 0
        var sum = 0.0
        frame.forEach { sample ->
            val value = sample.toInt()
            sum += value * value
        }
        return kotlin.math.sqrt(sum / frame.size).toInt()
    }
}
