package com.silverlink.shared.detection

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 加速度数据缓冲区
 *
 * 用于收集连续的加速度数据，形成滑动时间窗口供分类器分析
 */
class AccelerometerBuffer(
    private val windowSizeMs: Long = 2000L,
    private val sampleIntervalMs: Long = 20L
) {

    data class AccelSample(
        val x: Float,
        val y: Float,
        val z: Float,
        val magnitude: Float,
        val timestamp: Long
    )

    private val buffer = mutableListOf<AccelSample>()
    private val maxSamples = (windowSizeMs / sampleIntervalMs).toInt()
    private var lastSampleTime = 0L

    fun addSample(x: Float, y: Float, z: Float, magnitude: Float, timestamp: Long): Boolean {
        if (timestamp - lastSampleTime < sampleIntervalMs) {
            return false
        }
        lastSampleTime = timestamp
        buffer.add(AccelSample(x, y, z, magnitude, timestamp))
        while (buffer.size > maxSamples) {
            buffer.removeAt(0)
        }
        return true
    }

    fun isFull(): Boolean = buffer.size >= maxSamples * 0.8

    fun getFeatures(): AccelFeatures? {
        if (buffer.size < 20) return null

        val magnitudes = buffer.map { it.magnitude }
        val xValues = buffer.map { it.x }
        val yValues = buffer.map { it.y }
        val zValues = buffer.map { it.z }

        return AccelFeatures(
            magMean = magnitudes.average().toFloat(),
            magMax = magnitudes.maxOrNull() ?: 0f,
            magMin = magnitudes.minOrNull() ?: 0f,
            magStd = standardDeviation(magnitudes),
            magRange = (magnitudes.maxOrNull() ?: 0f) - (magnitudes.minOrNull() ?: 0f),
            xMean = xValues.average().toFloat(),
            yMean = yValues.average().toFloat(),
            zMean = zValues.average().toFloat(),
            xStd = standardDeviation(xValues),
            yStd = standardDeviation(yValues),
            zStd = standardDeviation(zValues),
            magChangeRate = calculateChangeRate(magnitudes),
            freeFallCount = countBelowThreshold(magnitudes, 3.0f),
            impactCount = countAboveThreshold(magnitudes, 20.0f),
            recentMagnitudes = magnitudes.takeLast(10).toFloatArray(),
            sampleCount = buffer.size
        )
    }

    fun getRecentAverage(n: Int): Float {
        if (buffer.isEmpty()) return 9.8f
        val samples = buffer.takeLast(n)
        return samples.map { it.magnitude }.average().toFloat()
    }

    fun clear() {
        buffer.clear()
        lastSampleTime = 0L
    }

    private fun standardDeviation(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).toFloat()
    }

    private fun calculateChangeRate(values: List<Float>): Float {
        if (values.size < 2) return 0f
        var totalChange = 0f
        for (i in 1 until values.size) {
            totalChange += abs(values[i] - values[i - 1])
        }
        return totalChange / (values.size - 1)
    }

    private fun countBelowThreshold(values: List<Float>, threshold: Float): Int {
        return values.count { it < threshold }
    }

    private fun countAboveThreshold(values: List<Float>, threshold: Float): Int {
        return values.count { it > threshold }
    }
}
