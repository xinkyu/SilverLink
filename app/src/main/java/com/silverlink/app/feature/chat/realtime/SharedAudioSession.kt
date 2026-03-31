package com.silverlink.app.feature.chat.realtime

import android.media.AudioManager
import android.util.Log

/**
 * 共享音频会话管理器
 * 
 * 用于让 AudioRecord 和 AudioTrack 使用相同的 audioSessionId，
 * 这样 AcousticEchoCanceler 才能正确识别并消除回声。
 */
object SharedAudioSession {
    private const val TAG = "SharedAudioSession"
    
    @Volatile
    private var sessionId: Int = 0
    
    /**
     * 设置共享的音频会话 ID (通常由 AudioRecord 生成后设置)
     */
    @Synchronized
    fun setSessionId(id: Int) {
        if (sessionId == 0 && id != 0) {
            sessionId = id
            Log.d(TAG, "Set shared audio session ID: $sessionId")
        }
    }

    /**
     * 获取共享的音频会话 ID
     */
    @Synchronized
    fun getSessionId(): Int {
        return sessionId
    }
    
    /**
     * 重置会话 ID（在完全停止音频处理时调用）
     */
    @Synchronized
    fun reset() {
        sessionId = 0
        Log.d(TAG, "Reset shared audio session")
    }
}
