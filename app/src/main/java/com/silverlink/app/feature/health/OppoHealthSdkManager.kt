package com.silverlink.app.feature.health

import android.app.Activity
import android.content.Context
import android.util.Log
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.sdk.health.BloodPressureData
import com.silverlink.sdk.health.BodyMeasurementData
import com.silverlink.sdk.health.HealthSdkException
import com.silverlink.sdk.health.HealthValuePoint
import com.silverlink.sdk.health.HealthServiceBridgeFactory
import com.silverlink.sdk.health.SleepSummaryPoint
import com.silverlink.shared.model.HealthData
import com.silverlink.shared.model.SleepQuality
import java.time.LocalDate
import java.time.ZoneId

object OppoHealthSdkManager {
    const val ERROR_HEALTH_APP_NOT_INSTALLED = 100007

    private const val TAG = "OppoHealthSdkManager"

    private fun bridge(context: Context) = HealthServiceBridgeFactory.get(context.applicationContext)

    suspend fun initializeIfConsented(context: Context, prefs: UserPreferences): Result<Unit> {
        Log.i(
            HealthDebugLogger.TAG_SDK,
            "initializeIfConsented consentGranted=${prefs.isOppoHealthSdkConsentGranted()} package=${context.packageName}"
        )
        if (!prefs.isOppoHealthSdkConsentGranted()) {
            return Result.failure(IllegalStateException("consent_not_granted"))
        }
        return bridge(context).initialize(context).also { result ->
            if (result.isSuccess) {
                Log.i(HealthDebugLogger.TAG_SDK, "SDK initialize success")
            } else {
                Log.e(HealthDebugLogger.TAG_SDK, "SDK initialize failed", result.exceptionOrNull())
            }
        }
    }

    suspend fun requestAuthorization(
        activity: Activity,
        redirectUrl: String? = null
    ): Result<Unit> {
        val sdkBridge = bridge(activity)
        val installed = sdkBridge.isHealthAppInstalled()
        Log.i(
            HealthDebugLogger.TAG_AUTH,
            "requestAuthorization start package=${activity.packageName} installed=$installed redirectUrl=${redirectUrl ?: "<default>"}"
        )

        if (!installed) {
            sdkBridge.openHealthAppDownload(activity)
            Log.w(HealthDebugLogger.TAG_AUTH, "Health app not installed, opening download")
            return Result.failure(HealthSdkException(ERROR_HEALTH_APP_NOT_INSTALLED))
        }

        val initResult = sdkBridge.initialize(activity)
        if (initResult.isFailure) {
            Log.e(HealthDebugLogger.TAG_AUTH, "SDK initialize before auth failed", initResult.exceptionOrNull())
            return initResult
        }
        Log.i(HealthDebugLogger.TAG_AUTH, "SDK initialize before auth success")

        val authResult = if (redirectUrl.isNullOrBlank()) {
            sdkBridge.requestAuthorization(activity)
        } else {
            sdkBridge.requestAuthorization(activity, redirectUrl)
        }

        if (authResult.isSuccess) {
            Log.i(HealthDebugLogger.TAG_AUTH, "Authorization request returned success")
            val scopes = runCatching { sdkBridge.getAuthorizedScopes().getOrDefault(emptyList()) }.getOrDefault(emptyList())
            Log.i(HealthDebugLogger.TAG_AUTH, "Authorized scopes after request=$scopes")
        } else {
            val error = authResult.exceptionOrNull()
            Log.e(
                HealthDebugLogger.TAG_AUTH,
                "Authorization request failed code=${getErrorCode(error)} message=${error?.message}",
                error
            )
        }

        if (getErrorCode(authResult.exceptionOrNull()) == ERROR_HEALTH_APP_NOT_INSTALLED) {
            sdkBridge.openHealthAppDownload(activity)
        }
        return authResult
    }

