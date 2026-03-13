package com.silverlink.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 结构化核心画像记忆，按 key 存储。
 */
@Entity(tableName = "user_profile_memory")
data class UserProfileMemoryEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val confidence: Float = 0.7f,
    val updatedAt: Long = System.currentTimeMillis()
)
