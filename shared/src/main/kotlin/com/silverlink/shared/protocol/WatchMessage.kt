package com.silverlink.shared.protocol

import com.silverlink.shared.model.*
import kotlinx.serialization.Serializable

@Serializable
sealed class WatchMessage {
    // Phone → Watch
    @Serializable
    data class MedicationReminder(
        val medication: MedicationInfo,
        val scheduledTime: String
    ) : WatchMessage()

    @Serializable
    data class SyncMedications(
        val medications: List<MedicationInfo>
    ) : WatchMessage()

    @Serializable
    data class SyncEmergencyContacts(
        val contacts: List<EmergencyContact>
    ) : WatchMessage()

    @Serializable
    data class HeartRateAlert(
        val threshold: Int,
        val type: String
    ) : WatchMessage()

    // Watch → Phone
    @Serializable
    data class MedicationConfirmed(
        val medicationId: Int,
        val time: String,
        val status: String
    ) : WatchMessage()

    @Serializable
    data class FallDetected(
        val alert: FallAlert
    ) : WatchMessage()

    @Serializable
    data class HealthDataSync(
        val data: HealthData
    ) : WatchMessage()

    @Serializable
    data class SOSTriggered(
        val timestamp: Long,
        val location: String? = null
    ) : WatchMessage()
}
