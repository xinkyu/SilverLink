package com.silverlink.sdk.health

/**
 * OPPO 健康服务 SDK 抽象接口
 *
 * SDK 到达后实现此接口，替换 MockHealthServiceBridge
 */
interface HealthServiceBridge {
    suspend fun getHeartRate(): Result<Int>
    suspend fun getSteps(): Result<Int>
    suspend fun getSleepData(date: String): Result<SleepData>
    suspend fun getBloodOxygen(): Result<Int>
    suspend fun startHeartRateMonitor(callback: (Int) -> Unit)
    suspend fun stopHeartRateMonitor()
    fun isAvailable(): Boolean
}
