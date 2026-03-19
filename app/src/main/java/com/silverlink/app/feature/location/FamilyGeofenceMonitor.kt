package com.silverlink.app.feature.location

import android.location.Location
import com.silverlink.app.data.local.FamilyGeofenceRuntimeState
import com.silverlink.app.data.local.FamilyGeofenceSettings
import com.silverlink.app.data.local.GeofenceBoundaryStatus
import com.silverlink.app.data.remote.LocationData
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

data class GeofenceEvaluationResult(
    val runtimeState: FamilyGeofenceRuntimeState,
    val distanceMeters: Float? = null,
    val accuracyBufferMeters: Float = 0f,
    val effectiveRadiusMeters: Float = 0f,
    val stableStatus: GeofenceBoundaryStatus = GeofenceBoundaryStatus.UNKNOWN,
    val pendingStatus: GeofenceBoundaryStatus? = null,
    val pendingDurationMillis: Long = 0L,
    val isUncertain: Boolean = false,
    val shouldNotifyEnter: Boolean = false,
    val shouldNotifyExit: Boolean = false,
    val suppressReason: String? = null
)

object FamilyGeofenceMonitor {
    private const val MIN_ACCURACY_BUFFER_METERS = 35f
    private const val MAX_RELIABLE_ACCURACY_METERS = 300f

