package com.silverlink.app.feature.falldetection

/**
 * 加速度数据缓冲区
 * 
 * 用于收集连续的加速度数据，形成滑动时间窗口供 ML 模型分析
 * 
 * @param windowSizeMs 时间窗口大小（毫秒）
 * @param sampleIntervalMs 采样间隔（毫秒）
 */
class AccelerometerBuffer(
    private val windowSizeMs: Long = 2000L,  // 2秒窗口
    private val sampleIntervalMs: Long = 20L  // 50Hz采样
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
    
    /**
     * 添加新的加速度样本
     * 返回 true 如果成功添加，false 如果因采样间隔被跳过
     */
    fun addSample(x: Float, y: Float, z: Float, magnitude: Float, timestamp: Long): Boolean {
        // 控制采样率
        if (timestamp - lastSampleTime < sampleIntervalMs) {
            return false
        }
        lastSampleTime = timestamp
        
        buffer.add(AccelSample(x, y, z, magnitude, timestamp))
        
        // 移除过期样本
        while (buffer.size > maxSamples) {
            buffer.removeAt(0)
        }
        
        return true
    }
    
    /**
     * 缓冲区是否已满（有足够数据进行分析）
     */
    fun isFull(): Boolean = buffer.size >= maxSamples * 0.8  // 80% 填充即可
    
    /**
     * 获取当前缓冲区的统计特征
     */
    fun getFeatures(): AccelFeatures? {
        if (buffer.size < 20) return null  // 至少需要20个样本
        
        val magnitudes = buffer.map { it.magnitude }
        val xValues = buffer.map { it.x }
        val yValues = buffer.map { it.y }
        val zValues = buffer.map { it.z }
        
        return AccelFeatures(
            // 幅度统计
            magMean = magnitudes.average().toFloat(),
            magMax = magnitudes.maxOrNull() ?: 0f,
            magMin = magnitudes.minOrNull() ?: 0f,
            magStd = standardDeviation(magnitudes),
            magRange = (magnitudes.maxOrNull() ?: 0f) - (magnitudes.minOrNull() ?: 0f),
            
            // 各轴统计
            xMean = xValues.average().toFloat(),
            yMean = yValues.average().toFloat(),
            zMean = zValues.average().toFloat(),
            xStd = standardDeviation(xValues),
            yStd = standardDeviation(yValues),
            zStd = standardDeviation(zValues),
            
            // 变化率特征
            magChangeRate = calculateChangeRate(magnitudes),
            
            // 时序特征
            freeFallCount = countBelowThreshold(magnitudes, 3.0f),
            impactCount = countAboveThreshold(magnitudes, 20.0f),
            
            // 最近样本
            recentMagnitudes = magnitudes.takeLast(10).toFloatArray(),
            
            // 样本数量
            sampleCount = buffer.size
        )
    }
    
    /**
     * 获取最近N个样本的平均幅度
     */
    fun getRecentAverage(n: Int): Float {
        if (buffer.isEmpty()) return 9.8f
        val samples = buffer.takeLast(n)
        return samples.map { it.magnitude }.average().toFloat()
    }
    
    /**
     * 清空缓冲区
     */
    fun clear() {
        buffer.clear()
        lastSampleTime = 0L
    }
    
    private fun standardDeviation(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }
    
    private fun calculateChangeRate(values: List<Float>): Float {
        if (values.size < 2) return 0f
        var totalChange = 0f
        for (i in 1 until values.size) {
            totalChange += kotlin.math.abs(values[i] - values[i - 1])
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

/**
 * 加速度特征数据类
 */
data class AccelFeatures(
    // 幅度统计
    val magMean: Float,
    val magMax: Float,
    val magMin: Float,
    val magStd: Float,
    val magRange: Float,
    
    // 各轴统计
    val xMean: Float,
    val yMean: Float,
    val zMean: Float,
    val xStd: Float,
    val yStd: Float,
    val zStd: Float,
    
    // 变化率
    val magChangeRate: Float,
    
    // 时序特征
    val freeFallCount: Int,
    val impactCount: Int,
    
    // 最近样本
    val recentMagnitudes: FloatArray,
    
    // 样本数
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
