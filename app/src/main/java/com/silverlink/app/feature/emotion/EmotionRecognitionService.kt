package com.silverlink.app.feature.emotion

import android.content.Context
import android.util.Log
import com.silverlink.app.data.model.Emotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton facade for on-device emotion recognition.
 *
 * Text emotion pipeline:
 *   Chinese text → Qwen translation → English → RoBERTa tokenizer → DistilRoBERTa ONNX → 7 classes → mapped to Emotion
 *
 * Speech emotion pipeline:
 *   M4A audio → decode to PCM → preprocess → DistilHuBERT ONNX → 4 classes → mapped to Emotion
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

    private val textClassifier = TextEmotionClassifier()
    private val speechClassifier = SpeechEmotionClassifier()
    private val tokenizer = RobertaTokenizer()
    private val audioPreprocessor = AudioPreprocessor()
    private val translationService = TranslationService()

    @Volatile
    private var isInitialized = false

    /**
     * Initialize ONNX models and tokenizer. Call from Application.onCreate() on a background thread.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            Log.d(TAG, "Initializing emotion recognition models...")
            tokenizer.initialize(context)
            textClassifier.initialize(context)
            speechClassifier.initialize(context)
            isInitialized = true
            Log.d(TAG, "Emotion recognition models initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize emotion recognition models", e)
            throw e
        }
    }

    /**
     * Analyze emotion from Chinese text.
     * Pipeline: translate to English → tokenize → DistilRoBERTa ONNX → map to Emotion
     *
     * Note: Requires network for the translation step.
     */
    suspend fun analyzeTextEmotion(chineseText: String): Emotion = withContext(Dispatchers.IO) {
        check(isInitialized) { "EmotionRecognitionService not initialized" }

        // Step 1: Translate Chinese to English
        val englishText = translationService.translateToEnglish(chineseText)
        Log.d(TAG, "Text for classification: '$englishText'")

        // Step 2: Tokenize
        val tokens = tokenizer.encode(englishText)

        // Step 3: ONNX inference
        val probabilities = textClassifier.classify(tokens)
        Log.d(TAG, "Text emotion probabilities: ${
            textClassifier.labels.zip(probabilities).joinToString { "${it.first}=${String.format("%.3f", it.second)}" }
        }")

        // Step 4: Map to app Emotion
        val emotion = EmotionMapper.mapTextEmotion(probabilities)
        Log.d(TAG, "Text emotion result: ${emotion.name}")
        emotion
    }

    /**
     * Analyze emotion from an audio file.
     * Pipeline: decode M4A → preprocess → DistilHuBERT ONNX → map to Emotion
     *
     * Fully offline - no network required.
     */
    suspend fun analyzeSpeechEmotion(audioFilePath: String): Emotion = withContext(Dispatchers.IO) {
        check(isInitialized) { "EmotionRecognitionService not initialized" }

        // Step 1: Preprocess audio
        val waveform = audioPreprocessor.processAudioFile(audioFilePath)

        // Step 2: ONNX inference
        val probabilities = speechClassifier.classify(waveform)
        Log.d(TAG, "Speech emotion probabilities: ${
            speechClassifier.labels.zip(probabilities).joinToString { "${it.first}=${String.format("%.3f", it.second)}" }
        }")

        // Step 3: Map to app Emotion
        val emotion = EmotionMapper.mapSpeechEmotion(probabilities)
        Log.d(TAG, "Speech emotion result: ${emotion.name}")
        emotion
    }

    /**
     * Release ONNX sessions and free resources.
     */
    fun release() {
        textClassifier.close()
        speechClassifier.close()
        isInitialized = false
        Log.d(TAG, "Emotion recognition models released")
    }
}
