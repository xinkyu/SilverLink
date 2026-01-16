package com.silverlink.app.ui.reminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.entity.Medication
import com.silverlink.app.feature.reminder.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    
    private val dao = SilverLinkApp.database.medicationDao()
    private val alarmScheduler = AlarmScheduler(application)

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAllMedications().collect { list ->
                _medications.value = list
            }
        }
    }

    fun addMedication(name: String, dosage: String, time: String) {
        viewModelScope.launch {
            val med = Medication(
                name = name,
                dosage = dosage,
                time = time,
                isTakenToday = false
            )
            val id = dao.insertMedication(med)
            val savedMed = med.copy(id = id.toInt())
            alarmScheduler.schedule(savedMed)
        }
    }

    fun toggleTaken(medication: Medication) {
        viewModelScope.launch {
            val updated = medication.copy(isTakenToday = !medication.isTakenToday)
            dao.updateMedication(updated)
        }
    }
    
    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            alarmScheduler.cancel(medication)
            dao.deleteMedication(medication)
        }
    }
}
