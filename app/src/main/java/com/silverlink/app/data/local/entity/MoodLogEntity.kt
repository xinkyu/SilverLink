package com.silverlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 情绪记录实体
 * 记录每次聊天检测到的情绪，用于日历历史视图
 */
@Entity(tableName = "mood_logs")
data class MoodLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mood: String,           // "HAPPY" | "SAD" | "ANGRY" | "ANXIOUS" | "NEUTRAL"
    val note: String,           // 触发情绪的对话摘要
    val date: String,           // "2026-01-18"
    val createdAt: Long = System.currentTimeMillis()
)
