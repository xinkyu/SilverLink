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
 * SER classifier using BERT + VGGish mel-spectrogram ONNX model.
 * Inputs: input_ids [batch, seq], attention_mask [batch, seq], mel_spectrogram [batch, 1, 96, 64]
 * Classifies 4 emotions: Angry, Happy, Sad, Neutral
 */
class MemoCmtClassifier {

    companion object {
        private const val TAG = "MemoCmtClassifier"
        private const val MODEL_ASSET = "models/4m_ser_bert_vggish_fp16.onnx"
        private const val DATA_ASSET  = "models/4m_ser_bert_vggish_fp16.onnx.data"
        private const val MODEL_FILE  = "4m_ser_bert_vggish_fp16.onnx"
        private const val DATA_FILE   = "4m_ser_bert_vggish_fp16.onnx.data"

        /** VGGish mel spectrogram dimensions */
        const val MEL_FRAMES = 96
        const val MEL_BINS = 64

        val LABELS = arrayOf("Angry", "Happy", "Sad", "Neutral")
    }

    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var ioMetadataLogged = false

    fun initialize(context: Context) {
        Log.d(TAG, "Loading BERT+VGGish emotion model...")
        // .onnx and .data must live side-by-side in the same directory
        val modelFile = File(context.filesDir, MODEL_FILE)
        val dataFile  = File(context.filesDir, DATA_FILE)

        copyAssetIfNeeded(context, MODEL_ASSET, modelFile)
        copyAssetIfNeeded(context, DATA_ASSET,  dataFile)

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }
        session = environment.createSession(modelFile.absolutePath, sessionOptions)
        Log.d(TAG, "BERT+VGGish model loaded successfully")
    }

    private fun copyAssetIfNeeded(context: Context, assetPath: String, destFile: File) {
        val assetSize = context.assets.open(assetPath).use { it.available().toLong() }
        if (!destFile.exists() || destFile.length() != assetSize) {
            Log.d(TAG, "Copying $assetPath → ${destFile.name} ($assetSize bytes)...")
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }
            Log.d(TAG, "Copied ${destFile.name}: ${destFile.length()} bytes")
        } else {
            Log.d(TAG, "${destFile.name} already cached (${destFile.length()} bytes)")
        }
    }

    /**
     * Emotion classification using BERT text inputs and VGGish mel spectrogram.
     *
     * @param tokenIds      BERT token IDs (variable length, includes [CLS]/[SEP]/padding)
     * @param attentionMask BERT attention mask (1 = real token, 0 = padding), same length as tokenIds
     * @param melSpectrogram VGGish mel spectrogram, flattened [MEL_FRAMES * MEL_BINS] = 6144 floats
     * @return EmotionResult with label, confidence, and all scores
     */
    fun classify(tokenIds: LongArray, attentionMask: LongArray, melSpectrogram: FloatArray): EmotionResult {
        val ortSession = session ?: throw IllegalStateException("Model not initialized")

        if (!ioMetadataLogged) {
            ioMetadataLogged = true
            val inputInfo = ortSession.inputInfo.keys.joinToString(", ")
            val outputInfo = ortSession.outputInfo.keys.joinToString(", ")
            Log.d(TAG, "Session inputs: [$inputInfo]")
            Log.d(TAG, "Session outputs: [$outputInfo]")
        }

        // Text inputs: [1, seqLen] int64
        val inputIdsTensor = createLongTensor(tokenIds)
        val attentionMaskTensor = createLongTensor(attentionMask)

        // Audio input: [1, 1, MEL_FRAMES, MEL_BINS] float32
        val melTensor = OnnxTensor.createTensor(
            environment,
            createDirectFloatBuffer(melSpectrogram),
            longArrayOf(1L, 1L, MEL_FRAMES.toLong(), MEL_BINS.toLong())
        )

        Log.d(TAG, "Input ids head(10): ${tokenIds.take(10)}")
        Log.d(
            TAG,
            "Mel spectrogram head(8): ${melSpectrogram.take(8).joinToString(prefix = "[", postfix = "]") { String.format("%.6f", it) }}"
        )

        val inputMap = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "mel_spectrogram" to melTensor
        )
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

        inputIdsTensor.close()
        attentionMaskTensor.close()
        melTensor.close()
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

    private fun createDirectFloatBuffer(values: FloatArray): FloatBuffer {
        val byteBuffer = ByteBuffer
            .allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(values)
        floatBuffer.rewind()
        return floatBuffer
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
