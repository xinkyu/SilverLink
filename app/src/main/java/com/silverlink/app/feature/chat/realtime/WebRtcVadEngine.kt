package com.silverlink.app.feature.chat.realtime

import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate

class WebRtcVadEngine(
    sampleRate: SampleRate = SampleRate.SAMPLE_RATE_16K,
    frameSize: FrameSize = FrameSize.FRAME_SIZE_320,
    mode: Mode = Mode.VERY_AGGRESSIVE,
    private val speechDurationMs: Int = 60,
    private val silenceDurationMs: Int = 450
) : AutoCloseable {
    val frameSizeSamples: Int = frameSize.value
    val sampleRateHz: Int = sampleRate.value

    private val vad = VadWebRTC(
        sampleRate = sampleRate,
        frameSize = frameSize,
        mode = mode,
        silenceDurationMs = silenceDurationMs,
        speechDurationMs = speechDurationMs
    )

    fun isSpeech(frame: ShortArray): Boolean {
        return vad.isSpeech(frame)
    }

    override fun close() {
        vad.close()
    }
}
