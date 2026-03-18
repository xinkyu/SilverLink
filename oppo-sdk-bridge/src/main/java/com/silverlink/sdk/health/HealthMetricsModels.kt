package com.silverlink.sdk.health

data class DailyActivitySummary(
    val steps: Int = 0,
    val calories: Int = 0,
    val distanceMeters: Int = 0,
    val moveMinutes: Int = 0
)

data class HealthValuePoint(
    val timestamp: Long,
    val value: Int
)

data class BloodPressureData(
    val timestamp: Long,
    val systolic: Int,
    val diastolic: Int
)

data class BodyMeasurementData(
    val timestamp: Long,
    val weightKg: Float,
    val bmi: Float = 0f
)

data class SleepSummaryPoint(
    val timestamp: Long,
    val totalMinutes: Int,
    val deepSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val score: Int
)

data class SleepStagePoint(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val stage: Int
)

class HealthSdkException(
    val code: Int,
    message: String = "health_sdk_error_$code"
) : RuntimeException(message)
