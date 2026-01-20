package com.silverlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 紧急联系人实体
 * 用于跌倒检测时发送通知
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    /** 联系人姓名 */
    val name: String,
    
    /** 电话号码 */
    val phone: String,
    
    /** 关系（如"儿子"、"女儿"、"老伴"等） */
    val relationship: String = "",
    
    /** 是否为首要联系人（跌倒时优先拨打电话） */
    val isPrimary: Boolean = false,
    
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis()
)
