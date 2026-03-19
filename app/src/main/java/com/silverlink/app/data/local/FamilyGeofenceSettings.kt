package com.silverlink.app.data.local

data class FamilyGeofenceSettings(
    val enabled: Boolean = false,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val radiusMeters: Float = 300f,
    val notifyOnExit: Boolean = true,
    val notifyOnEnter: Boolean = false,
    val dwellMinutes: Int = 5,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartHour: Int = 22,
    val quietHoursEndHour: Int = 7,
    val lowFrequencyEnabled: Boolean = true,
    val reminderCooldownMinutes: Int = 60
) {
    val hasCenter: Boolean
        get() = centerLatitude != null && centerLongitude != null
}

enum class GeofenceBoundaryStatus {
    UNKNOWN,
    INSIDE,
    OUTSIDE
}

data class FamilyGeofenceRuntimeState(
    val currentStatus: GeofenceBoundaryStatus = GeofenceBoundaryStatus.UNKNOWN,
    val pendingStatus: GeofenceBoundaryStatus? = null,
    val pendingSinceMillis: Long = 0L,
    val lastStatusChangeMillis: Long = 0L,
    val lastEnterAlertMillis: Long = 0L,
    val lastExitAlertMillis: Long = 0L
)
