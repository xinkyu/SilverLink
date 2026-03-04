package com.silverlink.wear.service

import android.content.Context
import android.util.Log
import com.silverlink.shared.protocol.MessageSerializer
import com.silverlink.shared.protocol.WatchMessage
import com.silverlink.wear.WatchApp

/**
 * Watch-side communication manager.
 * Receives sync messages from the phone and updates WatchPreferences.
 * Sends watch data (health, SOS, fall alerts) to the phone.
 *
 * Transport layer (NearbyBridge) is NOT owned here — the watch-side SDK
 * integration will be plugged in later. This class is a pure message handler.
 */
class WatchCommunicationManager(private val context: Context) {

    companion object {
        private const val TAG = "WatchComm"
    }

    private val prefs = WatchApp.instance.watchPreferences

    /** Callback for outgoing messages; set by the transport layer. */
    var messageSender: ((ByteArray) -> Boolean)? = null

    /** Whether the transport is currently connected. */
    var connected: Boolean = false

    fun initialize() {
        Log.d(TAG, "WatchCommunicationManager initialized")
    }

    /**
     * Called by the transport layer when raw data arrives from the phone.
     */
    fun onDataReceived(data: ByteArray) {
        try {
            val message = MessageSerializer.deserialize(data)
            Log.d(TAG, "Received: ${message::class.simpleName}")
            handleMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    private fun handleMessage(message: WatchMessage) {
        when (message) {
            is WatchMessage.SyncMedications -> {
                prefs.saveMedications(message.medications)
                Log.d(TAG, "Saved ${message.medications.size} medications")
            }
            is WatchMessage.SyncEmergencyContacts -> {
                prefs.saveEmergencyContacts(message.contacts)
                Log.d(TAG, "Saved ${message.contacts.size} emergency contacts")
            }
            is WatchMessage.MedicationReminder -> {
                Log.d(TAG, "Medication reminder: ${message.medication.name} at ${message.scheduledTime}")
                // TODO: trigger local alert
            }
            is WatchMessage.HeartRateAlert -> {
                Log.d(TAG, "Heart rate alert: threshold=${message.threshold}, type=${message.type}")
            }
            else -> {
                Log.d(TAG, "Ignoring watch->phone message: ${message::class.simpleName}")
            }
        }
    }

    fun sendHealthData() {
        val data = prefs.getHealthData()
        val message = WatchMessage.HealthDataSync(data)
        send(message)
    }

    fun sendSOSTriggered() {
        val message = WatchMessage.SOSTriggered(timestamp = System.currentTimeMillis())
        send(message)
    }

    fun sendMedicationConfirmed(medicationId: Int, time: String) {
        val message = WatchMessage.MedicationConfirmed(
            medicationId = medicationId,
            time = time,
            status = "taken"
        )
        send(message)
    }

    private fun send(message: WatchMessage): Boolean {
        if (!connected) {
            Log.w(TAG, "Not connected, message dropped: ${message::class.simpleName}")
            return false
        }
        val data = MessageSerializer.serialize(message)
        val sender = messageSender
        if (sender == null) {
            Log.w(TAG, "No message sender configured")
            return false
        }
        return sender(data)
    }

    fun isConnected(): Boolean = connected
}
