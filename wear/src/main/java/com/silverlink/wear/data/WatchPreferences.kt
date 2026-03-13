package com.silverlink.wear.data

import android.content.Context
import android.content.SharedPreferences
import com.silverlink.shared.model.EmergencyContact
import com.silverlink.shared.model.HealthData
import com.silverlink.shared.model.MedicationInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SharedPreferences wrapper for watch-side persistent data storage.
 * Stores health data, medication list, emergency contacts, sleep info, and settings.
 */
class WatchPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("watch_prefs", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    // ========== Health Data ==========

    var heartRate: Int
        get() = prefs.getInt(KEY_HEART_RATE, 0)
        set(value) = prefs.edit().putInt(KEY_HEART_RATE, value).apply()

    var steps: Int
        get() = prefs.getInt(KEY_STEPS, 0)
        set(value) = prefs.edit().putInt(KEY_STEPS, value).apply()

    var bloodOxygen: Int
        get() = prefs.getInt(KEY_BLOOD_OXYGEN, 0)
        set(value) = prefs.edit().putInt(KEY_BLOOD_OXYGEN, value).apply()

    var minHeartRate: Int
        get() = prefs.getInt(KEY_MIN_HEART_RATE, 0)
        set(value) = prefs.edit().putInt(KEY_MIN_HEART_RATE, value).apply()

    var maxHeartRate: Int
        get() = prefs.getInt(KEY_MAX_HEART_RATE, 0)
        set(value) = prefs.edit().putInt(KEY_MAX_HEART_RATE, value).apply()

    fun saveHealthData(data: HealthData) {
        prefs.edit()
            .putInt(KEY_HEART_RATE, data.heartRate)
            .putInt(KEY_STEPS, data.steps)
            .putInt(KEY_BLOOD_OXYGEN, data.bloodOxygen)
            .putLong(KEY_HEALTH_TIMESTAMP, data.timestamp)
            .apply()
    }

    fun getHealthData(): HealthData = HealthData(
        heartRate = heartRate,
        steps = steps,
        bloodOxygen = bloodOxygen,
        sleepMinutes = deepSleepMinutes + lightSleepMinutes + remSleepMinutes,
        timestamp = prefs.getLong(KEY_HEALTH_TIMESTAMP, 0L)
    )

    // ========== Sleep Data ==========

    var deepSleepMinutes: Int
        get() = prefs.getInt(KEY_DEEP_SLEEP, 0)
        set(value) = prefs.edit().putInt(KEY_DEEP_SLEEP, value).apply()

    var lightSleepMinutes: Int
        get() = prefs.getInt(KEY_LIGHT_SLEEP, 0)
        set(value) = prefs.edit().putInt(KEY_LIGHT_SLEEP, value).apply()

    var remSleepMinutes: Int
        get() = prefs.getInt(KEY_REM_SLEEP, 0)
        set(value) = prefs.edit().putInt(KEY_REM_SLEEP, value).apply()

    var sleepScore: Int
        get() = prefs.getInt(KEY_SLEEP_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_SLEEP_SCORE, value).apply()

    // ========== Medications ==========

    fun getMedications(): List<MedicationInfo> {
        val raw = prefs.getString(KEY_MEDICATIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<MedicationInfo>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMedications(medications: List<MedicationInfo>) {
        prefs.edit()
            .putString(KEY_MEDICATIONS, json.encodeToString(medications))
            .apply()
    }

    // ========== Emergency Contacts ==========

    fun getEmergencyContacts(): List<EmergencyContact> {
        val raw = prefs.getString(KEY_EMERGENCY_CONTACTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<EmergencyContact>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        prefs.edit()
            .putString(KEY_EMERGENCY_CONTACTS, json.encodeToString(contacts))
            .apply()
    }

    fun getPrimaryEmergencyContact(): EmergencyContact? {
        return getEmergencyContacts().firstOrNull { it.isPrimary }
            ?: getEmergencyContacts().firstOrNull()
    }

    // ========== Settings ==========

    var fallDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_FALL_DETECTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FALL_DETECTION_ENABLED, value).apply()

    var highSensitivity: Boolean
        get() = prefs.getBoolean(KEY_HIGH_SENSITIVITY, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_SENSITIVITY, value).apply()

    // ========== Paired Device ==========

    var pairedDeviceId: String?
        get() = prefs.getString(KEY_PAIRED_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_PAIRED_DEVICE_ID, value).apply()

    var pairedDeviceName: String?
        get() = prefs.getString(KEY_PAIRED_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_PAIRED_DEVICE_NAME, value).apply()

    companion object {
        private const val KEY_HEART_RATE = "heart_rate"
        private const val KEY_STEPS = "steps"
        private const val KEY_BLOOD_OXYGEN = "blood_oxygen"
        private const val KEY_MIN_HEART_RATE = "min_heart_rate"
        private const val KEY_MAX_HEART_RATE = "max_heart_rate"
        private const val KEY_HEALTH_TIMESTAMP = "health_timestamp"
        private const val KEY_DEEP_SLEEP = "deep_sleep_minutes"
        private const val KEY_LIGHT_SLEEP = "light_sleep_minutes"
        private const val KEY_REM_SLEEP = "rem_sleep_minutes"
        private const val KEY_SLEEP_SCORE = "sleep_score"
        private const val KEY_MEDICATIONS = "medications_json"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts_json"
        private const val KEY_FALL_DETECTION_ENABLED = "fall_detection_enabled"
        private const val KEY_HIGH_SENSITIVITY = "high_sensitivity"
        private const val KEY_PAIRED_DEVICE_ID = "paired_device_id"
        private const val KEY_PAIRED_DEVICE_NAME = "paired_device_name"
    }
}