    fun evaluate(
        settings: FamilyGeofenceSettings,
        runtimeState: FamilyGeofenceRuntimeState,
        latestLocation: LocationData?,
        nowMillis: Long = System.currentTimeMillis()
    ): GeofenceEvaluationResult {
        if (!settings.enabled || !settings.hasCenter || latestLocation == null) {
            return GeofenceEvaluationResult(runtimeState = runtimeState)
        }

        val centerLat = settings.centerLatitude ?: return GeofenceEvaluationResult(runtimeState = runtimeState)
        val centerLng = settings.centerLongitude ?: return GeofenceEvaluationResult(runtimeState = runtimeState)
        val distance = distanceMeters(
            latitude = latestLocation.latitude,
            longitude = latestLocation.longitude,
            targetLatitude = centerLat,
            targetLongitude = centerLng
        )
        val rawAccuracy = latestLocation.accuracy.takeIf { it > 0f } ?: 0f
        val accuracyBuffer = max(rawAccuracy, MIN_ACCURACY_BUFFER_METERS)
        val outsideThreshold = settings.radiusMeters + accuracyBuffer
        val insideThreshold = max(0f, settings.radiusMeters - min(accuracyBuffer, settings.radiusMeters * 0.5f))

        val candidateStatus = when {
            rawAccuracy >= MAX_RELIABLE_ACCURACY_METERS -> null
            distance > outsideThreshold -> GeofenceBoundaryStatus.OUTSIDE
            distance < insideThreshold -> GeofenceBoundaryStatus.INSIDE
            else -> null
        }

        if (candidateStatus == null) {
            val clearedState = runtimeState.copy(
                pendingStatus = null,
                pendingSinceMillis = 0L
            )
            return GeofenceEvaluationResult(
                runtimeState = clearedState,
                distanceMeters = distance,
                accuracyBufferMeters = accuracyBuffer,
                effectiveRadiusMeters = outsideThreshold,
                stableStatus = clearedState.currentStatus,
                isUncertain = true,
                suppressReason = if (rawAccuracy >= MAX_RELIABLE_ACCURACY_METERS) "定位精度较差，暂不判断" else "位于守护范围边界缓冲区"
            )
        }

        if (runtimeState.currentStatus == GeofenceBoundaryStatus.UNKNOWN) {
            val initializedState = runtimeState.copy(
                currentStatus = candidateStatus,
                pendingStatus = null,
                pendingSinceMillis = 0L,
                lastStatusChangeMillis = nowMillis
            )
            return GeofenceEvaluationResult(
                runtimeState = initializedState,
                distanceMeters = distance,
                accuracyBufferMeters = accuracyBuffer,
                effectiveRadiusMeters = outsideThreshold,
                stableStatus = candidateStatus
            )
        }

        if (candidateStatus == runtimeState.currentStatus) {
            val stableState = runtimeState.copy(
                pendingStatus = null,
                pendingSinceMillis = 0L
            )
            return GeofenceEvaluationResult(
                runtimeState = stableState,
                distanceMeters = distance,
                accuracyBufferMeters = accuracyBuffer,
                effectiveRadiusMeters = outsideThreshold,
                stableStatus = stableState.currentStatus
            )
        }

        val pendingState = if (runtimeState.pendingStatus == candidateStatus && runtimeState.pendingSinceMillis > 0L) {
            runtimeState
        } else {
            runtimeState.copy(
                pendingStatus = candidateStatus,
                pendingSinceMillis = nowMillis
            )
        }
        val pendingDuration = nowMillis - pendingState.pendingSinceMillis
        val requiredDuration = settings.dwellMinutes * 60_000L

        if (pendingDuration < requiredDuration) {
            return GeofenceEvaluationResult(
                runtimeState = pendingState,
                distanceMeters = distance,
                accuracyBufferMeters = accuracyBuffer,
                effectiveRadiusMeters = outsideThreshold,
                stableStatus = pendingState.currentStatus,
                pendingStatus = candidateStatus,
                pendingDurationMillis = pendingDuration
            )
        }

        val quietHours = isQuietHours(settings, nowMillis)
        val enterAllowed = candidateStatus == GeofenceBoundaryStatus.INSIDE &&
            settings.notifyOnEnter &&
            !quietHours &&
            shouldNotify(settings, pendingState.lastEnterAlertMillis, nowMillis)
        val exitAllowed = candidateStatus == GeofenceBoundaryStatus.OUTSIDE &&
            settings.notifyOnExit &&
            !quietHours &&
            shouldNotify(settings, pendingState.lastExitAlertMillis, nowMillis)

        val transitioned = pendingState.copy(
            currentStatus = candidateStatus,
            pendingStatus = null,
            pendingSinceMillis = 0L,
            lastStatusChangeMillis = nowMillis,
            lastEnterAlertMillis = if (enterAllowed) nowMillis else pendingState.lastEnterAlertMillis,
            lastExitAlertMillis = if (exitAllowed) nowMillis else pendingState.lastExitAlertMillis
        )

        return GeofenceEvaluationResult(
            runtimeState = transitioned,
            distanceMeters = distance,
            accuracyBufferMeters = accuracyBuffer,
            effectiveRadiusMeters = outsideThreshold,
            stableStatus = candidateStatus,
            shouldNotifyEnter = enterAllowed,
            shouldNotifyExit = exitAllowed,
            suppressReason = when {
                quietHours -> "当前处于安静时段"
                candidateStatus == GeofenceBoundaryStatus.INSIDE && !settings.notifyOnEnter -> "已关闭进入提醒"
                candidateStatus == GeofenceBoundaryStatus.OUTSIDE && !settings.notifyOnExit -> "已关闭离开提醒"
                candidateStatus == GeofenceBoundaryStatus.INSIDE && !enterAllowed -> "进入提醒冷却中"
                candidateStatus == GeofenceBoundaryStatus.OUTSIDE && !exitAllowed -> "离开提醒冷却中"
                else -> null
            }
        )
    }

    fun distanceMeters(
        latitude: Double,
        longitude: Double,
        targetLatitude: Double,
        targetLongitude: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(latitude, longitude, targetLatitude, targetLongitude, result)
        return result[0]
    }

    fun isQuietHours(settings: FamilyGeofenceSettings, nowMillis: Long): Boolean {
        if (!settings.quietHoursEnabled) return false
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (settings.quietHoursStartHour == settings.quietHoursEndHour) {
            true
        } else if (settings.quietHoursStartHour < settings.quietHoursEndHour) {
            hour in settings.quietHoursStartHour until settings.quietHoursEndHour
        } else {
            hour >= settings.quietHoursStartHour || hour < settings.quietHoursEndHour
        }
    }

    private fun shouldNotify(
        settings: FamilyGeofenceSettings,
        lastAlertMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (!settings.lowFrequencyEnabled) return true
        if (lastAlertMillis <= 0L) return true
        return nowMillis - lastAlertMillis >= settings.reminderCooldownMinutes * 60_000L
    }
}
