package com.silverlink.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 聊天数据访问对象
 */
@Dao
interface ChatDao {
    
    // ==================== 会话操作 ====================
    
    /**
     * 创建新会话
     */
    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long
    
    /**
     * 更新会话
     */
    @Update
    suspend fun updateConversation(conversation: ConversationEntity)
    
    /**
     * 获取所有会话（按最后更新时间降序）
     */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    
    /**
     * 获取会话列表（非 Flow，用于一次性加载）
     */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getConversationList(): List<ConversationEntity>
    
    /**
     * 获取单个会话
     */
    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: Long): ConversationEntity?
    
    /**
     * 删除会话（级联删除消息）
     */
    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)
    
    // ==================== 消息操作 ====================
    
    /**
     * 插入新消息
     */
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long
    
    /**
     * 获取指定会话的所有消息（按时间升序）
     */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessageEntity>>
    
    /**
     * 获取指定会话的消息列表（非 Flow）
     */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessageList(conversationId: Long): List<ChatMessageEntity>
    
    /**
     * 获取指定会话的消息数量
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int
    
    /**
     * 删除指定会话的所有消息
     */
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)
}
