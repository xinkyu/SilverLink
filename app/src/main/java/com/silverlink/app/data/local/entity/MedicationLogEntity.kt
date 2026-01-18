package com.silverlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 服药记录实体
 * 记录每次服药情况，用于日历历史视图
 */
@Entity(tableName = "medication_logs")
data class MedicationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Int,
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,  // "08:00"
    val status: String,         // "taken" | "missed" | "snoozed"
    val date: String,           // "2026-01-18"
    val createdAt: Long = System.currentTimeMillis()
)
