package com.silverlink.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 长期记忆记录。
 * keywordsText 使用逗号拼接，首版便于快速查询，后续可平滑替换为向量字段。
 */
@Entity(
    tableName = "memory_records",
    indices = [Index("sourceConversationId"), Index("createdAt"), Index("lastAccessAt")]
)
data class MemoryRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceConversationId: Long,
    val content: String,
    val keywordsText: String = "",
    val importance: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessAt: Long = System.currentTimeMillis()
)
