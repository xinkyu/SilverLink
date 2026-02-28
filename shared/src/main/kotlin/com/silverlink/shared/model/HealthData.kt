package com.silverlink.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthData(
    val heartRate: Int = 0,
    val steps: Int = 0,
    val sleepMinutes: Int = 0,
    val sleepQuality: SleepQuality = SleepQuality.UNKNOWN,
    val bloodOxygen: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class SleepQuality {
    DEEP, LIGHT, REM, AWAKE, UNKNOWN
}
