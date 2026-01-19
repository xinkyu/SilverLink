package com.silverlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.silverlink.app.data.local.entity.MedicationLogEntity
import com.silverlink.app.data.local.entity.MoodLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 历史记录 DAO
 * 用于查询服药记录和情绪记录
 */
@Dao
interface HistoryDao {
    
    // ==================== 服药记录 ====================
    
    @Insert
    suspend fun insertMedicationLog(log: MedicationLogEntity): Long
    
    @Query("SELECT * FROM medication_logs WHERE date = :date ORDER BY scheduledTime ASC")
    suspend fun getMedicationLogsByDate(date: String): List<MedicationLogEntity>
    
    @Query("SELECT * FROM medication_logs WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, scheduledTime ASC")
    suspend fun getMedicationLogsByDateRange(startDate: String, endDate: String): List<MedicationLogEntity>
    
    @Query("SELECT DISTINCT date FROM medication_logs ORDER BY date DESC")
    suspend fun getAllMedicationLogDates(): List<String>
    
    @Query("SELECT COUNT(*) FROM medication_logs WHERE date = :date AND status = 'taken'")
    suspend fun getTakenCountByDate(date: String): Int
    
    @Query("SELECT COUNT(*) FROM medication_logs WHERE date = :date")
    suspend fun getTotalCountByDate(date: String): Int

    @Query("SELECT COUNT(*) FROM medication_logs WHERE date = :date AND medicationId = :medicationId AND scheduledTime = :scheduledTime")
    suspend fun getMedicationLogCount(date: String, medicationId: Int, scheduledTime: String): Int
    
    // ==================== 情绪记录 ====================
    
    @Insert
    suspend fun insertMoodLog(log: MoodLogEntity): Long
    
    @Query("SELECT * FROM mood_logs WHERE date = :date ORDER BY createdAt DESC")
    suspend fun getMoodLogsByDate(date: String): List<MoodLogEntity>
    
    @Query("SELECT * FROM mood_logs WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, createdAt DESC")
    suspend fun getMoodLogsByDateRange(startDate: String, endDate: String): List<MoodLogEntity>
    
    @Query("SELECT DISTINCT date FROM mood_logs ORDER BY date DESC")
    suspend fun getAllMoodLogDates(): List<String>
    
    /**
     * 获取某天的主导情绪（出现次数最多的）
     */
    @Query("""
        SELECT mood FROM mood_logs 
        WHERE date = :date 
        GROUP BY mood 
        ORDER BY COUNT(*) DESC 
        LIMIT 1
    """)
    suspend fun getDominantMoodByDate(date: String): String?
    
    /**
     * 获取最近一条情绪记录（用于主动关怀唤醒词生成）
     */
    @Query("SELECT * FROM mood_logs ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMoodLog(): MoodLogEntity?
}
