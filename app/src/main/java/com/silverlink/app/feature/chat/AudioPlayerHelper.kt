package com.silverlink.app.feature.chat

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 音频播放辅助类
 * 用于播放 TTS 合成的音频数据
 */
class AudioPlayerHelper(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayerHelper"
        private const val TEMP_AUDIO_FILE = "tts_audio_temp.mp3"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    /**
     * 是否正在播放
     */
    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    /**
     * 播放音频数据
     * @param audioData MP3 格式的音频数据
     */
    suspend fun play(audioData: ByteArray) {
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
