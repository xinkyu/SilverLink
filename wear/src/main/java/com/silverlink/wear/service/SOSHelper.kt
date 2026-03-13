package com.silverlink.wear.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.silverlink.shared.model.FallAlert
import com.silverlink.shared.protocol.MessageSerializer
import com.silverlink.shared.protocol.WatchMessage
import com.silverlink.wear.WatchApp

/**
 * Centralized SOS helper — handles emergency contact calling
 * and notifying the phone via NearbyBridge.
 */
object SOSHelper {

    private const val TAG = "SOSHelper"

    enum class SOSTriggerSource { MANUAL, FALL_DETECTION }

    /**
     * Trigger SOS: vibrate, call emergency contact, notify phone.
     * @return the name of the emergency contact being called, or null if none configured.
     */
    fun triggerSOS(context: Context, source: SOSTriggerSource): String? {
        Log.i(TAG, "SOS triggered from $source")

        // Vibrate urgently
        vibrateUrgent(context)

        // Get primary emergency contact
        val prefs = WatchApp.instance.watchPreferences
        val contact = prefs.getPrimaryEmergencyContact()

        // Notify phone via NearbyBridge (currently Mock — will be real after SDK integration)
        notifyPhone(source)

        // Dial emergency contact
        if (contact != null) {
            Log.i(TAG, "Calling emergency contact: ${contact.name} (${contact.phone})")
            dialEmergencyContact(context, contact.phone)
            return contact.name
        } else {
            Log.w(TAG, "No emergency contact configured")
            return null
        }
    }

    private fun dialEmergencyContact(context: Context, phoneNumber: String) {
        try {
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dial emergency contact", e)
        }
    }

    private fun vibrateUrgent(context: Context) {
        try {
            val pattern = longArrayOf(0, 800, 200, 800, 200, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    private fun notifyPhone(source: SOSTriggerSource) {
        try {
            val message = when (source) {
                SOSTriggerSource.MANUAL -> WatchMessage.SOSTriggered(
                    timestamp = System.currentTimeMillis()
                )
                SOSTriggerSource.FALL_DETECTION -> WatchMessage.SOSTriggered(
                    timestamp = System.currentTimeMillis()
                )
            }
            val data = MessageSerializer.serialize(message)
            // TODO: Send via NearbyBridge when connected
            // nearbyBridge.sendMessage(data)
            Log.i(TAG, "SOS message prepared (${data.size} bytes), awaiting NearbyBridge connection")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare SOS notification", e)
        }
    }
}
