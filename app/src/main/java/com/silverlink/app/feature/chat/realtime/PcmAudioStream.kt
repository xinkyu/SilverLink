package com.silverlink.app.feature.chat.realtime

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class PcmAudioStream(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    companion object {
        private const val TAG = "PcmAudioStream"
    }

    private var audioRecord: AudioRecord? = null
    private var isRunning = AtomicBoolean(false)
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    fun start(frameSizeInSamples: Int, onFrame: (ShortArray) -> Unit) {
        if (isRunning.get()) return

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val frameSizeBytes = frameSizeInSamples * 2
        val bufferSize = (minBufferSize * 2).coerceAtLeast(frameSizeBytes * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                val sessionId = audioRecord?.audioSessionId ?: 0
                if (sessionId != 0) {
                    SharedAudioSession.setSessionId(sessionId)
                    Log.d(TAG, "Initialized AudioRecord with session ID: $sessionId")
                }
            } else {
                Log.e(TAG, "AudioRecord init failed")
                audioRecord?.release()
                audioRecord = null
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioRecord", e)
            return
        }

        setupAudioEffects(audioRecord)

        isRunning.set(true)
        audioRecord?.startRecording()

        val frameBuffer = ShortArray(frameSizeInSamples)
        while (isRunning.get()) {
            val read = audioRecord?.read(frameBuffer, 0, frameBuffer.size) ?: 0
            if (read > 0) {
                if (read == frameBuffer.size) {
                    onFrame(frameBuffer.copyOf())
                } else {
                    onFrame(frameBuffer.copyOfRange(0, read))
                }
            } else if (read < 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.w(TAG, "AudioRecord read error: $read")
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord stop failed", e)
        } finally {
            releaseAudioEffects()
            audioRecord?.release()
            audioRecord = null
        }
    }

    private fun setupAudioEffects(record: AudioRecord?) {
        val sessionId = record?.audioSessionId ?: return
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
        }
        if (AutomaticGainControl.isAvailable()) {
            gainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
        }
    }

    private fun releaseAudioEffects() {
        echoCanceler?.release()
        noiseSuppressor?.release()
        gainControl?.release()
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
    }
}
