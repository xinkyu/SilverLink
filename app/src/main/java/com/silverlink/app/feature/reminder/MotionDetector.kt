package com.silverlink.app.feature.reminder

import android.graphics.Bitmap
import android.util.Log

/**
 * 运动检测器
 * 比较前后帧的像素差异，判断画面是否稳定
 */
class MotionDetector(
    private val motionThreshold: Float = 0.02f,  // 2% 像素变化视为有运动
    private val stableDurationMs: Long = 500L     // 稳定 500ms 后触发
) {
    companion object {
        private const val TAG = "MotionDetector"
        private const val SAMPLE_SIZE = 8  // 缩小采样尺寸以提高性能
    }

    private var previousFrame: IntArray? = null
    private var stableStartTime: Long = 0L
    private var isCurrentlyStable = false
    private var hasCaptured = false  // 防止重复触发

    /**
     * 分析当前帧，返回是否应该捕获
     * @param bitmap 当前相机帧
     * @return true 如果画面已稳定足够时间且未捕获过
     */
    fun analyzeFrame(bitmap: Bitmap): Boolean {
        if (hasCaptured) return false
        
        // 缩小图像以提高分析速度
        val scaled = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, false)
        val currentPixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        scaled.getPixels(currentPixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        scaled.recycle()

        val previous = previousFrame
        previousFrame = currentPixels

        if (previous == null) {
            // 第一帧，无法比较
            return false
        }

        // 计算像素变化比例
        val motionScore = calculateMotionScore(previous, currentPixels)
        val isStable = motionScore < motionThreshold

        Log.d(TAG, "Motion score: ${"%.4f".format(motionScore)}, isStable: $isStable")

        val currentTime = System.currentTimeMillis()

        if (isStable) {
            if (!isCurrentlyStable) {
                // 刚变稳定，记录开始时间
                stableStartTime = currentTime
                isCurrentlyStable = true
            } else {
                // 持续稳定，检查是否达到阈值
                val stableDuration = currentTime - stableStartTime
                if (stableDuration >= stableDurationMs) {
                    Log.d(TAG, "Stable for ${stableDuration}ms, triggering capture")
                    hasCaptured = true
                    return true
                }
            }
        } else {
            // 有运动，重置稳定状态
            isCurrentlyStable = false
            stableStartTime = 0L
        }

        return false
    }

    /**
     * 计算两帧之间的运动分数 (0-1)
     */
    private fun calculateMotionScore(frame1: IntArray, frame2: IntArray): Float {
        if (frame1.size != frame2.size) return 1f

        var changedPixels = 0
        val threshold = 30  // 单个通道变化阈值

        for (i in frame1.indices) {
            val p1 = frame1[i]
            val p2 = frame2[i]

            // 比较 RGB 通道差异
            val rDiff = kotlin.math.abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF))
            val gDiff = kotlin.math.abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF))
            val bDiff = kotlin.math.abs((p1 and 0xFF) - (p2 and 0xFF))

            if (rDiff > threshold || gDiff > threshold || bDiff > threshold) {
                changedPixels++
            }
        }

        return changedPixels.toFloat() / frame1.size
    }

    /**
     * 获取当前稳定进度 (0-1)
     */
    fun getStabilityProgress(): Float {
        if (!isCurrentlyStable) return 0f
        val elapsed = System.currentTimeMillis() - stableStartTime
        return (elapsed.toFloat() / stableDurationMs).coerceIn(0f, 1f)
    }

    /**
     * 是否当前帧稳定
     */
    fun isStable(): Boolean = isCurrentlyStable

    /**
     * 重置检测器状态，允许再次捕获
     */
    fun reset() {
        previousFrame = null
        stableStartTime = 0L
        isCurrentlyStable = false
        hasCaptured = false
    }
}
