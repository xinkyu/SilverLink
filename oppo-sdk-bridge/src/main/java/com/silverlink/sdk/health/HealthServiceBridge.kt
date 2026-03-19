package com.silverlink.sdk.health

import android.app.Activity
import android.content.Context

/**
 * OPPO 健康服务 SDK 抽象接口
 *
 * SDK 到达后实现此接口，替换 MockHealthServiceBridge
 */
interface HealthServiceBridge {
    fun initialize(context: Context): Result<Unit>
    suspend fun requestAuthorization(activity: Activity): Result<Unit>
    suspend fun requestAuthorization(activity: Activity, redirectUrl: String): Result<Unit>
    suspend fun revokeAuthorization(): Result<Unit>
    suspend fun getAuthorizedScopes(): Result<List<String>>
    suspend fun getHeartRate(): Result<Int>
    suspend fun getHeartRateDailySummary(startTime: Long, endTime: Long): Result<List<HealthValuePoint>>
    suspend fun getSteps(): Result<Int>
    suspend fun getDailyActivityTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>>
    suspend fun getSleepData(date: String): Result<SleepData>
    suspend fun getSleepDailySummary(startTime: Long, endTime: Long): Result<List<SleepSummaryPoint>>
    suspend fun getBloodOxygen(): Result<Int>
    suspend fun getBloodOxygenTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>>
    suspend fun getBloodOxygenDailySummary(startTime: Long, endTime: Long): Result<List<HealthValuePoint>>
    suspend fun getPressure(): Result<Int>
    suspend fun getPressureTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>>
    suspend fun getPressureDailySummary(startTime: Long, endTime: Long): Result<List<HealthValuePoint>>
    suspend fun getBloodPressure(): Result<BloodPressureData>
    suspend fun getBloodPressureTimeline(startTime: Long, endTime: Long): Result<List<BloodPressureData>>
    suspend fun getWeight(): Result<BodyMeasurementData>
    suspend fun getWeightTimeline(startTime: Long, endTime: Long): Result<List<BodyMeasurementData>>
    suspend fun getDailyActivitySummary(startTime: Long, endTime: Long): Result<DailyActivitySummary>
    suspend fun getHeartRateTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>>
    suspend fun getSleepTimeline(startTime: Long, endTime: Long): Result<List<SleepStagePoint>>
    suspend fun startHeartRateMonitor(callback: (Int) -> Unit)
    suspend fun stopHeartRateMonitor()
    fun isHealthAppInstalled(): Boolean
    fun openHealthAppDownload(activity: Activity)
    fun isAvailable(): Boolean
}
