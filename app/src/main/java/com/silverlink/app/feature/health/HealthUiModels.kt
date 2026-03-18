package com.silverlink.app.feature.health

import com.silverlink.sdk.health.BloodPressureData
import com.silverlink.sdk.health.BodyMeasurementData
import com.silverlink.sdk.health.SleepStagePoint
import com.silverlink.sdk.health.SleepSummaryPoint

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
    val sleepScore: Int = 0,
    val sleepDeepMinutes: Int = 0,
    val sleepLightMinutes: Int = 0,
    val sleepRemMinutes: Int = 0,
    val sleepAwakeMinutes: Int = 0,
    val latestPressure: Int = 0,
    val latestBloodPressureSystolic: Int = 0,
    val latestBloodPressureDiastolic: Int = 0,
    val latestWeightKg: Float = 0f,
    val latestBodyMassIndex: Float = 0f,
    val heartRateTimeline: List<HealthTrendPoint> = emptyList(),
    val bloodOxygenTimeline: List<HealthTrendPoint> = emptyList(),
    val pressureTimeline: List<HealthTrendPoint> = emptyList(),
    val activityTimeline: List<HealthTrendPoint> = emptyList(),
    val heartRateDailySummary: List<HealthTrendPoint> = emptyList(),
    val activityDailySummary: List<HealthTrendPoint> = emptyList(),
    val bloodOxygenDailySummary: List<HealthTrendPoint> = emptyList(),
    val pressureDailySummary: List<HealthTrendPoint> = emptyList(),
    val sleepDailySummary: List<SleepSummaryPoint> = emptyList(),
    val sleepTimeline: List<SleepStagePoint> = emptyList(),
    val bloodPressureTimeline: List<BloodPressureData> = emptyList(),
    val weightTimeline: List<BodyMeasurementData> = emptyList()
)
