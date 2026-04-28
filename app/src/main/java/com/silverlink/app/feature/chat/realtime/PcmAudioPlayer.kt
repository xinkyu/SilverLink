package com.silverlink.app.feature.chat.realtime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 基于 AudioTrack 的音频播放器
 * 
 * 使用共享的 audioSessionId，使得 AcousticEchoCanceler 能够
 * 识别播放的音频并从麦克风输入中消除回声。
 * 
 * 工作流程：
 * 1. 将 MP3 数据写入临时文件
 * 2. 使用 MediaExtractor + MediaCodec 解码为 PCM
 * 3. 使用 AudioTrack（共享 sessionId）播放 PCM
 */
class PcmAudioPlayer(
    private val audioSessionId: Int,
    private val sampleRate: Int = 22050,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO
) {
    companion object {
        private const val TAG = "PcmAudioPlayer"
        private const val TEMP_FILE_NAME = "tts_decode_temp.mp3"
    }
    
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    
    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackFinished: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    /**
     * 播放 MP3 音频数据
     */
    suspend fun play(mp3Data: ByteArray, cacheDir: File) = withContext(Dispatchers.IO) {
        try {
            stop()
            
            Log.d(TAG, "Starting playback, mp3 size: ${mp3Data.size}, sessionId: $audioSessionId")
            
            // 写入临时文件
            val tempFile = File(cacheDir, TEMP_FILE_NAME)
            FileOutputStream(tempFile).use { it.write(mp3Data) }
            
            // 解码并播放
            decodeAndPlay(tempFile)
            
            // 清理临时文件
            tempFile.delete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Playback failed", e)
            withContext(Dispatchers.Main) {
                onError?.invoke(e.message ?: "播放失败")
                onPlaybackFinished?.invoke()
            }
        }
    }
    
    private suspend fun decodeAndPlay(mp3File: File) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        
        try {
            extractor.setDataSource(mp3File.absolutePath)
            
            // 查找音频轨道
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }
            
            if (audioTrackIndex < 0 || format == null) {
                throw IllegalArgumentException("No audio track found in file")
            }
            
            extractor.selectTrack(audioTrackIndex)
            
            // 获取音频参数
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
            val decodedSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            Log.d(TAG, "Audio format: $mime, sampleRate: $decodedSampleRate, channels: $channelCount")
            
            // 创建解码器
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            
            // 创建 AudioTrack
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(decodedSampleRate)
                .setChannelMask(if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            
            val minBufferSize = AudioTrack.getMinBufferSize(
                decodedSampleRate,
                if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize * 2)
                .setSessionId(audioSessionId)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
            isPlaying = true
            
            withContext(Dispatchers.Main) {
                onPlaybackStarted?.invoke()
            }
            
            // 解码循环
            val bufferInfo = MediaCodec.BufferInfo()
            var isEndOfStream = false
            
            while (!isEndOfStream && isPlaying) {
                // 输入数据到解码器
                val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEndOfStream = true
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }
                
                // 从解码器获取输出并播放
                var outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0 && isPlaying) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val pcmData = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcmData)
                        outputBuffer.clear()
                        
                        audioTrack?.write(pcmData, 0, pcmData.size)
                    }
                    
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isEndOfStream = true
                        break
                    }
                    
                    outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            }
            
            Log.d(TAG, "Playback completed")
            
        } finally {
            decoder?.stop()
            decoder?.release()
            extractor.release()
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            isPlaying = false
            
            withContext(Dispatchers.Main) {
                onPlaybackFinished?.invoke()
            }
        }
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        isPlaying = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping playback", e)
        } finally {
            audioTrack = null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        onPlaybackStarted = null
        onPlaybackFinished = null
        onError = null
    }
}