    suspend fun revokeAuthorization(context: Context): Result<Unit> {
        Log.i(HealthDebugLogger.TAG_AUTH, "revokeAuthorization start")
        return bridge(context).revokeAuthorization().also { result ->
            if (result.isSuccess) {
                Log.i(HealthDebugLogger.TAG_AUTH, "revokeAuthorization success")
            } else {
                Log.e(HealthDebugLogger.TAG_AUTH, "revokeAuthorization failed", result.exceptionOrNull())
            }
        }
    }

    suspend fun getAuthorizedScopes(context: Context): Result<List<String>> {
        return bridge(context).getAuthorizedScopes().also { result ->
            if (result.isSuccess) {
                Log.i(HealthDebugLogger.TAG_AUTH, "getAuthorizedScopes=${result.getOrDefault(emptyList())}")
            } else {
                Log.e(HealthDebugLogger.TAG_AUTH, "getAuthorizedScopes failed", result.exceptionOrNull())
            }
        }
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
        Log.i(HealthDebugLogger.TAG_DATA, "pullDashboardData start date=$date")
        val day = LocalDate.parse(date)
        val zone = ZoneId.systemDefault()

        val startOfDay = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val last24hStart = endOfDay - 24 * 60 * 60 * 1000L
        val last30dStart = day.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()

        val daily = sdkBridge.getDailyActivitySummary(startOfDay, endOfDay).getOrElse {
            Log.e(HealthDebugLogger.TAG_DATA, "getDailyActivitySummary failed", it)
            return Result.failure(it)
        }
        val heartTimeline = sdkBridge.getHeartRateTimeline(last24hStart, endOfDay)
            .getOrDefault(emptyList())
            .toTrendPoints()
        val bloodOxygenTimeline = sdkBridge.getBloodOxygenTimeline(last24hStart, endOfDay)
            .getOrDefault(emptyList())
            .toTrendPoints()
        val pressureTimeline = sdkBridge.getPressureTimeline(last24hStart, endOfDay)
            .getOrDefault(emptyList())
            .toTrendPoints()
        val activityTimeline = sdkBridge.getDailyActivityTimeline(last30dStart, endOfDay)
            .getOrDefault(emptyList())
            .toTrendPoints()
        val latestHeartRate = heartTimeline.lastOrNull()?.value
            ?: sdkBridge.getHeartRate().getOrDefault(0)
        val bloodOxygen = sdkBridge.getBloodOxygen().getOrDefault(0)
        val pressure = sdkBridge.getPressure().getOrDefault(0)
        val sleep = sdkBridge.getSleepData(date).getOrNull()
        val sleepTimeline = sdkBridge.getSleepTimeline(startOfDay, endOfDay).getOrDefault(emptyList())
        val bloodPressure = sdkBridge.getBloodPressure().getOrNull()
        val weight = sdkBridge.getWeight().getOrNull()

        val dashboard = OppoHealthDashboardData(
                steps = daily.steps,
                calories = daily.calories,
                distanceMeters = daily.distanceMeters,
                moveMinutes = daily.moveMinutes,
                latestHeartRate = latestHeartRate,
                bloodOxygen = bloodOxygen,
                sleepMinutes = sleep?.totalMinutes ?: 0,
                sleepScore = sleep?.score ?: 0,
                sleepDeepMinutes = sleep?.deepSleepMinutes ?: 0,
                sleepLightMinutes = sleep?.lightSleepMinutes ?: 0,
                sleepRemMinutes = sleep?.remMinutes ?: 0,
                sleepAwakeMinutes = sleep?.awakeMinutes ?: 0,
                latestPressure = pressure,
                latestBloodPressureSystolic = bloodPressure?.systolic ?: 0,
                latestBloodPressureDiastolic = bloodPressure?.diastolic ?: 0,
                latestWeightKg = weight?.weightKg ?: 0f,
                latestBodyMassIndex = weight?.bmi ?: 0f,
                heartRateTimeline = heartTimeline,
                bloodOxygenTimeline = bloodOxygenTimeline,
                pressureTimeline = pressureTimeline,
                activityTimeline = activityTimeline,
                heartRateDailySummary = sdkBridge.getHeartRateDailySummary(last30dStart, endOfDay)
                    .getOrDefault(emptyList())
                    .toTrendPoints(),
                activityDailySummary = activityTimeline,
                bloodOxygenDailySummary = sdkBridge.getBloodOxygenDailySummary(last30dStart, endOfDay)
                    .getOrDefault(emptyList())
                    .toTrendPoints(),
                pressureDailySummary = sdkBridge.getPressureDailySummary(last30dStart, endOfDay)
                    .getOrDefault(emptyList())
                    .toTrendPoints(),
                sleepDailySummary = sdkBridge.getSleepDailySummary(last30dStart, endOfDay)
                    .getOrDefault(emptyList()),
                sleepTimeline = sleepTimeline,
                bloodPressureTimeline = sdkBridge.getBloodPressureTimeline(last30dStart, endOfDay)
                    .getOrDefault(emptyList()),
                weightTimeline = sdkBridge.getWeightTimeline(last30dStart, endOfDay)
                    .getOrDefault(emptyList())
            )
        Log.i(
            HealthDebugLogger.TAG_DATA,
            "pullDashboardData success steps=${dashboard.steps} heartRate=${dashboard.latestHeartRate} spo2=${dashboard.bloodOxygen} sleep=${dashboard.sleepMinutes} pressure=${dashboard.latestPressure} bp=${dashboard.latestBloodPressureSystolic}/${dashboard.latestBloodPressureDiastolic} weight=${dashboard.latestWeightKg}"
        )
        return Result.success(dashboard)
    }

