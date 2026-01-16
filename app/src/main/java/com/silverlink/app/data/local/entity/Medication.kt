package com.silverlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val dosage: String,
    val times: String, // 多个时间，逗号分隔，如 "08:00,12:00,18:00"
    val isTakenToday: Boolean = false
) {
    // 辅助方法：获取时间列表
    fun getTimeList(): List<String> = times.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
