package com.silverlink.app.feature.emotion

import android.content.Context
import android.util.Log
import com.silverlink.app.data.model.Emotion
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton facade for on-device multimodal emotion recognition.
 *
 * Uses BERT + VGGish mel spectrogram ONNX model to jointly analyze text and audio.
 *
 * Cross-modal pipeline:
 *   Chinese text → Qwen translation → English → WordPiece tokenize → token IDs + attention mask
 *   Audio file → decode to PCM → VGGish mel spectrogram
 *   → ONNX (BERT + VGGish) → 4 classes → mapped to Emotion
 *
 * Text-only pipeline (no audio available):
 *   Chinese text → Qwen translation → English → WordPiece tokenize → token IDs + attention mask
 *   + zero mel spectrogram → ONNX → 4 classes → mapped to Emotion
 *
 * Speech-only pipeline (no text available):
 *   Audio file → decode to PCM → VGGish mel spectrogram
 *   + empty text tokens → ONNX → 4 classes → mapped to Emotion
 */
class EmotionRecognitionService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EmotionRecognitionSvc"

        @Volatile
        private var instance: EmotionRecognitionService? = null

        fun getInstance(context: Context): EmotionRecognitionService {
            return instance ?: synchronized(this) {
                instance ?: EmotionRecognitionService(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val classifier = MemoCmtClassifier()
    private val tokenizer = WordPieceTokenizer()
    private val audioPreprocessor = AudioPreprocessor()
    private val translationService = TranslationService()

    private fun logTokenStats(mode: String, tokenIds: LongArray) {
        val nonPad = tokenIds.count { it != 0L }
        val unk = tokenIds.count { it == 100L }
        val head = tokenIds.take(16).joinToString(prefix = "[", postfix = "]")
        Log.d(TAG, "$mode token stats: nonPad=$nonPad, unk=$unk, head=$head")
    }

    @Volatile
    private var isInitialized = false

    /**
     * Check if the model is initialized and ready for inference.
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Initialize ONNX model and tokenizer. Call from Application.onCreate() on a background thread.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            Log.d(TAG, "Initializing BERT+VGGish emotion model...")
            tokenizer.initialize(context)
            classifier.initialize(context)
            isInitialized = true
            Log.d(TAG, "Emotion model initialized successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize emotion model", e)
            throw e
        }
    }

    /**
     * Cross-modal emotion analysis: fuses text and audio for best accuracy.
     * Pipeline: translate text → tokenize + preprocess audio → MemoCMT ONNX → Emotion
     *
     * Note: Requires network for the translation step.
     */
    suspend fun analyzeCrossModal(chineseText: String, audioFilePath: String): Emotion = withContext(Dispatchers.IO) {
        check(isInitialized) { "EmotionRecognitionService not initialized" }

        // Text branch: translate + tokenize + attention mask
        val englishText = translationService.translateToEnglish(chineseText)
        Log.d(TAG, "Text for classification: '$englishText'")
        val (tokenIds, attentionMask) = tokenizer.encodeWithMask(englishText)
        logTokenStats("cross-modal", tokenIds)

        // Audio branch: decode + VGGish mel spectrogram
        val melSpectrogram = audioPreprocessor.processAudioToMelSpectrogram(audioFilePath)

        // Cross-modal inference
        val result = classifier.classify(tokenIds, attentionMask, melSpectrogram)
        Log.d(TAG, "Emotion result: ${result.label} (${String.format("%.3f", result.confidence)}) | ${
            result.allScores.entries.joinToString { "${it.key}=${String.format("%.3f", it.value)}" }
        }")

        val emotion = EmotionMapper.mapResult(result)
        Log.d(TAG, "Emotion: ${emotion.name}")
        emotion
    }

    /**
     * Analyze emotion from Chinese text only (audio input is zeroed).
     * Pipeline: translate → tokenize → MemoCMT ONNX (text + zero audio) → Emotion
     *
     * Note: Requires network for the translation step.
     */
    /**
     * Analyze emotion from Chinese text only, using Qwen API for classification.
     * The ONNX model requires real audio mel spectrogram, so text-only uses LLM instead.
     *
     * Note: Requires network.
     */
    suspend fun analyzeTextEmotion(chineseText: String): Emotion = withContext(Dispatchers.IO) {
        check(isInitialized) { "EmotionRecognitionService not initialized" }

        try {
            val request = QwenRequest(
                input = Input(
                    messages = listOf(
                        Message(
                            "system",
                            "Classify the emotion of the following Chinese text into exactly one of: " +
                                "Angry, Happy, Sad, Neutral. " +
                                "Output ONLY the single emotion word, nothing else."
                        ),
                        Message("user", chineseText)
                    )
                )
            )
            val response = RetrofitClient.api.chat(request)
            val raw = (response.output.choices?.firstOrNull()?.message?.content
                ?: response.output.text).orEmpty().trim()
            val label = when {
                raw.contains("angry", ignoreCase = true)   -> "Angry"
                raw.contains("happy", ignoreCase = true)   -> "Happy"
                raw.contains("sad",   ignoreCase = true)   -> "Sad"
                raw.contains("neutral", ignoreCase = true) -> "Neutral"
                else -> "Neutral"
            }
            Log.d(TAG, "Text emotion (Qwen): raw='$raw' → $label")
            EmotionMapper.mapLabel(label)
        } catch (e: Exception) {
            Log.e(TAG, "Text emotion Qwen call failed, defaulting to NEUTRAL", e)
            Emotion.NEUTRAL
        }
    }

    /**
     * Analyze emotion from audio only (text input is empty tokens).
     * Pipeline: decode audio → preprocess → MemoCMT ONNX (empty text + audio) → Emotion
     *
     * Fully offline - no network required.
     */
    suspend fun analyzeSpeechEmotion(audioFilePath: String): Emotion = withContext(Dispatchers.IO) {
        check(isInitialized) { "EmotionRecognitionService not initialized" }

        // Empty text tokens (CLS + SEP + padding) with attention mask = [1,1,0,...]
        val emptyTokenIds = LongArray(128).apply {
            this[0] = 101L  // [CLS]
            this[1] = 102L  // [SEP]
        }
        val emptyAttentionMask = LongArray(128).apply {
            this[0] = 1L
            this[1] = 1L
        }

        val melSpectrogram = audioPreprocessor.processAudioToMelSpectrogram(audioFilePath)

        val result = classifier.classify(emptyTokenIds, emptyAttentionMask, melSpectrogram)
        Log.d(TAG, "Speech-only result: ${result.label} (${String.format("%.3f", result.confidence)}) | ${
            result.allScores.entries.joinToString { "${it.key}=${String.format("%.3f", it.value)}" }
        }")

        val emotion = EmotionMapper.mapResult(result)
        Log.d(TAG, "Speech emotion: ${emotion.name}")
        emotion
    }

    /**
     * Release ONNX session and free resources.
     */
    fun release() {
        classifier.close()
        isInitialized = false
        Log.d(TAG, "Emotion model released")
    }
}
