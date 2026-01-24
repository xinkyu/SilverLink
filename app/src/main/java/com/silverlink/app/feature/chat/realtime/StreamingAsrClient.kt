package com.silverlink.app.feature.chat.realtime

import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.model.SpeechResult
import com.silverlink.app.feature.chat.SpeechRecognitionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StreamingAsrClient(
    private val buffer: RealtimeAudioSegmentBuffer,
    private val recognizer: SpeechRecognitionService,
    private val scope: CoroutineScope,
    private val onPartial: (String) -> Unit,
    private val onFinal: (SpeechResult) -> Unit
) : StreamingAsrClientApi {
    private var recognitionJob: Job? = null
    private var lastPartialMs = 0L

    override fun start() {
        buffer.reset()
        recognitionJob?.cancel()
    }

    override fun pushFrame(frame: ShortArray, isSpeech: Boolean) {
        if (isSpeech) {
            buffer.append(frame)
            val now = System.currentTimeMillis()
            if (now - lastPartialMs > 700) {
                lastPartialMs = now
                onPartial("...")
            }
        }
    }

    override fun finish() {
        if (recognitionJob != null) return
        recognitionJob = scope.launch(Dispatchers.IO) {
            val audioBytes = buffer.toPcmBytes()
            val result = recognizer.recognizePcm(audioBytes)
            result.fold(
                onSuccess = { speech ->
                    onFinal(speech)
                },
                onFailure = {
                    onFinal(SpeechResult(text = "", emotion = Emotion.NEUTRAL, confidence = 0f))
                }
            )
        }
    }

    override fun stop() {
        recognitionJob?.cancel()
        recognitionJob = null
        lastPartialMs = 0L
    }
}

interface StreamingAsrClientApi {
    fun start()
    fun pushFrame(frame: ShortArray, isSpeech: Boolean)
    fun finish()
    fun stop()
}
