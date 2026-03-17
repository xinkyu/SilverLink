package com.silverlink.sdk.health

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlin.random.Random

/**
 * Mock 实现，用于开发期间模拟健康数据
 */
class MockHealthServiceBridge : HealthServiceBridge {

    private var heartRateCallback: ((Int) -> Unit)? = null
    private var isMonitoring = false

    override fun initialize(context: Context): Result<Unit> = Result.success(Unit)

    override suspend fun requestAuthorization(activity: Activity): Result<Unit> = Result.success(Unit)

    override suspend fun requestAuthorization(activity: Activity, redirectUrl: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun revokeAuthorization(): Result<Unit> = Result.success(Unit)

    override suspend fun getAuthorizedScopes(): Result<List<String>> =
        Result.success(
            listOf(
                "READ_HEART_RATE",
                "READ_DAILY_ACTIVITY",
                "READ_SLEEP_DATA",
                "READ_BLOOD_OXYGEN_DATA"
            )
        )

    override suspend fun getHeartRate(): Result<Int> {
        delay(100)
        return Result.success(Random.nextInt(60, 100))
    }

    override suspend fun getSteps(): Result<Int> {
        delay(100)
        return Result.success(Random.nextInt(1000, 8000))
    }

    override suspend fun getSleepData(date: String): Result<SleepData> {
        delay(200)
        val totalMinutes = Random.nextInt(300, 540)
        val deepMinutes = (totalMinutes * 0.2).toInt()
        val remMinutes = (totalMinutes * 0.25).toInt()
        val awakeMinutes = Random.nextInt(10, 40)
        val lightMinutes = totalMinutes - deepMinutes - remMinutes - awakeMinutes

        return Result.success(
            SleepData(
                totalMinutes = totalMinutes,
                deepSleepMinutes = deepMinutes,
                lightSleepMinutes = lightMinutes,
                remMinutes = remMinutes,
                awakeMinutes = awakeMinutes,
                score = Random.nextInt(60, 95),
                date = date
            )
        )
    }

    override suspend fun getBloodOxygen(): Result<Int> {
        delay(100)
        return Result.success(Random.nextInt(95, 100))
    }

    override suspend fun getDailyActivitySummary(startTime: Long, endTime: Long): Result<DailyActivitySummary> {
        delay(120)
        return Result.success(
            DailyActivitySummary(
                steps = Random.nextInt(1800, 9000),
                calories = Random.nextInt(1200, 2800),
                distanceMeters = Random.nextInt(1000, 7000),
                moveMinutes = Random.nextInt(15, 120)
            )
        )
    }

    override suspend fun getHeartRateTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>> {
        delay(120)
        val points = mutableListOf<HealthValuePoint>()
        val step = ((endTime - startTime) / 24).coerceAtLeast(60_000)
        var t = startTime
        while (t <= endTime) {
            points.add(HealthValuePoint(timestamp = t, value = Random.nextInt(58, 108)))
            t += step
        }
        return Result.success(points)
    }

    override suspend fun getSleepTimeline(startTime: Long, endTime: Long): Result<List<SleepStagePoint>> {
        delay(120)
        val points = listOf(
            SleepStagePoint(startTime + 60 * 60 * 1000L, startTime + 2 * 60 * 60 * 1000L, 4),
            SleepStagePoint(startTime + 2 * 60 * 60 * 1000L, startTime + 4 * 60 * 60 * 1000L, 2),
            SleepStagePoint(startTime + 4 * 60 * 60 * 1000L, startTime + 5 * 60 * 60 * 1000L, 3)
        )
        return Result.success(points)
    }

    override suspend fun startHeartRateMonitor(callback: (Int) -> Unit) {
        heartRateCallback = callback
        isMonitoring = true
        while (isMonitoring && currentCoroutineContext().isActive) {
            callback(Random.nextInt(60, 100))
            delay(15_000)
        }
    }

    override suspend fun stopHeartRateMonitor() {
        isMonitoring = false
        heartRateCallback = null
    }

    override fun isHealthAppInstalled(): Boolean = true

    override fun openHealthAppDownload(activity: Activity) = Unit

    override fun isAvailable(): Boolean = true
}
