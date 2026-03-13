package com.silverlink.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MemoryDao {

    @Insert
    suspend fun insertMemoryRecord(record: MemoryRecordEntity): Long

    @Update
    suspend fun updateMemoryRecord(record: MemoryRecordEntity)

    @Query(
        """
        SELECT * FROM memory_records
        WHERE content LIKE '%' || :keyword || '%'
           OR keywordsText LIKE '%' || :keyword || '%'
        ORDER BY importance DESC, lastAccessAt DESC, createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchMemoryByKeyword(keyword: String, limit: Int): List<MemoryRecordEntity>

    @Query(
        """
        SELECT * FROM memory_records
        ORDER BY importance DESC, lastAccessAt DESC, createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun getTopMemories(limit: Int): List<MemoryRecordEntity>

    @Query("UPDATE memory_records SET lastAccessAt = :accessAt WHERE id IN (:ids)")
    suspend fun touchMemories(ids: List<Long>, accessAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM memory_records WHERE createdAt < :expireBefore AND importance < :maxImportance")
    suspend fun pruneLowImportanceMemories(expireBefore: Long, maxImportance: Float = 0.3f): Int

    @Query("DELETE FROM memory_records")
    suspend fun clearAllMemories()

    @Query("SELECT * FROM memory_records ORDER BY createdAt DESC")
    suspend fun listAllMemories(): List<MemoryRecordEntity>

    @Query("SELECT * FROM memory_records ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listRecentMemories(limit: Int): List<MemoryRecordEntity>

    @Query("SELECT * FROM memory_records WHERE content = :content ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLatestByExactContent(content: String): MemoryRecordEntity?

    @Query("DELETE FROM memory_records WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfileMemory(memory: UserProfileMemoryEntity)

    @Query("SELECT * FROM user_profile_memory ORDER BY updatedAt DESC")
    suspend fun listUserProfileMemories(): List<UserProfileMemoryEntity>

    @Query("SELECT * FROM user_profile_memory WHERE `key` = :key LIMIT 1")
    suspend fun getUserProfileMemory(key: String): UserProfileMemoryEntity?

    @Query("DELETE FROM user_profile_memory WHERE `key` = :key")
    suspend fun deleteUserProfileMemory(key: String)

    @Query("DELETE FROM user_profile_memory")
    suspend fun clearUserProfileMemories()
}
