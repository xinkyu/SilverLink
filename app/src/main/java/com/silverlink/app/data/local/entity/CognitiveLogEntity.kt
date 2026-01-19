package com.silverlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 认知评估记录实体
 * 记录老人的认知考察结果，用于追踪认知变化趋势
 */
@Entity(tableName = "cognitive_logs")
data class CognitiveLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val elderDeviceId: String,        // 长辈设备 ID
    val photoCloudId: String,         // 测试用的照片 ID
    val questionType: String,         // 问题类型: "person", "location", "date", "event"
    val expectedAnswer: String,       // 预期答案（如人物名称）
    val actualAnswer: String,         // 老人的实际回答
    val isCorrect: Boolean,           // 是否回答正确
    val responseTimeMs: Long,         // 回答用时（毫秒）
    val confidence: Float = 0f,       // AI 对回答正确性的置信度 (0-1)
    val createdAt: Long = System.currentTimeMillis(),
    val syncedToCloud: Boolean = false // 是否已同步到云端
)

/**
 * 认知日报/周报汇总
 */
data class CognitiveSummary(
    val totalQuestions: Int,
    val correctAnswers: Int,
    val averageResponseTimeMs: Long,
    val startDate: String,
    val endDate: String
) {
    val correctRate: Float get() = if (totalQuestions > 0) correctAnswers.toFloat() / totalQuestions else 0f
    val scoreDescription: String get() = when {
        correctRate >= 0.9f -> "记忆力很棒"
        correctRate >= 0.7f -> "记忆力良好"
        correctRate >= 0.5f -> "建议多练习"
        else -> "建议关注"
    }
}
