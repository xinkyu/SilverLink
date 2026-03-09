package com.silverlink.app.feature.emotion

import android.content.Context
import android.util.Log
import com.silverlink.app.data.model.Emotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton facade for on-device cross-modal emotion recognition.
 *
 * Uses MemoCMT architecture (DistilBERT + DistilHuBERT with cross-attention fusion)
 * to jointly analyze text and audio for emotion classification.
 *
 * Cross-modal pipeline:
 *   Chinese text → Qwen translation → English → WordPiece tokenize → token IDs
 *   Audio file → decode to PCM → 16kHz mono waveform
 *   → MemoCMT ONNX (cross-attention fusion) → 4 classes → mapped to Emotion
 *
 * Text-only pipeline (no audio available):
 *   Chinese text → Qwen translation → English → WordPiece tokenize → token IDs
 *   + zero audio → MemoCMT ONNX → 4 classes → mapped to Emotion
 *
 * Speech-only pipeline (no text available):
 *   Audio file → decode to PCM → 16kHz mono waveform
 *   + empty text tokens → MemoCMT ONNX → 4 classes → mapped to Emotion
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
            Log.d(TAG, "Initializing MemoCMT cross-modal emotion model...")
            tokenizer.initialize(context)
            classifier.initialize(context)
            isInitialized = true
            Log.d(TAG, "MemoCMT model initialized successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize MemoCMT model", e)
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

        // Text branch: translate + tokenize
        val englishText = translationService.translateToEnglish(chineseText)
        Log.d(TAG, "Text for classification: '$englishText'")
        val tokenIds = tokenizer.encode(englishText)
        logTokenStats("cross-modal", tokenIds)

        // Audio branch: decode + preprocess
        val audioWaveform = audioPreprocessor.processAudioFile(audioFilePath)

        // Cross-modal inference
        val result = classifier.classify(tokenIds, audioWaveform)
        Log.d(TAG, "MemoCMT cross-modal result: ${result.label} (${String.format("%.3f", result.confidence)}) | ${
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
    suspend fun analyzeTextEmotion(chineseText: String): Emotion = withContext(Dispatchers.IO) {
        check(isInitialized) { "EmotionRecognitionService not initialized" }

        val englishText = translationService.translateToEnglish(chineseText)
        Log.d(TAG, "Text for classification: '$englishText'")
        val tokenIds = tokenizer.encode(englishText)
        logTokenStats("text-only", tokenIds)

        // Zero audio (text-only mode)
        val zeroAudio = FloatArray(MemoCmtClassifier.AUDIO_MAX_LENGTH)

        val result = classifier.classify(tokenIds, zeroAudio)
        Log.d(TAG, "MemoCMT text-only result: ${result.label} (${String.format("%.3f", result.confidence)}) | ${
            result.allScores.entries.joinToString { "${it.key}=${String.format("%.3f", it.value)}" }
        }")

        val emotion = EmotionMapper.mapResult(result)
        Log.d(TAG, "Text emotion: ${emotion.name}")
        emotion
    }

    /**
     * Analyze emotion from audio only (text input is empty tokens).
     * Pipeline: decode audio → preprocess → MemoCMT ONNX (empty text + audio) → Emotion
     *
     * Fully offline - no network required.
     */
    suspend fun analyzeSpeechEmotion(audioFilePath: String): Emotion = withContext(Dispatchers.IO) {
        check(isInitialized) { "EmotionRecognitionService not initialized" }

        // Empty text tokens (CLS + SEP + padding)
        val emptyTokenIds = LongArray(MemoCmtClassifier.TEXT_MAX_LENGTH).apply {
            this[0] = 101L  // [CLS]
            this[1] = 102L  // [SEP]
        }

        val audioWaveform = audioPreprocessor.processAudioFile(audioFilePath)

        val result = classifier.classify(emptyTokenIds, audioWaveform)
        Log.d(TAG, "MemoCMT speech-only result: ${result.label} (${String.format("%.3f", result.confidence)}) | ${
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
        Log.d(TAG, "MemoCMT model released")
    }
}
