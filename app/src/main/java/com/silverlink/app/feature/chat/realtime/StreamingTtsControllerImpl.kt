package com.silverlink.app.feature.chat.realtime

import com.silverlink.app.data.model.Emotion
import com.silverlink.app.feature.chat.AudioPlayerHelper
import com.silverlink.app.feature.chat.TextToSpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StreamingTtsControllerImpl(
    private val ttsService: TextToSpeechService,
    private val audioPlayer: AudioPlayerHelper,
    private val scope: CoroutineScope,
    private val onPlaybackState: (Boolean) -> Unit,
    private val emotionProvider: () -> Emotion,
    private val dialectProvider: () -> String,
    private val rateProvider: () -> Double
) {
    private var ttsJob: Job? = null

    fun requestReply(text: String) {
        ttsJob?.cancel()
        ttsJob = scope.launch(Dispatchers.IO) {
            val emotion = emotionProvider()
            val rate = rateProvider()
            val dialect = dialectProvider()
            val result = ttsService.synthesize(text, rate, dialect, emotion)
            result.fold(
                onSuccess = { audioBytes ->
                    playAudio(audioBytes)
                },
                onFailure = {
                    onPlaybackState(false)
                }
            )
        }
    }

    fun playAudio(audioBytes: ByteArray) {
        scope.launch {
            onPlaybackState(true)
            audioPlayer.play(audioBytes)
            onPlaybackState(false)
        }
    }

    fun cancelPlayback() {
        ttsJob?.cancel()
        audioPlayer.stop()
        onPlaybackState(false)
    }
}
