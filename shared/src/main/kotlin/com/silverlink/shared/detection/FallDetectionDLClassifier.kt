package com.silverlink.shared.detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * 基于 ONNX Runtime 的深度学习跌倒分类器
 *
 * 使用 1D-CNN 模型直接从原始加速度序列推理跌倒概率，
 * 替代基于手工特征的规则评分方案。
 *
 * @param modelBytes ONNX 模型文件的字节数组（从 assets 加载）
 * @param logger     可选的日志回调
 */
class FallDetectionDLClassifier(
    modelBytes: ByteArray,
    private val logger: Logger? = null
) {

    fun interface Logger {
        fun log(tag: String, message: String)
    }

    companion object {
        private const val TAG = "FallDLClassifier"

        /** 模型输入维度 */
        private const val CHANNELS = 4       // x, y, z, magnitude
        private const val SEQ_LENGTH = 100   // 2秒 @ 50Hz

        /** 跌倒概率阈值（提高以减少误报，正常拿手机不会触发） */
        const val FALL_PROBABILITY_THRESHOLD = 0.65f

        /** 快速预筛选阈值（提高以过滤日常动作） */
        private const val PRESCREEN_MIN_MAG_MAX = 15.0f   // 约1.5G，排除拿手机等日常动作
        private const val PRESCREEN_MIN_MAG_RANGE = 10.0f  // 需要明显的加速度波动
    }

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = ortEnv.createSession(modelBytes)

    init {
        logger?.log(TAG, "ONNX model loaded, inputs=${session.inputNames}, outputs=${session.outputNames}")
    }

    /**
     * 分类结果（与现有接口兼容）
     */
    data class ClassificationResult(
        val fallProbability: Float,
        val confidence: Float,
        val detectedPatterns: List<String>
    )

    /**
     * 使用 ONNX 模型推理跌倒概率
     *
     * @param rawSequence 来自 AccelerometerBuffer.getRawSequence() 的原始数据
     *                    FloatArray[4 * 100]，通道优先排列
     * @return ClassificationResult 包含跌倒概率
     */
    fun classify(rawSequence: FloatArray): ClassificationResult {
        val patterns = mutableListOf<String>()

        // 将 flat array [4*100] reshape 为 [1, 4, 100] 的 tensor
        val inputShape = longArrayOf(1, CHANNELS.toLong(), SEQ_LENGTH.toLong())
        val floatBuffer = FloatBuffer.wrap(rawSequence)
        val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, inputShape)

        val probability: Float
        try {
            val results = session.run(mapOf(session.inputNames.first() to inputTensor))
            val output = results[0].value

            probability = when (output) {
                is Array<*> -> {
                    // [batch, 1] -> float
                    val row = output[0]
                    when (row) {
                        is FloatArray -> row[0]
                        else -> 0f
                    }
                }
                is FloatArray -> output[0]
                else -> {
                    logger?.log(TAG, "Unexpected output type: ${output?.javaClass}")
                    0f
                }
            }

            results.close()
        } finally {
            inputTensor.close()
        }

        if (probability >= FALL_PROBABILITY_THRESHOLD) {
            patterns.add("DL模型检测到跌倒")
        }

        logger?.log(TAG, "DL inference: probability=${String.format("%.4f", probability)}")

        return ClassificationResult(
            fallProbability = probability,
            confidence = if (probability > 0.8f || probability < 0.2f) 0.9f else 0.6f,
            detectedPatterns = patterns
        )
    }

    /**
     * 快速预筛选（基于幅度范围，减少不必要的推理调用）
     *
     * 在调用完整的 ONNX 推理之前，先检查缓冲区统计特征。
     * 如果加速度数据非常平稳（无异常波动），则直接跳过推理。
     */
    fun quickPrescreen(features: AccelFeatures): Boolean {
        if (features.magMax < PRESCREEN_MIN_MAG_MAX) return false
        if (features.magRange < PRESCREEN_MIN_MAG_RANGE) return false
        return true
    }

    /**
     * 释放 ONNX 资源
     */
    fun close() {
        try {
            session.close()
            logger?.log(TAG, "ONNX session closed")
        } catch (e: Exception) {
            logger?.log(TAG, "Error closing session: ${e.message}")
        }
    }
}
