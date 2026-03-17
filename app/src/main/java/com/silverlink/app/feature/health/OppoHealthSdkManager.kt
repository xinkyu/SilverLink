package com.silverlink.app.feature.health

import android.app.Activity
import android.content.Context
import android.util.Log
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.sdk.health.HealthServiceBridgeFactory
import com.silverlink.sdk.health.HealthSdkException
import com.silverlink.shared.model.HealthData
import com.silverlink.shared.model.SleepQuality
import java.time.LocalDate
import java.time.ZoneId

object OppoHealthSdkManager {
    const val ERROR_HEALTH_APP_NOT_INSTALLED = 100007


    private const val TAG = "OppoHealthSdkManager"

    private fun bridge(context: Context) = HealthServiceBridgeFactory.get(context.applicationContext)

    suspend fun initializeIfConsented(context: Context, prefs: UserPreferences): Result<Unit> {
        if (!prefs.isOppoHealthSdkConsentGranted()) {
            return Result.failure(IllegalStateException("consent_not_granted"))
        }
        return bridge(context).initialize(context)
    }

    suspend fun requestAuthorization(
        activity: Activity,
        redirectUrl: String? = null
    ): Result<Unit> {
        val sdkBridge = bridge(activity)

        if (!sdkBridge.isHealthAppInstalled()) {
            sdkBridge.openHealthAppDownload(activity)
            return Result.failure(HealthSdkException(ERROR_HEALTH_APP_NOT_INSTALLED))
        }

        val initResult = sdkBridge.initialize(activity)
        if (initResult.isFailure) return initResult

        val authResult = if (redirectUrl.isNullOrBlank()) {
            sdkBridge.requestAuthorization(activity)
        } else {
            sdkBridge.requestAuthorization(activity, redirectUrl)
        }

        if (getErrorCode(authResult.exceptionOrNull()) == ERROR_HEALTH_APP_NOT_INSTALLED) {
            sdkBridge.openHealthAppDownload(activity)
        }
        return authResult
    }

    suspend fun revokeAuthorization(context: Context): Result<Unit> {
        return bridge(context).revokeAuthorization()
    }

    suspend fun getAuthorizedScopes(context: Context): Result<List<String>> {
        return bridge(context).getAuthorizedScopes()
    }

    suspend fun pullLatestSnapshot(context: Context, date: String): Result<HealthData> {
        val sdkBridge = bridge(context)

        val heartRate = sdkBridge.getHeartRate().getOrElse {
            Log.w(TAG, "getHeartRate failed", it)
            0
        }
        val steps = sdkBridge.getSteps().getOrElse {
            Log.w(TAG, "getSteps failed", it)
            0
        }
        val bloodOxygen = sdkBridge.getBloodOxygen().getOrElse {
            Log.w(TAG, "getBloodOxygen failed", it)
            0
        }

        val sleep = sdkBridge.getSleepData(date).getOrNull()
        return Result.success(
            HealthData(
                heartRate = heartRate,
                steps = steps,
                bloodOxygen = bloodOxygen,
                sleepMinutes = sleep?.totalMinutes ?: 0,
                sleepQuality = SleepQuality.UNKNOWN,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun pullDashboardData(context: Context, date: String): Result<OppoHealthDashboardData> {
        val sdkBridge = bridge(context)
        val day = LocalDate.parse(date)
        val zone = ZoneId.systemDefault()

        val startOfDay = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val last24hStart = endOfDay - 24 * 60 * 60 * 1000L

        val daily = sdkBridge.getDailyActivitySummary(startOfDay, endOfDay).getOrElse {
            return Result.failure(it)
        }
        val heartTimeline = sdkBridge.getHeartRateTimeline(last24hStart, endOfDay)
            .getOrDefault(emptyList())
            .map { HealthTrendPoint(it.timestamp, it.value) }
        val latestHeartRate = heartTimeline.lastOrNull()?.value
            ?: sdkBridge.getHeartRate().getOrDefault(0)
        val bloodOxygen = sdkBridge.getBloodOxygen().getOrDefault(0)
        val sleep = sdkBridge.getSleepData(date).getOrNull()

        return Result.success(
            OppoHealthDashboardData(
                steps = daily.steps,
                calories = daily.calories,
                distanceMeters = daily.distanceMeters,
                moveMinutes = daily.moveMinutes,
                latestHeartRate = latestHeartRate,
                bloodOxygen = bloodOxygen,
                sleepMinutes = sleep?.totalMinutes ?: 0,
                heartRateTimeline = heartTimeline
            )
        )
    }

    fun getErrorCode(error: Throwable?): Int? {
        return when (error) {
            is HealthSdkException -> error.code
            else -> null
        }
    }

    fun isSdkAvailable(context: Context): Boolean = bridge(context).isAvailable()

    fun isHealthAppInstalled(context: Context): Boolean = bridge(context).isHealthAppInstalled()

    fun openHealthAppDownload(activity: Activity) {
        bridge(activity).openHealthAppDownload(activity)
    }
}
