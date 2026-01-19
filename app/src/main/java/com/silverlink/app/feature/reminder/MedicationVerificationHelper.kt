package com.silverlink.app.feature.reminder

import android.util.Log

/**
 * 药品验证结果
 */
sealed class VerificationResult {
    /**
     * 正确 - 识别的药品与当前时间段计划的药品匹配
     */
    data class Correct(
        val medicationName: String,
        val dosage: String
    ) : VerificationResult()
    
    /**
     * 错误的药 - 识别出药品但不是当前时间段应该服用的
     */
    data class WrongMed(
        val recognizedName: String,
        val correctName: String,
        val correctDosage: String
    ) : VerificationResult()
    
    /**
     * 当前没有计划的药 - 现在不是吃药时间
     */
    data class NoScheduleNow(
        val nextTime: String? = null
    ) : VerificationResult()
    
    /**
     * 未能识别的药品
     */
    object UnknownMed : VerificationResult()
}

/**
 * 药品验证辅助类
 * 将 AI 识别的药品与计划中的药品进行比对
 */
class MedicationVerificationHelper {
    
    companion object {
        private const val TAG = "MedicationVerification"
        
        // 模糊匹配阈值（0-100），超过此值视为匹配
        private const val FUZZY_MATCH_THRESHOLD = 60
        
        // 时间窗口（分钟）：当前时间前后多少分钟内的药品都算"现在该吃的"
        private const val TIME_WINDOW_MINUTES = 120
    }
    
    /**
     * 验证识别到的药品是否是当前应该服用的
     * 
     * @param recognizedName AI识别出的药品名称
     * @param scheduledMeds 用户计划的所有药品列表
     * @param currentTime 当前时间 (格式: "HH:mm")
     * @return 验证结果
     */
    fun verifyMedication(
        recognizedName: String,
        scheduledMeds: List<com.silverlink.app.data.local.entity.Medication>,
        currentTime: String
    ): VerificationResult {
        if (recognizedName.isBlank()) {
            Log.d(TAG, "Recognized name is blank")
            return VerificationResult.UnknownMed
        }
        
        // 1. 筛选当前时间段内应该服用的药品
        val medsForNow = filterMedicationsInTimeWindow(scheduledMeds, currentTime)
        
        Log.d(TAG, "Current time: $currentTime, Meds for now: ${medsForNow.map { it.name }}")
        
        if (medsForNow.isEmpty()) {
            // 当前没有药需要吃，找下一次吃药时间
            val nextTime = findNextScheduledTime(scheduledMeds, currentTime)
            Log.d(TAG, "No medications scheduled now, next time: $nextTime")
            return VerificationResult.NoScheduleNow(nextTime)
        }
        
        // 2. 尝试匹配识别的药品
        for (med in medsForNow) {
            val similarity = calculateSimilarity(recognizedName, med.name)
            Log.d(TAG, "Comparing '$recognizedName' with '${med.name}': similarity = $similarity")
            
            if (similarity >= FUZZY_MATCH_THRESHOLD) {
                // 找到匹配的药品
                Log.d(TAG, "Match found: ${med.name}")
                return VerificationResult.Correct(
                    medicationName = med.name,
                    dosage = med.dosage
                )
            }
        }
        
        // 3. 没有匹配，返回错误提示（推荐第一个应该吃的药）
        val suggestedMed = medsForNow.first()
        Log.d(TAG, "No match found, suggesting: ${suggestedMed.name}")
        return VerificationResult.WrongMed(
            recognizedName = recognizedName,
            correctName = suggestedMed.name,
            correctDosage = suggestedMed.dosage
        )
    }
    
    /**
     * 筛选在时间窗口内应该服用的药品
     */
    private fun filterMedicationsInTimeWindow(
        medications: List<com.silverlink.app.data.local.entity.Medication>,
        currentTime: String
    ): List<com.silverlink.app.data.local.entity.Medication> {
        val currentMinutes = parseTimeToMinutes(currentTime) ?: return emptyList()
        
        return medications.filter { med ->
            med.getTimeList().any { time ->
                val scheduleMinutes = parseTimeToMinutes(time) ?: return@any false
                val diff = kotlin.math.abs(currentMinutes - scheduleMinutes)
                // 考虑跨天情况 (例如 23:00 和 00:30)
                val adjustedDiff = minOf(diff, 24 * 60 - diff)
                adjustedDiff <= TIME_WINDOW_MINUTES
            }
        }
    }
    
    /**
     * 找到下一次吃药时间
     */
    private fun findNextScheduledTime(
        medications: List<com.silverlink.app.data.local.entity.Medication>,
        currentTime: String
    ): String? {
        val currentMinutes = parseTimeToMinutes(currentTime) ?: return null
        
        val allTimes = medications.flatMap { it.getTimeList() }
            .mapNotNull { parseTimeToMinutes(it)?.let { m -> it to m } }
            .sortedBy { (_, minutes) ->
                // 计算从当前时间到该时间的差距（考虑跨天）
                if (minutes > currentMinutes) {
                    minutes - currentMinutes
                } else {
                    24 * 60 - currentMinutes + minutes
                }
            }
        
        return allTimes.firstOrNull()?.first
    }
    
    /**
     * 将 "HH:mm" 格式的时间转换为分钟数
     */
    private fun parseTimeToMinutes(time: String): Int? {
        return try {
            val parts = time.split(":")
            if (parts.size == 2) {
                parts[0].toInt() * 60 + parts[1].toInt()
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 计算两个字符串的相似度 (0-100)
     * 使用 Levenshtein 距离的变体
     */
    fun calculateSimilarity(str1: String, str2: String): Int {
        val s1 = str1.lowercase().trim()
        val s2 = str2.lowercase().trim()
        
        // 完全匹配
        if (s1 == s2) return 100
        
        // 包含关系（一个是另一个的子串）
        if (s1.contains(s2) || s2.contains(s1)) {
            val longerLen = maxOf(s1.length, s2.length)
            val shorterLen = minOf(s1.length, s2.length)
            return (shorterLen.toDouble() / longerLen * 95).toInt()
        }
        
        // Levenshtein 距离
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 100
        
        val distance = levenshteinDistance(s1, s2)
        return ((1 - distance.toDouble() / maxLen) * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * 计算 Levenshtein 编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
                }
            }
        }
        
        return dp[m][n]
    }
}
