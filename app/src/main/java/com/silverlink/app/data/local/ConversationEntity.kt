package com.silverlink.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 会话实体 - 代表一次独立的对话
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 会话标题（通常取自第一条用户消息） */
    val title: String = "新对话",
    
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 最后更新时间 */
    val updatedAt: Long = System.currentTimeMillis()
)
