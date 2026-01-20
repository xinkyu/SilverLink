package com.silverlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val dosage: String,
    val times: String, // Format: "HH:mm,HH:mm"
    val isTakenToday: Boolean = false
) {
    fun getTimeList(): List<String> {
        return if (times.isBlank()) {
            emptyList()
        } else {
            times.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
