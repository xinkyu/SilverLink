package com.silverlink.app.feature.emotion

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp

/**
 * MemoCMT cross-modal emotion classifier using DistilBERT + DistilHuBERT ONNX model.
 * Fuses text and audio modalities via cross-attention to classify 4 emotions:
 * Angry, Happy, Sad, Neutral
 *
 * Based on paper: s41598-025-89202-x (MemoCMT: Cross-Modal Transformer for Multimodal Emotion Recognition)
 */
class MemoCmtClassifier {

    companion object {
        private const val TAG = "MemoCmtClassifier"
        private const val MODEL_ASSET = "models/model_mobile.onnx"
        private const val MODEL_FILE = "model_mobile.onnx"

        const val TEXT_MAX_LENGTH = 297
        const val AUDIO_MAX_LENGTH = 160220

        val LABELS = arrayOf("Angry", "Happy", "Sad", "Neutral")
    }

    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var ioMetadataLogged = false

    fun initialize(context: Context) {
        Log.d(TAG, "Loading MemoCMT cross-modal model...")
        Log.d(TAG, "Model config: asset=$MODEL_ASSET, cacheFile=$MODEL_FILE")
        // Copy model from assets to internal storage for memory-mapped loading
        val modelFile = File(context.filesDir, MODEL_FILE)
        val assetSize = context.assets.open(MODEL_ASSET).use { it.available().toLong() }
        Log.d(TAG, "Asset size: $assetSize bytes, cache path: ${modelFile.absolutePath}")
        if (!modelFile.exists() || modelFile.length() != assetSize) {
            Log.d(TAG, "Copying model to internal storage (asset=$assetSize, cached=${if (modelFile.exists()) modelFile.length() else "none"})...")
            context.assets.open(MODEL_ASSET).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            Log.d(TAG, "Model copied: ${modelFile.length()} bytes")
        }
        Log.d(TAG, "Cache file size after check: ${modelFile.length()} bytes")
        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }
        session = environment.createSession(modelFile.absolutePath, sessionOptions)
        Log.d(TAG, "MemoCMT model loaded successfully")
    }

    /**
     * Cross-modal emotion classification using both text and audio inputs.
     *
     * @param tokenIds DistilBERT WordPiece token IDs, padded to TEXT_MAX_LENGTH (297)
     * @param audioWaveform 16kHz mono float waveform, padded/truncated to AUDIO_MAX_LENGTH (160220)
     * @return EmotionResult with label, confidence, and all scores
     */
    fun classify(tokenIds: LongArray, audioWaveform: FloatArray): EmotionResult {
        val ortSession = session ?: throw IllegalStateException("Model not initialized")

        if (!ioMetadataLogged) {
            ioMetadataLogged = true
            val inputInfo = ortSession.inputInfo.keys.joinToString(", ")
            val outputInfo = ortSession.outputInfo.keys.joinToString(", ")
            Log.d(TAG, "Session inputs: [$inputInfo]")
            Log.d(TAG, "Session outputs: [$outputInfo]")
        }

        // Text input: [1, 297], int64 with explicit native-endian direct buffer.
        val textTensor = createLongTensor(tokenIds)

        // Audio input: [1, 160220], float32 with explicit native-endian direct buffer.
        val audioTensor = createFloatTensor(audioWaveform)

        Log.d(TAG, "Input text head(10): ${tokenIds.take(10)}")
        Log.d(
            TAG,
            "Input audio head(8): ${audioWaveform.take(8).joinToString(prefix = "[", postfix = "]") { String.format("%.6f", it) }}"
        )

        val inputMap = mapOf("input_text" to textTensor, "input_audio" to audioTensor)
        val outputNames = ortSession.outputInfo.keys
        val preferLogits = outputNames.firstOrNull { it.equals("logits", ignoreCase = true) }
        val results = if (preferLogits != null) {
            ortSession.run(inputMap, setOf(preferLogits))
        } else {
            ortSession.run(inputMap)
        }

        @Suppress("UNCHECKED_CAST")
        val logits = (results[0].value as Array<FloatArray>)[0]
        Log.d(
            TAG,
            "Raw logits: ${logits.joinToString(prefix = "[", postfix = "]") { String.format("%.6f", it) }}" +
                if (preferLogits != null) " (from output='$preferLogits')" else " (from output index 0)"
        )

        textTensor.close()
        audioTensor.close()
        results.close()

        val probabilities = softmax(logits)
        val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: 3

        return EmotionResult(
            label = LABELS[maxIdx],
            confidence = probabilities[maxIdx],
            allScores = LABELS.zip(probabilities.toList()).toMap()
        )
    }

    private fun createLongTensor(values: LongArray): OnnxTensor {
        val byteBuffer = ByteBuffer
            .allocateDirect(values.size * Long.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val longBuffer: LongBuffer = byteBuffer.asLongBuffer()
        longBuffer.put(values)
        longBuffer.rewind()
        return OnnxTensor.createTensor(
            environment,
            longBuffer,
            longArrayOf(1, values.size.toLong())
        )
    }

    private fun createFloatTensor(values: FloatArray): OnnxTensor {
        val byteBuffer = ByteBuffer
            .allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(values)
        floatBuffer.rewind()
        return OnnxTensor.createTensor(
            environment,
            floatBuffer,
            longArrayOf(1, values.size.toLong())
        )
    }

    fun close() {
        session?.close()
        session = null
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = logits.map { exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }
}

data class EmotionResult(
    val label: String,
    val confidence: Float,
    val allScores: Map<String, Float>
)
