package com.silverlink.shared.detection

/**
 * 加速度特征数据类
 */
data class AccelFeatures(
    val magMean: Float,
    val magMax: Float,
    val magMin: Float,
    val magStd: Float,
    val magRange: Float,
    val xMean: Float,
    val yMean: Float,
    val zMean: Float,
    val xStd: Float,
    val yStd: Float,
    val zStd: Float,
    val magChangeRate: Float,
    val freeFallCount: Int,
    val impactCount: Int,
    val recentMagnitudes: FloatArray,
    val sampleCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AccelFeatures
        return magMean == other.magMean && magMax == other.magMax
    }

    override fun hashCode(): Int {
        return 31 * magMean.hashCode() + magMax.hashCode()
    }
}
