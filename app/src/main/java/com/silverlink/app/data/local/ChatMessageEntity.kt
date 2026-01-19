package com.silverlink.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天消息实体 - 用于 Room 数据库持久化
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 所属会话 ID */
    val conversationId: Long,
    
    /** 消息角色: "user", "assistant", "system" */
    val role: String,
    
    /** 消息内容 */
    val content: String,
    
    /** 检测到的情绪 (可空，仅用户消息有) */
    val emotion: String? = null,
    
    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis()
)
