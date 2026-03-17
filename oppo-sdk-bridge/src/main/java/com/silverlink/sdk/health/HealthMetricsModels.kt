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

data class SleepStagePoint(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val stage: Int
)

class HealthSdkException(
    val code: Int,
    message: String = "health_sdk_error_$code"
) : RuntimeException(message)
