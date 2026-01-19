package com.silverlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记忆照片实体
 * 存储从云端同步的照片元数据和本地缓存路径
 */
@Entity(tableName = "memory_photos")
data class MemoryPhotoEntity(
    @PrimaryKey 
    val cloudId: String,              // 云端唯一 ID
    val elderDeviceId: String,        // 长辈设备 ID
    val familyDeviceId: String,       // 上传者设备 ID
    val imageUrl: String,             // 云存储图片 URL
    val thumbnailUrl: String?,        // 缩略图 URL
    val localPath: String?,           // 本地缓存路径
    val thumbnailPath: String?,       // 本地缩略图路径
    val description: String,          // 家人录入的描述
    val aiDescription: String,        // AI 生成的描述
    val takenDate: String?,           // 拍摄日期 (YYYY-MM-DD)
    val location: String?,            // 拍摄地点
    val people: String?,              // 照片中的人物（逗号分隔）
    val tags: String?,                // 标签（逗号分隔）
    val isDownloaded: Boolean = false, // 是否已下载到本地
    val createdAt: Long = System.currentTimeMillis(), // 创建时间
    val lastSyncAt: Long = 0          // 最后同步时间
) {
    /**
     * 获取人物列表
     */
    fun getPeopleList(): List<String> = 
        people?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    
    /**
     * 获取标签列表
     */
    fun getTagsList(): List<String> = 
        tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    
    /**
     * 获取用于展示的描述（优先使用家人描述，其次用 AI 描述）
     */
    fun getDisplayDescription(): String = 
        description.ifBlank { aiDescription }
    
    /**
     * 获取用于检索的完整文本（用于本地搜索）
     */
    fun getSearchableText(): String = buildString {
        append(description)
        append(" ")
        append(aiDescription)
        append(" ")
        append(location ?: "")
        append(" ")
        append(people ?: "")
        append(" ")
        append(tags ?: "")
    }.lowercase()
}
