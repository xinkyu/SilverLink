package com.silverlink.app.feature.chat

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.silverlink.app.feature.chat.realtime.PcmAudioPlayer
import com.silverlink.app.feature.chat.realtime.SharedAudioSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 音频播放辅助类
 * 用于播放 TTS 合成的音频数据
 * 
 * 支持两种模式：
 * 1. 共享会话模式（默认）：使用 PcmAudioPlayer + 共享 audioSessionId，支持硬件回声消除
 * 2. 传统模式：使用 MediaPlayer，用于非实时对话场景
 */
class AudioPlayerHelper(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayerHelper"
        private const val TEMP_AUDIO_FILE = "tts_audio_temp.mp3"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var pcmPlayer: PcmAudioPlayer? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    
    // 是否使用共享会话模式（用于实时对话的回声消除）
    private var useSharedSession = true

    /**
     * 是否正在播放
     */
    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true || pcmPlayer != null

    /**
     * 设置是否使用共享会话模式
     * @param enabled true 使用 PcmAudioPlayer（支持回声消除），false 使用 MediaPlayer
     */
    fun setUseSharedSession(enabled: Boolean) {
        useSharedSession = enabled
        Log.d(TAG, "Shared session mode: $enabled")
    }

    /**
     * 播放音频数据
     * @param audioData MP3 格式的音频数据
     */
    suspend fun play(audioData: ByteArray) {
        if (useSharedSession) {
            playWithSharedSession(audioData)
        } else {
            playWithMediaPlayer(audioData)
        }
    }
    
    /**
     * 使用共享音频会话播放（支持回声消除）
     */
    private suspend fun playWithSharedSession(audioData: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                stop()
                
                val sessionId = SharedAudioSession.getSessionId()
                Log.d(TAG, "Playing with shared session ID: $sessionId")
                
                pcmPlayer = PcmAudioPlayer(sessionId).apply {
                    onPlaybackStarted = {
                        Log.d(TAG, "PCM playback started")
                    }
                    onPlaybackFinished = {
                        Log.d(TAG, "PCM playback finished")
                        pcmPlayer = null
                        onCompletionListener?.invoke()
                    }
                    onError = { error ->
                        Log.e(TAG, "PCM playback error: $error")
                        pcmPlayer = null
                        onErrorListener?.invoke(error)
                    }
                }
                
                pcmPlayer?.play(audioData, context.cacheDir)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error playing with shared session", e)
                withContext(Dispatchers.Main) {
                    onErrorListener?.invoke(e.message ?: "播放失败")
                }
            }
        }
    }
    
    /**
     * 使用 MediaPlayer 播放（传统模式）
     */
    private suspend fun playWithMediaPlayer(audioData: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                // 停止当前播放
                stop()

                // 将音频数据写入临时文件
                val tempFile = File(context.cacheDir, TEMP_AUDIO_FILE)
                FileOutputStream(tempFile).use { fos ->
                    fos.write(audioData)
                }

                // 在主线程创建并启动 MediaPlayer
                withContext(Dispatchers.Main) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        setOnCompletionListener {
                            Log.d(TAG, "Playback completed")
                            release()
                            mediaPlayer = null
                            onCompletionListener?.invoke()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                            release()
                            mediaPlayer = null
                            onErrorListener?.invoke("播放错误: $what")
                            true
                        }
                        prepare()
                        start()
                        Log.d(TAG, "Started playing audio")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio", e)
                withContext(Dispatchers.Main) {
                    onErrorListener?.invoke(e.message ?: "播放失败")
                }
            }
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        // 停止 PCM 播放器
        try {
            pcmPlayer?.stop()
            pcmPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping PCM player", e)
        }
        
        // 停止 MediaPlayer
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            Log.d(TAG, "Stopped playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    /**
     * 设置播放完成监听器
     */
    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }

    /**
     * 设置错误监听器
     */
    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()
        onCompletionListener = null
        onErrorListener = null
    }
}
