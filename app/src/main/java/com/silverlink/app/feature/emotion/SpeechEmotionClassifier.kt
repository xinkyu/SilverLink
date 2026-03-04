package com.silverlink.app.feature.emotion

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Speech emotion classifier using DistilHuBERT ONNX model (8-bit quantized).
 * Classifies audio waveforms into 4 emotions:
 * anger, happiness, neutral, sadness
 */
class SpeechEmotionClassifier {

    companion object {
        private const val TAG = "SpeechEmotionClassifier"
        private const val MODEL_PATH = "models/emotion_model_mobile.onnx"
    }

    val labels = listOf("anger", "happiness", "neutral", "sadness")

    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    fun initialize(context: Context) {
        Log.d(TAG, "Loading speech emotion model...")
        val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }
        session = environment.createSession(modelBytes, sessionOptions)
        Log.d(TAG, "Speech emotion model loaded successfully")
    }

    /**
     * Classify a preprocessed audio waveform and return probabilities for each emotion.
     * @param waveform Float array of preprocessed audio samples (16kHz, 8 seconds = 128000 samples)
     */
    fun classify(waveform: FloatArray): List<Float> {
        val ortSession = session ?: throw IllegalStateException("Model not initialized")

        val shape = longArrayOf(1, waveform.size.toLong())
        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(waveform),
            shape
        )

        val inputs = mapOf("input_values" to tensor)
        val results = ortSession.run(inputs)

        // Extract logits - shape [1, 4]
        @Suppress("UNCHECKED_CAST")
        val logits = (results[0].value as Array<FloatArray>)[0]

        tensor.close()
        results.close()

        return softmax(logits)
    }

    fun close() {
        session?.close()
        session = null
    }

    private fun softmax(logits: FloatArray): List<Float> {
        val maxLogit = logits.max()
        val exps = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }
    }
}
