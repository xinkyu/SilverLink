package com.silverlink.app.feature.falldetection

import android.content.Context
import android.util.Log

/**
 * 跌倒检测 ML 分类器
 * 
 * 使用多层特征分析来判断是否发生跌倒：
 * 1. 统计特征分析（均值、方差、范围）
 * 2. 时序模式识别（自由落体→撞击→静止）
 * 3. 方向变化检测
 * 
 * 返回跌倒概率 (0.0 - 1.0)
 */
class FallDetectionMLClassifier(private val context: Context) {
    
    companion object {
        private const val TAG = "FallMLClassifier"
        
        // 跌倒特征阈值（针对软着陆场景大幅优化）
        private const val MIN_IMPACT_MAGNITUDE = 13.0f    // 降低到 1.3G (摔床上冲击较小)
        private const val MIN_FREE_FALL_SAMPLES = 1       // 只要有任何失重迹象即可
        private const val MIN_MAG_RANGE = 8.0f            // 最小幅度范围降低
        private const val MAX_NORMAL_STD = 2.0f           // 进一步收紧静止时的标准差
        
        // 分类概率阈值
        const val FALL_PROBABILITY_THRESHOLD = 0.40f      // 降低到 40%，通过确认期来过滤误报
    }
    
    /**
     * 分类结果
     */
    data class ClassificationResult(
        val fallProbability: Float,        // 跌倒概率 (0-1)
        val confidence: Float,             // 置信度 (0-1)
        val detectedPatterns: List<String> // 检测到的模式
    )
    
    /**
     * 分析加速度特征，返回跌倒概率
     */
    fun classify(features: AccelFeatures): ClassificationResult {
        val patterns = mutableListOf<String>()
        var score = 0f
        var maxScore = 0f
        
        // ========== 特征 1: 幅度范围检测 ==========
        // 跌倒通常有明显的加速度变化（从自由落体到撞击）
        maxScore += 25f
        if (features.magRange > MIN_MAG_RANGE) {
            val rangeScore = minOf(25f, (features.magRange - MIN_MAG_RANGE) / 2f)
            score += rangeScore
            patterns.add("幅度范围异常: ${String.format("%.1f", features.magRange)}")
            Log.d(TAG, "Range score: $rangeScore (range=${features.magRange})")
        }
        
        // ========== 特征 2: 撞击检测 ==========
        // 检测是否有高强度撞击
        maxScore += 30f
        if (features.magMax > MIN_IMPACT_MAGNITUDE) {
            val impactScore = minOf(30f, (features.magMax - MIN_IMPACT_MAGNITUDE) / 1.5f)
            score += impactScore
            patterns.add("检测到撞击: ${String.format("%.1f", features.magMax)}m/s²")
            Log.d(TAG, "Impact score: $impactScore (max=${features.magMax})")
        }
        
        // ========== 特征 3: 自由落体检测 ==========
        // 跌倒前通常有短暂的自由落体（加速度接近0）
        maxScore += 20f
        if (features.freeFallCount >= MIN_FREE_FALL_SAMPLES) {
            val fallScore = minOf(20f, features.freeFallCount * 3f)
            score += fallScore
            patterns.add("自由落体样本: ${features.freeFallCount}")
            Log.d(TAG, "FreeFall score: $fallScore (count=${features.freeFallCount})")
        }
        
        // ========== 特征 4: 标准差异常 ==========
        // 跌倒过程中加速度变化剧烈，标准差会很大
        maxScore += 15f
        if (features.magStd > MAX_NORMAL_STD) {
            val stdScore = minOf(15f, (features.magStd - MAX_NORMAL_STD) * 2f)
            score += stdScore
            patterns.add("变化剧烈: std=${String.format("%.2f", features.magStd)}")
            Log.d(TAG, "Std score: $stdScore (std=${features.magStd})")
        }
        
        // ========== 特征 5: 时序模式验证 ==========
        // 检查是否存在 "低→高→稳定" 的典型跌倒模式
        maxScore += 10f
        if (hasTypicalFallPattern(features)) {
            score += 10f
            patterns.add("检测到典型跌倒模式")
            Log.d(TAG, "Pattern bonus: +10")
        }
        
        // ========== 计算最终概率 ==========
        val probability = score / maxScore
        val confidence = calculateConfidence(features)
        
        Log.d(TAG, "Classification: score=$score/$maxScore, probability=$probability, confidence=$confidence")
        Log.d(TAG, "Patterns: $patterns")
        
        return ClassificationResult(
            fallProbability = probability,
            confidence = confidence,
            detectedPatterns = patterns
        )
    }
    
    /**
     * 检测典型跌倒模式：低加速度（自由落体）→ 高加速度（撞击）→ 稳定（静止）
     */
    private fun hasTypicalFallPattern(features: AccelFeatures): Boolean {
        val samples = features.recentMagnitudes
        if (samples.size < 5) return false
        
        // 查找模式：是否存在先低后高的序列（降低阈值）
        var hasLowPhase = false
        var hasHighPhase = false
        
        for (i in 0 until samples.size - 1) {
            if (samples[i] < 6.0f) hasLowPhase = true  // 从 4.0 降低到 6.0
            if (hasLowPhase && samples[i] > 12.0f) hasHighPhase = true  // 从 15.0 降低到 12.0
        }
        
        return hasLowPhase && hasHighPhase
    }
    
    /**
     * 计算分类置信度
     * 基于特征的完整性和一致性
     */
    private fun calculateConfidence(features: AccelFeatures): Float {
        var confidence = 0.5f
        
        // 样本越多，置信度越高
        if (features.sampleCount >= 80) {
            confidence += 0.2f
        } else if (features.sampleCount >= 50) {
            confidence += 0.1f
        }
        
        // 特征越明显，置信度越高
        if (features.magRange > 30f) confidence += 0.15f
        if (features.impactCount >= 2) confidence += 0.1f
        
        return minOf(1.0f, confidence)
    }
    
    /**
     * 快速预筛选
     * 在完整分析前先做快速检查，过滤明显不是跌倒的情况
     */
    fun quickPrescreen(features: AccelFeatures): Boolean {
        // 放宽预筛选条件，避免漏掉软着陆
        if (features.magMax < 11.0f) {
            return false
        }
        
        if (features.magRange < 6.0f) {
            return false
        }
        
        return true
    }
}

/**
 * 跌倒检测结果
 */
enum class FallDetectionResult {
    NO_FALL,           // 未检测到跌倒
    POSSIBLE_FALL,     // 可能跌倒，需要进一步确认
    CONFIRMED_FALL     // 确认跌倒
}
