package com.silverlink.app.feature.health

data class HealthTrendPoint(
    val timestamp: Long,
    val value: Int
)

data class OppoHealthDashboardData(
    val steps: Int = 0,
    val stepGoal: Int = 8000,
    val calories: Int = 0,
    val distanceMeters: Int = 0,
    val moveMinutes: Int = 0,
    val latestHeartRate: Int = 0,
    val bloodOxygen: Int = 0,
    val sleepMinutes: Int = 0,
    val heartRateTimeline: List<HealthTrendPoint> = emptyList()
)
