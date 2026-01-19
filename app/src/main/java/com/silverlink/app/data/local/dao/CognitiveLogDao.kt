package com.silverlink.app.data.local.dao

import androidx.room.*
import com.silverlink.app.data.local.entity.CognitiveLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 认知评估记录数据访问对象
 */
@Dao
interface CognitiveLogDao {
    
    /**
     * 插入认知评估记录
     */
    @Insert
    suspend fun insert(log: CognitiveLogEntity): Long
    
    /**
     * 获取指定时间范围内的认知记录
     */
    @Query("""
        SELECT * FROM cognitive_logs 
        WHERE elderDeviceId = :elderDeviceId 
        AND createdAt BETWEEN :startTime AND :endTime
        ORDER BY createdAt DESC
    """)
    suspend fun getLogsByTimeRange(
        elderDeviceId: String,
        startTime: Long,
        endTime: Long
    ): List<CognitiveLogEntity>
    
    /**
     * 获取最近 N 条认知记录
     */
    @Query("""
        SELECT * FROM cognitive_logs 
        WHERE elderDeviceId = :elderDeviceId 
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun getRecentLogs(elderDeviceId: String, limit: Int): List<CognitiveLogEntity>
    
    /**
     * 获取所有未同步的记录
     */
    @Query("SELECT * FROM cognitive_logs WHERE syncedToCloud = 0")
    suspend fun getUnsyncedLogs(): List<CognitiveLogEntity>
    
    /**
     * 标记为已同步
     */
    @Query("UPDATE cognitive_logs SET syncedToCloud = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    /**
     * 统计正确率
     */
    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END) as correct
        FROM cognitive_logs 
        WHERE elderDeviceId = :elderDeviceId 
        AND createdAt >= :sinceTime
    """)
    suspend fun getStats(elderDeviceId: String, sinceTime: Long): CognitiveStats
    
    /**
     * 获取平均响应时间
     */
    @Query("""
        SELECT AVG(responseTimeMs) FROM cognitive_logs 
        WHERE elderDeviceId = :elderDeviceId 
        AND createdAt >= :sinceTime
    """)
    suspend fun getAverageResponseTime(elderDeviceId: String, sinceTime: Long): Long?
    
    /**
     * 删除旧记录（保留最近 N 条）
     */
    @Query("""
        DELETE FROM cognitive_logs 
        WHERE elderDeviceId = :elderDeviceId 
        AND id NOT IN (
            SELECT id FROM cognitive_logs 
            WHERE elderDeviceId = :elderDeviceId 
            ORDER BY createdAt DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun deleteOldLogs(elderDeviceId: String, keepCount: Int)
}

/**
 * 认知统计数据类（用于 Room 查询结果）
 */
data class CognitiveStats(
    val total: Int,
    val correct: Int
) {
    val correctRate: Float get() = if (total > 0) correct.toFloat() / total else 0f
}