    suspend fun getHeartRateSummary(context: Context, startTime: Long, endTime: Long): Result<List<HealthTrendPoint>> {
        return bridge(context).getHeartRateDailySummary(startTime, endTime).mapTrendPoints()
    }

    suspend fun getHeartRateTimeline(context: Context, startTime: Long, endTime: Long): Result<List<HealthTrendPoint>> {
        return bridge(context).getHeartRateTimeline(startTime, endTime).mapTrendPoints()
    }

    suspend fun getActivitySummary(context: Context, startTime: Long, endTime: Long): Result<List<HealthTrendPoint>> {
        return bridge(context).getDailyActivityTimeline(startTime, endTime).mapTrendPoints()
    }

    suspend fun getSleepSummary(context: Context, startTime: Long, endTime: Long): Result<List<SleepSummaryPoint>> {
        return bridge(context).getSleepDailySummary(startTime, endTime)
    }

    suspend fun getBloodOxygenSummary(context: Context, startTime: Long, endTime: Long): Result<List<HealthTrendPoint>> {
        return bridge(context).getBloodOxygenDailySummary(startTime, endTime).mapTrendPoints()
    }

    suspend fun getBloodOxygenTimeline(context: Context, startTime: Long, endTime: Long): Result<List<HealthTrendPoint>> {
        return bridge(context).getBloodOxygenTimeline(startTime, endTime).mapTrendPoints()
    }

    suspend fun getPressureSummary(context: Context, startTime: Long, endTime: Long): Result<List<HealthTrendPoint>> {
        return bridge(context).getPressureDailySummary(startTime, endTime).mapTrendPoints()
    }

    suspend fun getPressureTimeline(context: Context, startTime: Long, endTime: Long): Result<List<HealthTrendPoint>> {
        return bridge(context).getPressureTimeline(startTime, endTime).mapTrendPoints()
    }

    suspend fun getBloodPressureTimeline(
        context: Context,
        startTime: Long,
        endTime: Long
    ): Result<List<BloodPressureData>> {
        return bridge(context).getBloodPressureTimeline(startTime, endTime)
    }

    suspend fun getWeightTimeline(
        context: Context,
        startTime: Long,
        endTime: Long
    ): Result<List<BodyMeasurementData>> {
        return bridge(context).getWeightTimeline(startTime, endTime)
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

    private fun List<HealthValuePoint>.toTrendPoints(): List<HealthTrendPoint> {
        return map { HealthTrendPoint(it.timestamp, it.value) }
    }

    private fun Result<List<HealthValuePoint>>.mapTrendPoints(): Result<List<HealthTrendPoint>> {
        return map { it.toTrendPoints() }
    }
}
