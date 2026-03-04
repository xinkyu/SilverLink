package com.silverlink.app.feature.emotion

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import kotlin.math.exp

/**
 * Text emotion classifier using DistilRoBERTa ONNX model.
 * Classifies English text into 7 emotions:
 * anger, disgust, fear, joy, neutral, sadness, surprise
 */
class TextEmotionClassifier {

    companion object {
        private const val TAG = "TextEmotionClassifier"
        private const val MODEL_PATH = "models/emotionEnglishDistilrobertaBase.onnx"
    }

    val labels = listOf("anger", "disgust", "fear", "joy", "neutral", "sadness", "surprise")

    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    fun initialize(context: Context) {
        Log.d(TAG, "Loading text emotion model...")
        val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }
        session = environment.createSession(modelBytes, sessionOptions)
        Log.d(TAG, "Text emotion model loaded successfully")
    }

    /**
     * Classify tokenized text and return probabilities for each emotion.
     */
    fun classify(tokenizerOutput: TokenizerOutput): List<Float> {
        val ortSession = session ?: throw IllegalStateException("Model not initialized")

        val seqLen = tokenizerOutput.inputIds.size.toLong()
        val shape = longArrayOf(1, seqLen)

        val inputIdsTensor = OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(tokenizerOutput.inputIds),
            shape
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(tokenizerOutput.attentionMask),
            shape
        )

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        val results = ortSession.run(inputs)

        // Extract logits - shape [1, 7]
        @Suppress("UNCHECKED_CAST")
        val logits = (results[0].value as Array<FloatArray>)[0]

        inputIdsTensor.close()
        attentionMaskTensor.close()
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
