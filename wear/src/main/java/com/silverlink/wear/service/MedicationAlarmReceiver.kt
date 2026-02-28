package com.silverlink.wear.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.silverlink.wear.ui.MedicationAlertActivity

class MedicationAlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WatchMedAlarm"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("med_name") ?: "药物"
        val medDosage = intent.getStringExtra("med_dosage") ?: ""

        Log.i(TAG, "Medication alarm: $medName $medDosage")

        val alertIntent = Intent(context, MedicationAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("med_name", medName)
            putExtra("med_dosage", medDosage)
        }

        try {
            context.startActivity(alertIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MedicationAlertActivity", e)
        }
    }
}
