package com.silverlink.app.feature.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * 音频录制器 - 使用 MediaRecorder 录制音频
 */
class AudioRecorder(private val context: Context) {
    
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    companion object {
        private const val TAG = "AudioRecorder"
    }

    /**
     * 开始录音
     * @return 录音文件路径，如果启动失败返回 null
     */
    fun startRecording(): String? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return outputFile?.absolutePath
        }

        try {
            // 创建输出文件
            val fileName = "voice_${System.currentTimeMillis()}.m4a"
            outputFile = File(context.cacheDir, fileName)

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)  // 16kHz 采样率
                setAudioEncodingBitRate(64000)  // 64kbps
                setOutputFile(outputFile!!.absolutePath)
                
                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Recording started: ${outputFile!!.absolutePath}")
            return outputFile!!.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            releaseRecorder()
            return null
        }
    }

    /**
     * 停止录音
     * @return 录音文件路径，如果停止失败返回 null
     */
    fun stopRecording(): String? {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return null
        }

        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false

            val file = outputFile
            Log.d(TAG, "Recording stopped: ${file?.absolutePath}, size: ${file?.length()} bytes")
            return file?.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            releaseRecorder()
            return null
        }
    }

    /**
     * 取消录音（删除文件）
     */
    fun cancelRecording() {
        releaseRecorder()
        outputFile?.delete()
        outputFile = null
    }

    /**
     * 是否正在录音
     */
    fun isRecording(): Boolean = isRecording

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release recorder", e)
        }
        recorder = null
        isRecording = false
    }
}
