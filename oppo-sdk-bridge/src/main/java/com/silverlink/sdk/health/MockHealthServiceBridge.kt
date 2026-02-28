package com.silverlink.sdk.health

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Mock 实现，用于开发期间模拟健康数据
 */
class MockHealthServiceBridge : HealthServiceBridge {

    private var heartRateCallback: ((Int) -> Unit)? = null
    private var isMonitoring = false

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

    override suspend fun startHeartRateMonitor(callback: (Int) -> Unit) {
        heartRateCallback = callback
        isMonitoring = true
    }

    override suspend fun stopHeartRateMonitor() {
        isMonitoring = false
        heartRateCallback = null
    }

    override fun isAvailable(): Boolean = true
}
