package com.silverlink.app.data.local.dao

import androidx.room.*
import com.silverlink.app.data.local.entity.MemoryPhotoEntity
import kotlinx.coroutines.flow.Flow

/**
 * 记忆照片数据访问对象
 */
@Dao
interface MemoryPhotoDao {
    
    /**
     * 插入或更新照片
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: MemoryPhotoEntity)
    
    /**
     * 批量插入照片
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<MemoryPhotoEntity>)
    
    /**
     * 根据云端 ID 获取照片
     */
    @Query("SELECT * FROM memory_photos WHERE cloudId = :cloudId")
    suspend fun getByCloudId(cloudId: String): MemoryPhotoEntity?
    
    /**
     * 获取所有照片（按创建时间倒序）
     */
    @Query("SELECT * FROM memory_photos WHERE elderDeviceId = :elderDeviceId ORDER BY createdAt DESC")
    fun getAllPhotos(elderDeviceId: String): Flow<List<MemoryPhotoEntity>>
    
    /**
     * 获取所有已下载的照片
     */
    @Query("SELECT * FROM memory_photos WHERE elderDeviceId = :elderDeviceId AND isDownloaded = 1 ORDER BY createdAt DESC")
    fun getDownloadedPhotos(elderDeviceId: String): Flow<List<MemoryPhotoEntity>>
    
    /**
     * 获取待下载的照片
     */
    @Query("SELECT * FROM memory_photos WHERE elderDeviceId = :elderDeviceId AND isDownloaded = 0 ORDER BY createdAt ASC")
    suspend fun getPendingDownloads(elderDeviceId: String): List<MemoryPhotoEntity>
    
    /**
     * 搜索照片（本地搜索）
     */
    @Query("""
        SELECT * FROM memory_photos 
        WHERE elderDeviceId = :elderDeviceId 
        AND (
            description LIKE '%' || :keyword || '%' 
            OR aiDescription LIKE '%' || :keyword || '%'
            OR location LIKE '%' || :keyword || '%'
            OR people LIKE '%' || :keyword || '%'
            OR tags LIKE '%' || :keyword || '%'
        )
        ORDER BY createdAt DESC
    """)
    suspend fun searchPhotos(elderDeviceId: String, keyword: String): List<MemoryPhotoEntity>
    
    /**
     * 按日期范围获取照片
     */
    @Query("""
        SELECT * FROM memory_photos 
        WHERE elderDeviceId = :elderDeviceId 
        AND takenDate BETWEEN :startDate AND :endDate
        ORDER BY takenDate DESC
    """)
    suspend fun getPhotosByDateRange(
        elderDeviceId: String, 
        startDate: String, 
        endDate: String
    ): List<MemoryPhotoEntity>
    
    /**
     * 获取包含特定人物的照片
     */
    @Query("""
        SELECT * FROM memory_photos 
        WHERE elderDeviceId = :elderDeviceId 
        AND people LIKE '%' || :personName || '%'
        ORDER BY createdAt DESC
    """)
    suspend fun getPhotosByPerson(elderDeviceId: String, personName: String): List<MemoryPhotoEntity>
    
    /**
     * 更新下载状态
     */
    @Query("UPDATE memory_photos SET isDownloaded = 1, localPath = :localPath, thumbnailPath = :thumbnailPath WHERE cloudId = :cloudId")
    suspend fun markAsDownloaded(cloudId: String, localPath: String, thumbnailPath: String?)
    
    /**
     * 更新同步时间
     */
    @Query("UPDATE memory_photos SET lastSyncAt = :timestamp WHERE cloudId = :cloudId")
    suspend fun updateSyncTime(cloudId: String, timestamp: Long)
    
    /**
     * 删除照片
     */
    @Delete
    suspend fun delete(photo: MemoryPhotoEntity)
    
    /**
     * 获取照片总数
     */
    @Query("SELECT COUNT(*) FROM memory_photos WHERE elderDeviceId = :elderDeviceId")
    suspend fun getPhotoCount(elderDeviceId: String): Int
    
    /**
     * 获取已下载照片数量
     */
    @Query("SELECT COUNT(*) FROM memory_photos WHERE elderDeviceId = :elderDeviceId AND isDownloaded = 1")
    suspend fun getDownloadedCount(elderDeviceId: String): Int

    /**
     * 获取最新照片创建时间（用于增量同步）
     */
    @Query("SELECT MAX(createdAt) FROM memory_photos WHERE elderDeviceId = :elderDeviceId")
    suspend fun getLatestCreatedAt(elderDeviceId: String): Long?
    
    /**
     * 随机获取一张包含人物的照片（用于认知测试）
     */
    @Query("""
        SELECT * FROM memory_photos 
        WHERE elderDeviceId = :elderDeviceId 
        AND people IS NOT NULL 
        AND people != ''
        AND isDownloaded = 1
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getRandomPersonPhoto(elderDeviceId: String): MemoryPhotoEntity?
}
