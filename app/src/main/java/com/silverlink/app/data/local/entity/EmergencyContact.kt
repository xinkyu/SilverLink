package com.silverlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 紧急联系人实体
 * 用于跌倒检测时的紧急通知
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,           // 联系人姓名
    val phone: String,          // 电话号码
    val relationship: String = "", // 关系（如：儿子、女儿、配偶）
    val isPrimary: Boolean = false, // 是否为主要联系人（优先拨打）
    val createdAt: Long = System.currentTimeMillis()
)
