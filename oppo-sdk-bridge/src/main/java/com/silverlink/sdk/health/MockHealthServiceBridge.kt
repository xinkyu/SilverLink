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

    override suspend fun getHeartRateDailySummary(startTime: Long, endTime: Long): Result<List<HealthValuePoint>> {
        delay(120)
        return Result.success(generateIntSeries(startTime, endTime, 58, 96))
    }

    override suspend fun getSteps(): Result<Int> {
        delay(100)
        return Result.success(Random.nextInt(1000, 8000))
    }

    override suspend fun getDailyActivityTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>> {
        delay(120)
        return Result.success(generateDailySeries(startTime, endTime, 1800, 9200))
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

    override suspend fun getSleepDailySummary(startTime: Long, endTime: Long): Result<List<SleepSummaryPoint>> {
        delay(120)
        val step = 24 * 60 * 60 * 1000L
        val result = mutableListOf<SleepSummaryPoint>()
        var t = startOfDay(startTime)
        while (t <= endTime) {
            val totalMinutes = Random.nextInt(320, 520)
            val deep = (totalMinutes * 0.22f).toInt()
            val rem = (totalMinutes * 0.24f).toInt()
            val awake = Random.nextInt(10, 40)
            val light = (totalMinutes - deep - rem - awake).coerceAtLeast(0)
            result += SleepSummaryPoint(
                timestamp = t,
                totalMinutes = totalMinutes,
                deepSleepMinutes = deep,
                lightSleepMinutes = light,
                remMinutes = rem,
                awakeMinutes = awake,
                score = Random.nextInt(60, 95)
            )
            t += step
        }
        return Result.success(result)
    }

    override suspend fun getBloodOxygen(): Result<Int> {
        delay(100)
        return Result.success(Random.nextInt(95, 100))
    }

    override suspend fun getBloodOxygenTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>> {
        delay(120)
        return Result.success(generateIntSeries(startTime, endTime, 94, 100))
    }

    override suspend fun getBloodOxygenDailySummary(startTime: Long, endTime: Long): Result<List<HealthValuePoint>> {
        delay(120)
        return Result.success(generateDailySeries(startTime, endTime, 94, 100))
    }

    override suspend fun getPressure(): Result<Int> {
        delay(100)
        return Result.success(Random.nextInt(20, 75))
    }

    override suspend fun getPressureTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>> {
        delay(120)
        return Result.success(generateIntSeries(startTime, endTime, 15, 80))
    }

    override suspend fun getPressureDailySummary(startTime: Long, endTime: Long): Result<List<HealthValuePoint>> {
        delay(120)
        return Result.success(generateDailySeries(startTime, endTime, 15, 80))
    }

    override suspend fun getBloodPressure(): Result<BloodPressureData> {
        delay(120)
        return Result.success(
            BloodPressureData(
                timestamp = System.currentTimeMillis(),
                systolic = Random.nextInt(108, 132),
                diastolic = Random.nextInt(68, 86)
            )
        )
    }

    override suspend fun getBloodPressureTimeline(startTime: Long, endTime: Long): Result<List<BloodPressureData>> {
        delay(140)
        val step = 24 * 60 * 60 * 1000L
        val result = mutableListOf<BloodPressureData>()
        var t = startOfDay(startTime)
        while (t <= endTime) {
            result += BloodPressureData(
                timestamp = t + 8 * 60 * 60 * 1000L,
                systolic = Random.nextInt(108, 138),
                diastolic = Random.nextInt(68, 90)
            )
            t += step
        }
        return Result.success(result)
    }

    override suspend fun getWeight(): Result<BodyMeasurementData> {
        delay(100)
        val weight = Random.nextDouble(64.0, 78.0).toFloat()
        return Result.success(
            BodyMeasurementData(
                timestamp = System.currentTimeMillis(),
                weightKg = weight,
                bmi = Random.nextDouble(21.0, 25.0).toFloat()
            )
        )
    }

    override suspend fun getWeightTimeline(startTime: Long, endTime: Long): Result<List<BodyMeasurementData>> {
        delay(140)
        val step = 7 * 24 * 60 * 60 * 1000L
        val result = mutableListOf<BodyMeasurementData>()
        var t = startOfDay(startTime)
        var weight = Random.nextDouble(64.0, 78.0).toFloat()
        while (t <= endTime) {
            result += BodyMeasurementData(
                timestamp = t + 8 * 60 * 60 * 1000L,
                weightKg = weight,
                bmi = (weight / 1.75f / 1.75f)
            )
            weight += Random.nextDouble(-0.6, 0.4).toFloat()
            t += step
        }
        return Result.success(result)
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

    private fun generateIntSeries(
        startTime: Long,
        endTime: Long,
        minValue: Int,
        maxValue: Int
    ): List<HealthValuePoint> {
        val points = mutableListOf<HealthValuePoint>()
        val step = ((endTime - startTime) / 24).coerceAtLeast(60_000)
        var t = startTime
        while (t <= endTime) {
            points += HealthValuePoint(timestamp = t, value = Random.nextInt(minValue, maxValue + 1))
            t += step
        }
        return points
    }

    private fun generateDailySeries(
        startTime: Long,
        endTime: Long,
        minValue: Int,
        maxValue: Int
    ): List<HealthValuePoint> {
        val result = mutableListOf<HealthValuePoint>()
        val step = 24 * 60 * 60 * 1000L
        var t = startOfDay(startTime)
        while (t <= endTime) {
            result += HealthValuePoint(timestamp = t, value = Random.nextInt(minValue, maxValue + 1))
            t += step
        }
        return result
    }

    private fun startOfDay(timestamp: Long): Long {
        val dayMs = 24 * 60 * 60 * 1000L
        return timestamp - (timestamp % dayMs)
    }
}
