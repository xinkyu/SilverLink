package com.silverlink.shared.detection

/**
 * 跌倒检测分类器
 *
 * 使用多层特征分析来判断是否发生跌倒：
 * 1. 统计特征分析（均值、方差、范围）
 * 2. 时序模式识别（自由落体→撞击→静止）
 * 3. 方向变化检测
 *
 * 返回跌倒概率 (0.0 - 1.0)
 */
class FallClassifier(private val logger: Logger? = null) {

    fun interface Logger {
        fun log(tag: String, message: String)
    }

    companion object {
        private const val TAG = "FallClassifier"
        private const val MIN_IMPACT_MAGNITUDE = 13.0f
        private const val MIN_FREE_FALL_SAMPLES = 1
        private const val MIN_MAG_RANGE = 8.0f
        private const val MAX_NORMAL_STD = 2.0f
        const val FALL_PROBABILITY_THRESHOLD = 0.40f
    }

    data class ClassificationResult(
        val fallProbability: Float,
        val confidence: Float,
        val detectedPatterns: List<String>
    )

    fun classify(features: AccelFeatures): ClassificationResult {
        val patterns = mutableListOf<String>()
        var score = 0f
        var maxScore = 0f

        // Feature 1: Magnitude range
        maxScore += 25f
        if (features.magRange > MIN_MAG_RANGE) {
            val rangeScore = minOf(25f, (features.magRange - MIN_MAG_RANGE) / 2f)
            score += rangeScore
            patterns.add("幅度范围异常: ${String.format("%.1f", features.magRange)}")
            logger?.log(TAG, "Range score: $rangeScore (range=${features.magRange})")
        }

        // Feature 2: Impact detection
        maxScore += 30f
        if (features.magMax > MIN_IMPACT_MAGNITUDE) {
            val impactScore = minOf(30f, (features.magMax - MIN_IMPACT_MAGNITUDE) / 1.5f)
            score += impactScore
            patterns.add("检测到撞击: ${String.format("%.1f", features.magMax)}m/s²")
            logger?.log(TAG, "Impact score: $impactScore (max=${features.magMax})")
        }

        // Feature 3: Free fall detection
        maxScore += 20f
        if (features.freeFallCount >= MIN_FREE_FALL_SAMPLES) {
            val fallScore = minOf(20f, features.freeFallCount * 3f)
            score += fallScore
            patterns.add("自由落体样本: ${features.freeFallCount}")
            logger?.log(TAG, "FreeFall score: $fallScore (count=${features.freeFallCount})")
        }

        // Feature 4: Standard deviation anomaly
        maxScore += 15f
        if (features.magStd > MAX_NORMAL_STD) {
            val stdScore = minOf(15f, (features.magStd - MAX_NORMAL_STD) * 2f)
            score += stdScore
            patterns.add("变化剧烈: std=${String.format("%.2f", features.magStd)}")
            logger?.log(TAG, "Std score: $stdScore (std=${features.magStd})")
        }

        // Feature 5: Temporal pattern validation
        maxScore += 10f
        if (hasTypicalFallPattern(features)) {
            score += 10f
            patterns.add("检测到典型跌倒模式")
            logger?.log(TAG, "Pattern bonus: +10")
        }

        val probability = score / maxScore
        val confidence = calculateConfidence(features)

        logger?.log(TAG, "Classification: score=$score/$maxScore, probability=$probability, confidence=$confidence")

        return ClassificationResult(
            fallProbability = probability,
            confidence = confidence,
            detectedPatterns = patterns
        )
    }

    private fun hasTypicalFallPattern(features: AccelFeatures): Boolean {
        val samples = features.recentMagnitudes
        if (samples.size < 5) return false

        var hasLowPhase = false
        var hasHighPhase = false

        for (i in 0 until samples.size - 1) {
            if (samples[i] < 6.0f) hasLowPhase = true
            if (hasLowPhase && samples[i] > 12.0f) hasHighPhase = true
        }

        return hasLowPhase && hasHighPhase
    }

    private fun calculateConfidence(features: AccelFeatures): Float {
        var confidence = 0.5f
        if (features.sampleCount >= 80) {
            confidence += 0.2f
        } else if (features.sampleCount >= 50) {
            confidence += 0.1f
        }
        if (features.magRange > 30f) confidence += 0.15f
        if (features.impactCount >= 2) confidence += 0.1f
        return minOf(1.0f, confidence)
    }

    fun quickPrescreen(features: AccelFeatures): Boolean {
        if (features.magMax < 11.0f) return false
        if (features.magRange < 6.0f) return false
        return true
    }
}

enum class FallDetectionResult {
    NO_FALL,
    POSSIBLE_FALL,
    CONFIRMED_FALL
}
