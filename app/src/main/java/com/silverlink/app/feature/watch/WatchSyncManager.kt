package com.silverlink.app.feature.watch

import android.content.Context
import android.util.Log
import com.silverlink.app.SilverLinkApp
import com.silverlink.shared.model.EmergencyContact
import com.silverlink.shared.model.HealthData
import com.silverlink.shared.model.MedicationInfo
import com.silverlink.shared.protocol.WatchMessage
import com.silverlink.sdk.nearby.NearbyBridge
import com.silverlink.shared.protocol.MessageSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages data synchronization between phone and watch.
 * Handles:
 *  - Syncing medications from phone DB → watch
 *  - Syncing emergency contacts from phone DB → watch
 *  - Receiving health data from watch → phone
 *  - Receiving SOS/fall alerts from watch
 */
class WatchSyncManager(
    private val context: Context,
    private val nearbyBridge: NearbyBridge
) {
    companion object {
        private const val TAG = "WatchSyncManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Latest health data received from watch
    private val _watchHealthData = MutableStateFlow<HealthData?>(null)
    val watchHealthData: StateFlow<HealthData?> = _watchHealthData.asStateFlow()

    // SOS events
    private val _lastSOSEvent = MutableStateFlow<WatchMessage.SOSTriggered?>(null)
    val lastSOSEvent: StateFlow<WatchMessage.SOSTriggered?> = _lastSOSEvent.asStateFlow()

    // Fall detection events
    private val _lastFallEvent = MutableStateFlow<WatchMessage.FallDetected?>(null)
    val lastFallEvent: StateFlow<WatchMessage.FallDetected?> = _lastFallEvent.asStateFlow()

    /**
     * Called when connection is established. Triggers initial data sync.
     */
    fun onConnected() {
        Log.d(TAG, "Connected, starting initial sync")
        syncMedicationsToWatch()
        syncEmergencyContactsToWatch()
    }

    /**
     * Handle incoming messages from watch.
     */
    fun onMessageReceived(message: WatchMessage) {
        when (message) {
            is WatchMessage.HealthDataSync -> {
                Log.d(TAG, "Health data received: HR=${message.data.heartRate}, steps=${message.data.steps}")
                _watchHealthData.value = message.data
            }
            is WatchMessage.SOSTriggered -> {
                Log.w(TAG, "SOS triggered from watch at ${message.timestamp}")
                _lastSOSEvent.value = message
                handleSOSFromWatch(message)
            }
            is WatchMessage.FallDetected -> {
                Log.w(TAG, "Fall detected on watch: severity=${message.alert.severity}")
                _lastFallEvent.value = message
                handleFallFromWatch(message)
            }
            is WatchMessage.MedicationConfirmed -> {
                Log.d(TAG, "Medication confirmed: id=${message.medicationId}, status=${message.status}")
                handleMedicationConfirmed(message)
            }
            // Phone→Watch messages should not arrive here, but handle gracefully
            else -> {
                Log.d(TAG, "Unhandled message type: ${message::class.simpleName}")
            }
        }
    }

    /**
     * Sync medications from phone Room DB to watch.
     */
    fun syncMedicationsToWatch() {
        scope.launch {
            try {
                val db = SilverLinkApp.database
                val list = db.medicationDao().getAllMedications().first()
                val medList = list.map { med ->
                    MedicationInfo(
                        id = med.id,
                        name = med.name,
                        dosage = med.dosage,
                        times = med.getTimeList(),
                        isTakenToday = med.isTakenToday
                    )
                }
                val message = WatchMessage.SyncMedications(medList)
                sendToWatch(message)
                Log.d(TAG, "Synced ${medList.size} medications to watch")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync medications", e)
            }
        }
    }

    /**
     * Sync emergency contacts from phone Room DB to watch.
     */
    fun syncEmergencyContactsToWatch() {
        scope.launch {
            try {
                val db = SilverLinkApp.database
                val contacts = db.emergencyContactDao().getAllContactsSync()
                val sharedContacts = contacts.map { entity ->
                    EmergencyContact(
                        id = entity.id,
                        name = entity.name,
                        phone = entity.phone,
                        relationship = entity.relationship,
                        isPrimary = entity.isPrimary
                    )
                }
                val message = WatchMessage.SyncEmergencyContacts(sharedContacts)
                sendToWatch(message)
                Log.d(TAG, "Synced ${sharedContacts.size} emergency contacts to watch")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync emergency contacts", e)
            }
        }
    }

    private fun handleSOSFromWatch(sos: WatchMessage.SOSTriggered) {
        // Trigger emergency notification on phone side
        scope.launch {
            try {
                val db = SilverLinkApp.database
                val primaryContact = db.emergencyContactDao().getPrimaryContact()
                if (primaryContact != null) {
                    Log.w(TAG, "SOS from watch — should call ${primaryContact.name}: ${primaryContact.phone}")
                    // The app's existing EmergencyNotifier handles the actual call/SMS
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle SOS from watch", e)
            }
        }
    }

    private fun handleFallFromWatch(fall: WatchMessage.FallDetected) {
        // Similar to SOS — the watch already handles its own alert,
        // but we notify the phone side for logging and family notification
        Log.w(TAG, "Fall alert from watch: confidence=${fall.alert.confidence}")
    }

    private fun handleMedicationConfirmed(confirmed: WatchMessage.MedicationConfirmed) {
        scope.launch {
            try {
                // Update the medication status in phone DB
                val db = SilverLinkApp.database
                // Mark the medication as taken today
                // (simplified — a real implementation would match by ID)
                Log.d(TAG, "Medication ${confirmed.medicationId} confirmed at ${confirmed.time}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle medication confirmation", e)
            }
        }
    }

    private fun sendToWatch(message: WatchMessage): Boolean {
        if (!nearbyBridge.isConnected()) {
            Log.w(TAG, "Cannot send — not connected to watch")
            return false
        }
        val data = MessageSerializer.serialize(message)
        return nearbyBridge.sendMessage(data).isSuccess
    }
}
